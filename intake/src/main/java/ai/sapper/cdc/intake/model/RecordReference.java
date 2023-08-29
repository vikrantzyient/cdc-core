package ai.sapper.cdc.intake.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Getter
@Setter
@Embeddable
public class RecordReference {
    @Column(name = "reference_type")
    private String type;
    @Column(name = "reference_key")
    private String key;

    public RecordReference() {

    }

    public RecordReference(@NonNull String type,
                           @NonNull String key) {
        this.type = type;
        this.key = key;
    }
}
