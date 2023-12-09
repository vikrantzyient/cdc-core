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

package io.zyient.core.caseflow.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.zyient.base.core.model.PropertyBag;
import io.zyient.core.caseflow.errors.CaseModelException;
import io.zyient.core.persistence.impl.rdbms.converters.PropertiesConverter;
import io.zyient.core.persistence.model.BaseEntity;
import io.zyient.core.persistence.model.DocumentId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@MappedSuperclass
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public abstract class Case<S extends CaseState<?>> extends BaseEntity<CaseId> implements PropertyBag {
    @EmbeddedId
    private CaseId id;
    @Embedded
    private S state;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "assigned_to")),
            @AttributeOverride(name = "type", column = @Column(name = "assigned_to_type")),
            @AttributeOverride(name = "timestamp", column = @Column(name = "assigned_timestamp"))
    })
    private UserOrRole assignedTo;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "closed_by")),
            @AttributeOverride(name = "type", column = @Column(name = "closed_by_type")),
            @AttributeOverride(name = "timestamp", column = @Column(name = "closed_timestamp"))
    })
    private UserOrRole closedBy;
    @OneToMany
    @JoinColumn(name = "case_id")
    private Set<CaseComment> comments;
    @OneToMany
    @JoinColumn(name = "case_id")
    private Set<ArtefactReference> artefactReferences;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "id", column = @Column(name = "parent_case_id"))
    })
    private CaseId parentId;
    @Transient
    private Set<CaseDocument<?, ?>> artefacts;
    @Column(name = "properties")
    @Convert(converter = PropertiesConverter.class)
    private Map<String, Object> properties;
    @Transient
    private Set<ArtefactReference> deleted;

    public Case() {
        id = new CaseId();
    }

    public Case<S> addArtefact(@NonNull CaseDocument<?, ?> artefact) {
        ArtefactReferenceId refId = new ArtefactReferenceId();
        refId.setCaseId(id);
        refId.setDocumentId(artefact.getId());
        ArtefactReference reference = new ArtefactReference();
        reference.setId(refId);
        reference.setArtefactType(artefact.getClass().getCanonicalName());
        artefactReferences.add(reference);
        artefact.setReferenceId(id);
        artefacts.add(artefact);

        return this;
    }

    public boolean deleteArtefact(@NonNull DocumentId docId) {
        ArtefactReference delete = null;
        for (ArtefactReference reference : artefactReferences) {
            if (reference.getId().getDocumentId().compareTo(docId) == 0) {
                delete = reference;
                break;
            }
        }
        if (delete != null) {
            artefactReferences.remove(delete);
            if (deleted == null) {
                deleted = new HashSet<>();
            }
            deleted.add(delete);
            CaseDocument<?, ?> deleteDoc = null;
            for (CaseDocument<?, ?> document : artefacts) {
                if (document.getId().compareTo(delete.getId().getDocumentId()) == 0) {
                    deleteDoc = document;
                    break;
                }
            }
            if (deleteDoc != null) {
                artefacts.remove(deleteDoc);
            }
            return true;
        }
        return false;
    }

    public CaseDocument<?, ?> findArtefact(@NonNull DocumentId documentId) {
        if (artefacts != null) {
            for (CaseDocument<?, ?> document : artefacts) {
                if (document.getId().compareTo(documentId) == 0) {
                    return document;
                }
            }
        }
        return null;
    }

    public CaseComment addComment(@NonNull String userOrRole,
                                  @NonNull EUserOrRole type,
                                  @NonNull String comment,
                                  Long responseTo,
                                  DocumentId documentId) throws CaseModelException {
        UserOrRole ur = new UserOrRole();
        ur.setName(userOrRole);
        ur.setType(type);
        ur.setTimestamp(System.currentTimeMillis());
        return addComment(ur, comment, responseTo, documentId);
    }

    public CaseComment addComment(@NonNull UserOrRole commentor,
                                  @NonNull String comment,
                                  Long responseTo,
                                  DocumentId docId) throws CaseModelException {
        CaseComment parent = null;
        if (responseTo != null) {
            parent = findParent(responseTo);
            if (parent == null) {
                throw new CaseModelException(String.format("Parent comment not found. [case=%s][comment id=%d]",
                        id.stringKey(), responseTo));
            }
        }
        commentor.setTimestamp(System.currentTimeMillis());
        CaseCommentId id = new CaseCommentId();
        id.setCaseId(this.id.getId());
        CaseComment c = new CaseComment();
        c.setId(id);
        c.setCommentedBy(commentor);
        c.setComment(comment);
        c.setState(ECaseCommentState.New);
        if (docId != null) {
            CaseDocument<?, ?> artefact = findArtefact(docId);
            if (artefact == null) {
                throw new CaseModelException(String.format("Artefact not found. [case=%s][artefact id=%s]",
                        id.getCaseId(), docId.stringKey()));
            }
            c.setArtefactId(docId);
        }
        comments.add(c);

        return c;
    }

    private CaseComment findParent(long parentId) {
        if (comments != null) {
            for (CaseComment comment : comments) {
                if (comment.getId().getCommentId() == parentId) {
                    return comment;
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasProperty(@NonNull String name) {
        if (properties != null) {
            return properties.containsKey(name);
        }
        return false;
    }

    @Override
    public Object getProperty(@NonNull String name) {
        if (properties != null) {
            return properties.get(name);
        }
        return null;
    }

    @Override
    public PropertyBag setProperty(@NonNull String name, @NonNull Object value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
        return this;
    }
}
