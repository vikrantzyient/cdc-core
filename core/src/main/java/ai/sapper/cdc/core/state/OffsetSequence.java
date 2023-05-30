package ai.sapper.cdc.core.state;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS
)
public class OffsetSequence {
    private long sequence = 0;
    private long timeUpdated;
}
