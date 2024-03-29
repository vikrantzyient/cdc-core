/*
 * Copyright(C) (2024) Zyient Inc. (open.source at zyient dot io)
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

package io.zyient.core.mapping.model.mapping;

import io.zyient.base.common.model.Context;
import io.zyient.core.mapping.model.EvaluationStatus;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class MappedResponse<T> {
    private T entity;
    private final Map<String, Object> source;
    private Map<String, Object> cached;
    private Context context;
    private EvaluationStatus status;

    public MappedResponse(Map<String, Object> source) {
        this.source = source;
    }

    public MappedResponse<T> add(@NonNull String name, @NonNull Object data) {
        if (cached == null) {
            cached = new HashMap<>();
        }
        cached.put(name, data);
        return this;
    }

    public Object getCached(@NonNull String name) {
        if (cached != null) {
            return cached.get(name);
        }
        return null;
    }
}
