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
    private Map<String, PropertyDef> properties;

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

    public ClassPropertyDef from(@NonNull String name, @NonNull Class<?> type) throws Exception {
        name(name);
        type(type);
        clazz = BeanUtils.get(type);
        abstractType(clazz.abstractType());
        generic(clazz.generic());
        properties = clazz.properties();
        return this;
    }

    public ClassPropertyDef from(@NonNull Field field) throws Exception {
        return from(field.getName(), field.getType());
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
}