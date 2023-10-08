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

package io.zyient.base.core.messaging.chronicle.builders;

import com.google.common.base.Preconditions;
import io.zyient.base.core.connections.chronicle.ChronicleProducerConnection;
import io.zyient.base.core.connections.settings.EConnectionType;
import io.zyient.base.core.messaging.builders.MessageSenderBuilder;
import io.zyient.base.core.messaging.builders.MessageSenderSettings;
import io.zyient.base.core.messaging.chronicle.BaseChronicleProducer;
import lombok.NonNull;

public class ChronicleProducerBuilder<M> extends MessageSenderBuilder<String, M> {
    private final Class<? extends BaseChronicleProducer<M>> type;

    public ChronicleProducerBuilder(@NonNull Class<? extends BaseChronicleProducer<M>> type,
                                    @NonNull Class<? extends MessageSenderSettings> settingsType) {
        super(settingsType);
        this.type = type;
    }

    public ChronicleProducerBuilder(@NonNull Class<? extends BaseChronicleProducer<M>> type) {
        super(MessageSenderSettings.class);
        this.type = type;
    }

    @Override
    public BaseChronicleProducer<M> build(@NonNull MessageSenderSettings settings) throws Exception {
        Preconditions.checkNotNull(env());
        Preconditions.checkArgument(settings.getType() == EConnectionType.chronicle);
        ChronicleProducerConnection connection = env().connectionManager()
                .getConnection(settings.getConnection(), ChronicleProducerConnection.class);
        if (connection == null) {
            throw new Exception(
                    String.format("Chronicle Producer connection not found. [name=%s]", settings.getConnection()));
        }
        if (!connection.isConnected()) {
            connection.connect();
        }
        BaseChronicleProducer<M> producer = type.getDeclaredConstructor().newInstance();
        producer.withConnection(connection);
        return (BaseChronicleProducer<M>) producer.init();
    }
}