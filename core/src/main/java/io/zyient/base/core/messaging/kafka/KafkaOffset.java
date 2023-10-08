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

package io.zyient.base.core.messaging.kafka;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import io.zyient.base.core.messaging.ReceiverOffset;
import io.zyient.base.core.state.Offset;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public class KafkaOffset extends ReceiverOffset<KafkaOffsetValue> {
    private String topic;
    private int partition;

    public KafkaOffset() {
        setOffsetRead(new KafkaOffsetValue(0L));
        setOffsetCommitted(new KafkaOffsetValue(0L));
    }
    @Override
    public int compareTo(@NonNull Offset offset) {
        Preconditions.checkArgument(offset instanceof KafkaOffset);
        Preconditions.checkArgument(topic.compareTo(((KafkaOffset) offset).topic) == 0);
        Preconditions.checkArgument(partition == ((KafkaOffset) offset).partition);
        return super.compareTo(offset);
    }

    @Override
    public KafkaOffsetValue parse(@NonNull String value) throws Exception {
        return new KafkaOffsetValue(Long.parseLong(value));
    }
}