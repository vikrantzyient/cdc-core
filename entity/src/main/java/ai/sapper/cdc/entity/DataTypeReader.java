package ai.sapper.cdc.entity;

import lombok.NonNull;

public interface DataTypeReader<T> {
    T read(@NonNull Object data) throws Exception;

    Class<?> getType();
}
