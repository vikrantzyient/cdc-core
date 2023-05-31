package ai.sapper.cdc.core.messaging.kafka;

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
public class KafkaMessageProcessingState<E extends Enum<?>, O extends Offset> extends MessageProcessorState<E, O, KafkaOffset> {
    public KafkaMessageProcessingState(@NonNull E errorState,
                                       @NonNull E initState) {
        super(errorState, initState);
    }

    public KafkaMessageProcessingState(@NonNull MessageProcessorState<E, O, KafkaOffset> state) {
        super(state);
    }
}