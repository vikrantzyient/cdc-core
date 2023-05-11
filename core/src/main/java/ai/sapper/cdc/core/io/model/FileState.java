package ai.sapper.cdc.core.io.model;

import ai.sapper.cdc.common.AbstractState;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public class FileState extends AbstractState<EFileState> {
    public FileState() {
        super(EFileState.Error);
    }

    public boolean markedForUpdate() {
        return (state() == EFileState.Updating
                || state() == EFileState.PendingSync
                || state() == EFileState.New);
    }

    public boolean synced() {
        return state() == EFileState.Synced;
    }
}
