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

package io.zyient.base.common.utils.beans;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Accessors(fluent = true)
public class ClassPropertyDef extends PropertyDef {
    private ClassDef clazz;
    private Map<String, PropertyDef> properties = new HashMap<>();

    public ClassPropertyDef() {
    }

    public ClassPropertyDef(@NonNull ClassPropertyDef source) {
        super(source);
        clazz = source.clazz;
        properties = source.properties;
    }

    public ClassPropertyDef add(@NonNull PropertyDef property) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        if (Strings.isNullOrEmpty(path())) {
            property.path(property.name());
        } else {
            property.path(String.format("%s.%s", path(), property.name()));
        }
        properties.put(property.name(), property);
        return this;
    }

    public ClassPropertyDef from(@NonNull Field field,
                                 @NonNull Class<?> owner) throws Exception {
        super.from(field, owner);
        clazz = BeanUtils.get(type());
        abstractType(clazz.abstractType());
        generic(clazz.generic());
        properties = clazz.properties();
        return this;
    }

    public ClassPropertyDef from(@NonNull String name,
                                 @NonNull Class<?> type,
                                 @NonNull Class<?> owner) throws Exception {
        name(name);
        type(type);
        owner(owner);
        clazz = BeanUtils.get(type());
        abstractType(clazz.abstractType());
        generic(clazz.generic());
        properties = clazz.properties();
        return this;
    }

    @Override
    public boolean canInitialize() {
        if (!abstractType()) {
            if (clazz.emptyConstructor() != null) {
                return Modifier.isPublic(clazz.emptyConstructor().getModifiers());
            }
        }
        return false;
    }


    @Override
    protected PropertyDef findField(@NonNull String[] parts, int index) {
        String name = parts[index];
        if (index == parts.length - 1) {
            if (name.compareTo(name()) == 0) {
                return this;
            } else if (properties.containsKey(name)) {
                return properties.get(name);
            }
        } else {
            if (properties != null && properties.containsKey(name)) {
                PropertyDef def = properties.get(name);
                return def.findField(parts, index + 1);
            }
        }
        return null;
    }
}
