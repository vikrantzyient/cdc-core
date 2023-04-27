package ai.sapper.cdc.entity;

import ai.sapper.cdc.common.utils.ReflectionUtils;
import ai.sapper.cdc.entity.schema.SchemaField;
import lombok.NonNull;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.ByteBuffer;

public class DataTypeUtils {
    public static DataType<?> createInstance(@NonNull DataType<?> source, long size, int... params) {
        if (source instanceof DecimalType) {
            Integer scale = null;
            Integer prec = null;
            if (params != null) {
                if (params.length >= 1) {
                    scale = params[0];
                }
                if (params.length >= 2) {
                    prec = params[1];
                }
            }
            return new DecimalType<>((DecimalType<?>) source, scale, prec);
        } else if (source instanceof IntegerType) {
            Integer min = null;
            Integer max = null;
            if (params != null) {
                if (params.length > 1) {
                    min = params[0];
                }
                if (params.length > 2) {
                    max = params[1];
                }
            }
            return new IntegerType((IntegerType) source, min, max);
        } else if (size > 0) {
            if (source instanceof TextType) {
                return new TextType((TextType) source, size);
            } else if (source instanceof BinaryType) {
                return new BinaryType((BinaryType) source, size);
            }
        }
        return source;
    }

    public static boolean isCompatible(@NonNull DataType<?> target,
                                       @NonNull DataType<?> current) {
        if (current.equals(target)) return true;
        if (current.getJavaType().equals(String.class)) {
            Class<?> type = target.getJavaType();
            if (ReflectionUtils.isPrimitiveTypeOrString(type)) {
                return true;
            } else if (type.equals(ByteBuffer.class)) {
                return true;
            }
        } else if (ReflectionUtils.isNumericType(current.getJavaType()) &&
                ReflectionUtils.isNumericType(target.getJavaType())) {
            return true;
        }
        return false;
    }

    public static boolean isNullType(@NonNull SchemaField field) {
        return field.getName().compareToIgnoreCase("null") == 0 ||
                field.getDataType().getJavaType().equals(ObjectUtils.Null.class);
    }
}
