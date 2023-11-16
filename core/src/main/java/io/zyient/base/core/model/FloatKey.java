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

import dev.morphia.annotations.Entity;
import io.zyient.base.common.model.entity.IKey;
import io.zyient.base.common.model.entity.NativeKey;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
@Entity
public class FloatKey extends NativeKey<Float> {
    public FloatKey() {
        super(Float.class);
    }

    /**
     * Compare the current key to the target.
     *
     * @param key - Key to compare to
     * @return - == 0, < -x, > +x
     */
    @Override
    public int compareTo(IKey key) {
        if (key instanceof FloatKey) {
            float f = (getKey() - ((FloatKey) key).getKey());
            if (f > -1 && f < 0) {
                return -1;
            } else if (f > 0 && f < 1) {
                return 1;
            }
            return (int) f;
        }
        return -1;
    }
}