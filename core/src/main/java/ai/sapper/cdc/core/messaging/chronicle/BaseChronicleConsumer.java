/*
 * Copyright(C) (2023) Sapper Inc. (open.source at zyient dot io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.sapper.cdc.core.messaging.chronicle;

import ai.sapper.cdc.common.model.Context;
import ai.sapper.cdc.common.utils.DefaultLogger;
import ai.sapper.cdc.common.utils.RunUtils;
import ai.sapper.cdc.core.connections.chronicle.ChronicleConsumerConnection;
import ai.sapper.cdc.core.messaging.InvalidMessageError;
import ai.sapper.cdc.core.messaging.MessageObject;
import ai.sapper.cdc.core.messaging.MessageReceiver;
import ai.sapper.cdc.core.messaging.MessagingError;
import ai.sapper.cdc.core.processing.ProcessorState;
import ai.sapper.cdc.core.state.Offset;
import ai.sapper.cdc.core.state.OffsetState;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.Wire;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public abstract class BaseChronicleConsumer<M> extends MessageReceiver<String, M> {
    private Queue<MessageObject<String, M>> cache = null;
    private final Map<String, ChronicleOffsetData> offsetMap = new HashMap<>();
    private ChronicleConsumerConnection consumer;
    private ChronicleStateManager stateManager;
    private ChronicleConsumerState state;

    @Override
    public MessageReceiver<String, M> init() throws MessagingError {
        Preconditions.checkState(connection() instanceof ChronicleConsumerConnection);
        consumer = (ChronicleConsumerConnection) connection();
        cache = new ArrayBlockingQueue<>(batchSize());
        try {
            if (!consumer.isConnected()) {
                consumer.connect();
            }
            if (stateful()) {
                Preconditions.checkArgument(offsetStateManager() instanceof ChronicleStateManager);
                stateManager = (ChronicleStateManager) offsetStateManager();
                state = stateManager.get(consumer.name());
                ChronicleOffset offset = state.getOffset();
                if (offset.getOffsetCommitted() > 0) {
                    seek(offset.getOffsetCommitted() + 1);
                } else {
                    seek(0);
                }
                if (offset.getOffsetCommitted() != offset.getOffsetRead()) {
                    DefaultLogger.warn(
                            String.format("[topic=%s] Read offset ahead of committed, potential resends.",
                                    consumer.name()));
                    offset.setOffsetRead(offset.getOffsetCommitted());
                    stateManager.update(state);
                }
            }
            offsetMap.clear();
            state().setState(ProcessorState.EProcessorState.Running);
            return this;
        } catch (Exception ex) {
            state().error(ex);
            throw new MessagingError(ex);
        }
    }

    @Override
    public MessageObject<String, M> receive(long timeout) throws MessagingError {
        Preconditions.checkState(state().isAvailable());
        if (cache.isEmpty()) {
            List<MessageObject<String, M>> batch = nextBatch(timeout);
            if (batch != null) {
                cache.addAll(batch);
            }
        }
        if (!cache.isEmpty()) {
            return cache.poll();
        }
        return null;
    }

    @Override
    public List<MessageObject<String, M>> nextBatch(long timeout) throws MessagingError {
        Preconditions.checkState(state().isAvailable());
        long stime = System.currentTimeMillis();
        long remaining = timeout;
        long lastIndex = state.getOffset().getOffsetRead();
        List<MessageObject<String, M>> messages = new ArrayList<>(batchSize());
        try {
            while (remaining > 0) {
                DocumentContext dc = consumer.tailer().readingDocument(true);
                boolean read = false;
                if (dc.isPresent()) {
                    ReadResponse<M> response = parse(dc);
                    if (response.index > lastIndex) {
                        lastIndex = response.index;
                    }
                    if (response.error != null) {
                        if (response.error instanceof InvalidMessageError) {
                            DefaultLogger.error("Error reading message.", response.error);
                        } else {
                            throw response.error;
                        }
                    } else if (response.message != null) {
                        messages.add(response.message);
                        offsetMap.put(response.message.id(),
                                new ChronicleOffsetData(response.message.key(), response.index));
                        read = true;
                    }
                }
                if (!read) {
                    long si = remaining / 10;
                    if (si > 0) {
                        RunUtils.sleep(si);
                    }
                    remaining -= System.currentTimeMillis() - stime;
                    continue;
                }
                if (messages.size() >= batchSize()) {
                    break;
                }
            }
            if (lastIndex > state.getOffset().getOffsetRead()) {
                updateReadState(lastIndex);
            }
            if (!messages.isEmpty()) {
                return messages;
            }
            return null;
        } catch (Throwable ex) {
            throw new MessagingError(ex);
        }
    }

    private ReadResponse<M> parse(DocumentContext context) throws Exception {
        final ReadResponse<M> response = new ReadResponse<>();
        response.index = context.index();
        Wire w = context.wire();
        if (w != null) {
            final BaseChronicleMessage<M> message = new BaseChronicleMessage<>();
            message.index(context.index());
            message.queue(consumer.name());
            w.read(consumer.settings().getName()).marshallable(m -> {
                message.id(m.read(BaseChronicleMessage.HEADER_MESSAGE_ID).text());
                message.correlationId(m.read(BaseChronicleMessage.HEADER_CORRELATION_ID).text());
                message.key(m.read(BaseChronicleMessage.HEADER_MESSAGE_KEY).text());
                String mo = m.read(BaseChronicleMessage.HEADER_MESSAGE_MODE).text();
                message.mode(MessageObject.MessageMode.valueOf(mo));
                String queue = m.read(BaseChronicleMessage.HEADER_MESSAGE_QUEUE).text();
                if (Strings.isNullOrEmpty(queue) || message.queue().compareToIgnoreCase(queue) != 0) {
                    response.error = new InvalidMessageError(message.id(),
                            String.format("Invalid message: Queue not expected. [expected=%s][queue=%s]",
                                    message.queue(),
                                    queue));
                    return;
                }
                byte[] data = m.read(BaseChronicleMessage.HEADER_MESSAGE_BODY).bytes();
                if (data == null || data.length == 0) {
                    response.error = new InvalidMessageError(message.id(),
                            "Data is NULL or empty.");
                    return;
                }
                try {
                    M body = deserialize(data);
                    if (body == null) {
                        response.error = new InvalidMessageError(message.id(),
                                "Failed to parse message body.");
                        return;
                    }
                    message.value(body);
                } catch (Exception ex) {
                    response.error = ex;
                    return;
                }
                response.message = message;
            });
        }
        return response;
    }


    @Override
    public void ack(@NonNull String messageId, boolean commit) throws MessagingError {
        Preconditions.checkState(state().isAvailable());
        try {
            synchronized (offsetMap) {
                if (offsetMap.containsKey(messageId)) {
                    ChronicleOffsetData od = offsetMap.get(messageId);
                    od.acked(true);
                    if (commit) {
                        updateCommitState(od.index());
                        offsetMap.remove(messageId);
                    }
                } else {
                    throw new MessagingError(String.format("No record offset found for key. [key=%s]", messageId));
                }
            }
        } catch (Exception ex) {
            throw new MessagingError(ex);
        }
    }

    @Override
    public void ack(@NonNull List<String> messageIds) throws MessagingError {
        Preconditions.checkState(state().isAvailable());
        Preconditions.checkArgument(!messageIds.isEmpty());
        try {
            synchronized (offsetMap) {
                long currentOffset = -1;
                for (String messageId : messageIds) {
                    if (offsetMap.containsKey(messageId)) {
                        ChronicleOffsetData od = offsetMap.get(messageId);
                        currentOffset = Math.max(od.index(), currentOffset);
                    } else {
                        throw new MessagingError(String.format("No record offset found for key. [key=%s]", messageId));
                    }
                    offsetMap.remove(messageId);
                }
                if (currentOffset > 0) {
                    if (currentOffset > state.getOffset().getOffsetCommitted()) {
                        updateCommitState(currentOffset);
                    }
                }
            }
        } catch (Exception ex) {
            throw new MessagingError(ex);
        }
    }

    @Override
    public int commit() throws MessagingError {
        int count = 0;
        synchronized (offsetMap) {
            List<String> messageIds = new ArrayList<>();
            for (String id : offsetMap.keySet()) {
                ChronicleOffsetData od = offsetMap.get(id);
                if (od.acked()) {
                    messageIds.add(id);
                }
            }
            if (!messageIds.isEmpty()) {
                ack(messageIds);
                count = messageIds.size();
            }
        }
        return count;
    }

    @Override
    public OffsetState<?, ?> currentOffset(Context context) throws MessagingError {
        if (!stateful())
            return null;
        return state;
    }

    @Override
    public void seek(@NonNull Offset offset, Context context) throws MessagingError {
        Preconditions.checkState(state().isAvailable());
        Preconditions.checkArgument(offset instanceof ChronicleOffset);
        ChronicleConsumerState s = (ChronicleConsumerState) currentOffset(context);
        try {
            long o = ((ChronicleOffset) offset).getOffsetCommitted();
            if (s.getOffset().getOffsetRead() < o) {
                o = s.getOffset().getOffsetRead();
                ((ChronicleOffset) offset).setOffsetCommitted(o);
            }
            seek(o);
            updateReadState(o);
        } catch (Exception ex) {
            throw new MessagingError(ex);
        }
    }

    private void seek(long offset) throws Exception {
        if (offset > 0) {
            consumer.tailer().moveToIndex(offset);
        } else {
            consumer.tailer().toEnd();
        }
    }

    private void updateReadState(long offset) throws Exception {
        if (!stateful()) return;
        state.getOffset().setOffsetRead(offset);
        state = stateManager.update(state);
    }

    private void updateCommitState(long offset) throws Exception {
        if (!stateful()) return;
        if (offset > state.getOffset().getOffsetRead()) {
            throw new Exception(
                    String.format("[topic=%s] Offsets out of sync. [read=%d][committing=%d]",
                            consumer.name(), state.getOffset().getOffsetRead(), offset));
        }
        state.getOffset().setOffsetCommitted(offset);
        state = stateManager.update(state);
    }


    @Override
    public void close() throws IOException {
        if (state().isAvailable()) {
            state().setState(ProcessorState.EProcessorState.Stopped);
        }
        if (cache != null) {
            cache.clear();
            cache = null;
        }
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    protected abstract M deserialize(byte[] message) throws MessagingError;

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class ReadResponse<M> {
        private BaseChronicleMessage<M> message;
        private Throwable error;
        private long index = -1;

        public ReadResponse(@NonNull BaseChronicleMessage<M> message) {
            this.message = message;
            error = null;
        }

        public ReadResponse() {
            message = null;
            error = null;
        }
    }
}
