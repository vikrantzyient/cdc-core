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

package io.zyient.base.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zyient.base.common.model.ValidationExceptions;
import io.zyient.base.common.model.entity.EEntityState;
import io.zyient.base.common.model.entity.IEntity;
import io.zyient.base.common.model.entity.IKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.persistence.Version;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity<K extends IKey> implements IEntity<K> {
    @Setter(AccessLevel.NONE)
    @Transient
    @JsonIgnore
    private final EntityState state = new EntityState();
    @Column(name = "time_created")
    private long createdTime;
    @Column(name = "time_updated")
    @Version
    private long updatedTime;

    public BaseEntity() {
        state.setState(EEntityState.Syncing);
    }

    /**
     * Validate this entity instance.
     *
     * @throws ValidationExceptions - On validation failure will throw exception.
     */
    @Override
    public final void validate() throws ValidationExceptions {
        try {
            doValidate();
        } catch (ValidationExceptions ex) {
            state.error(ex);
            throw ex;
        }
    }

    /**
     * Validate this entity instance.
     *
     * @throws ValidationExceptions - On validation failure will throw exception.
     */
    public abstract void doValidate() throws ValidationExceptions;
}