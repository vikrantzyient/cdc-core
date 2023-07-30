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

import ai.sapper.cdc.core.processing.MessageProcessorState;
import ai.sapper.cdc.core.state.Offset;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public class ChronicleMessageProcessingState<E extends Enum<?>, O extends Offset> extends MessageProcessorState<E, O, ChronicleOffset> {
    public ChronicleMessageProcessingState(@NonNull E errorState,
                                           @NonNull E initState) {
        super(errorState, initState, ChronicleConsumerState.OFFSET_TYPE);
    }

    public ChronicleMessageProcessingState(@NonNull MessageProcessorState<E, O, ChronicleOffset> state) {
        super(state);
    }
}