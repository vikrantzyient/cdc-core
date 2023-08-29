package ai.sapper.cdc.intake.model;

import ai.sapper.cdc.common.model.Context;
import ai.sapper.cdc.common.model.CopyException;
import ai.sapper.cdc.common.model.ValidationExceptions;
import ai.sapper.cdc.common.model.entity.IEntity;
import ai.sapper.cdc.common.utils.ReflectionUtils;
import ai.sapper.cdc.core.io.model.FileInode;
import ai.sapper.cdc.core.stores.JsonReference;
import ai.sapper.cdc.core.stores.annotations.Reference;
import ai.sapper.cdc.core.utils.FileUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "ingest_file_records")
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public class FileItemRecord implements IEntity<IdKey> {
    @Id
    @Column(name = "id")
    private IdKey fileId;
    @Column(name = "intake_channel")
    @Enumerated(EnumType.STRING)
    private EIntakeChannel channel;
    @Column(name = "record_type")
    @Enumerated(EnumType.STRING)
    private EFileRecordType recordType;
    @Column(name = "drive")
    private String drive;
    @Column(name = "source_folder")
    private String sourceFolder;
    @Column(name = "name")
    private String fileName;
    @Column(name = "file_location")
    private Map<String, String> fileLocation;
    @Column(name = "file_location_url")
    private String fileLocationUrl;
    @Column(name = "file_pdf_location_url")
    private String filePdfLocationUrl;
    @Column(name = "processed_timestamp")
    private long processedTimestamp;
    @Column(name = "read_timestamp")
    private long readTimestamp;
    @Column(name = "processing_timestamp")
    private long processingTimestamp;
    @Column(name = "reprocess_count")
    private int reProcessCount;
    @Column(name = "source_user_id")
    private String sourceUserId;
    @Column(name = "processing_status")
    @Enumerated(EnumType.STRING)
    private ERecordState state = ERecordState.Unknown;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "file_type")
    private String fileType;
    @Column(name = "file_size")
    private long fileSize;
    @Column(name = "parent_id")
    private String parentId;
    @Column(name = "context_json")
    private String contextJson;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "parent_id")
    @Reference(target = FileItemRecord.class, reference = "itemReferences")
    private Set<FileItemRecord> fileItemRecords;
    @Column(name = "request_id")
    private String requestId;
    @Embedded
    private RecordReference recordReference;
    @Version
    @Column(name = "record_version")
    private long recordVersion = 0;
    @Transient
    @JsonIgnore
    private Throwable error;
    @Column(name = "is_inline_attachment")
    private boolean isInlineAttachment = false;
    @Transient
    private UpdateParams updateParams;
    @Transient
    private JsonReference itemReferences;

    public FileItemRecord() {
    }

    public boolean hasError() {
        return state == ERecordState.Error;
    }

    public void addFileItemRecord(@NonNull FileItemRecord record) {
        if (fileItemRecords == null) {
            fileItemRecords = new HashSet<>();
        }
        record.setParentId(fileId.getId());

        fileItemRecords.add(record);
    }

    public static FileItemRecord create(@NonNull EIntakeChannel channel,
                                        @NonNull FileInode inode,
                                        @NonNull String userId) throws IOException {
        try {
            FileItemRecord record = new FileItemRecord();
            // TODO: File ID shouldn't be random
            record.fileId = new IdKey(inode.getUuid());
            record.channel = channel;
            record.setDrive(inode.getDomain());
            record.setParentId(inode.getParent().getUuid());
            record.fileName = inode.getName();
            record.fileLocation = inode.getPath();
            record.fileType = FileUtils.getFileMimeType(inode.getFsPath());
            record.fileSize = inode.size();
            record.setState(ERecordState.Unknown);
            record.processingTimestamp = System.currentTimeMillis();
            record.readTimestamp = System.currentTimeMillis();
            record.setSourceUserId(userId);

            return record;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public int compare(IdKey idKey) {
        return 0;
    }

    @Override
    public IEntity<IdKey> copyChanges(IEntity<IdKey> iEntity,
                                      Context context) throws CopyException {
        try {
            if (iEntity instanceof FileItemRecord source) {
                this.fileId = source.fileId;
                this.fileItemRecords = new HashSet<>(source.fileItemRecords);
                this.fileLocation = source.fileLocation;
                this.recordReference = source.recordReference;
                this.updateParams = source.updateParams;
                ReflectionUtils.copyNatives(source, this);
            } else {
                throw new CopyException(String.format("Invalid entity: [type=%s]", iEntity.getClass().getCanonicalName()));
            }
            return this;
        } catch (Exception ex) {
            throw new CopyException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public IEntity<IdKey> clone(Context context) throws CopyException {
        try {
            return (IEntity<IdKey>) clone();
        } catch (Exception ex) {
            throw new CopyException(ex);
        }
    }

    @Override
    public void validate() throws ValidationExceptions {

    }

    @Override
    @JsonIgnore
    public IdKey getKey() {
        return fileId;
    }
}
