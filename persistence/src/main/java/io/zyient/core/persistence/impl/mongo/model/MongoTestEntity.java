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

package io.zyient.core.persistence.impl.mongo.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.morphia.annotations.Entity;
import io.zyient.base.common.model.Context;
import io.zyient.base.common.model.CopyException;
import io.zyient.base.common.model.ValidationExceptions;
import io.zyient.base.common.model.entity.EEntityState;
import io.zyient.base.common.model.entity.IEntity;
import io.zyient.base.core.model.StringKey;
import io.zyient.core.persistence.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
@Entity("JsonTest")
public class MongoTestEntity extends BaseEntity<StringKey> {
    private StringKey key;
    private String textValue;
    private long timestamp;
    private double doubleValue;
    private List<TestMongoNested> nested;

    public MongoTestEntity() {
        key = new StringKey(UUID.randomUUID().toString());
        textValue = String.format("[%s] Random text....", UUID.randomUUID().toString());
        timestamp = System.nanoTime();
        Random rnd = new Random(System.nanoTime());
        doubleValue = rnd.nextDouble();
        int c = rnd.nextInt(10);
        if (c > 0) {
            nested = new ArrayList<>(c);
            for (int ii = 0; ii < c; ii++) {
                TestMongoNested ne = new TestMongoNested();
                ne.setParentId(key.stringKey());
                nested.add(ne);
            }
        }
        getState().setState(EEntityState.New);
    }

    /**
     * Compare the entity key with the key specified.
     *
     * @param key - Target Key.
     * @return - Comparision.
     */
    @Override
    public int compare(StringKey key) {
        return this.key.compareTo(key);
    }

    /**
     * Copy the changes from the specified source entity
     * to this instance.
     * <p>
     * All properties other than the Key will be copied.
     * Copy Type:
     * Primitive - Copy
     * String - Copy
     * Enum - Copy
     * Nested Entity - Copy Recursive
     * Other Objects - Copy Reference.
     *
     * @param source  - Source instance to Copy from.
     * @param context - Execution context.
     * @return - Copied Entity instance.
     * @throws CopyException
     */
    @Override
    public IEntity<StringKey> copyChanges(IEntity<StringKey> source, Context context) throws CopyException {
        return null;
    }

    /**
     * Clone this instance of Entity.
     *
     * @param context - Clone Context.
     * @return - Cloned Instance.
     * @throws CopyException
     */
    @Override
    public IEntity<StringKey> clone(Context context) throws CopyException {
        return null;
    }

    /**
     * Get the object instance Key.
     *
     * @return - Key
     */
    @Override
    public StringKey entityKey() {
        return key;
    }

    /**
     * Validate this entity instance.
     *
     * @param errors
     * @throws ValidationExceptions - On validation failure will throw exception.
     */
    @Override
    public ValidationExceptions doValidate(ValidationExceptions errors) throws ValidationExceptions {
        return errors;
    }
}