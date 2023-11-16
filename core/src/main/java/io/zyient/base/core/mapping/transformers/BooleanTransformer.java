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

package io.zyient.base.core.mapping.transformers;

import com.google.common.base.Strings;
import io.zyient.base.common.utils.ReflectionUtils;
import io.zyient.base.core.mapping.DataException;
import io.zyient.base.core.mapping.Transformer;
import lombok.NonNull;

public class BooleanTransformer implements Transformer<Boolean> {
    @Override
    public Boolean transform(@NonNull Object source) throws DataException {
        if (ReflectionUtils.isBoolean(source.getClass())) {
            return (boolean) source;
        } else if (ReflectionUtils.isNumericType(source.getClass())) {
            int v = (int) source;
            return (v > 0);
        } else if (source instanceof String value) {
            if (Strings.isNullOrEmpty(value)) {
                return null;
            }
            return Boolean.parseBoolean(value);
        }
        throw new DataException(String.format("Cannot transform to Double. [source=%s]", source.getClass()));
    }
}
