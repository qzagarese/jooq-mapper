package com.github.qzagarese.jooqmapper;


import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;
import com.github.qzagarese.jooqmapper.util.RecordUtils;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.TableField;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;


public class  JooqMapper<T> {
    private static final String TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG = "Target entity must declare an annotation of type %s";
    private static final String CANNOT_INSTANTIATE_TYPE_TEMPLATE_MSG = "Cannot instantiate type %s. Make sure your type provides a zero-args public constructor.";
    private static final String FIELD_NOT_FOUND_TEMPLATE_MSG = "Could not find field %s on class %s. Please check your jooq generated classes.";
    private static final String ASSIGNMENT_ERROR_TEMPLATE_MSG = "Cannot assign value of type %s to field (%s) of type %s";

    private final Result<Record> result;
    private final TableField pivot;

    private Map<T, Record> indexedResult;


    public JooqMapper(Result<Record> result, TableField<? extends Record,T> pivot) {
        this.result = result;
        this.pivot = pivot;
        indexedResult = RecordUtils.indexBy(result.stream(), pivot);
    }

    public <T> Stream<T> buildStream(Class<T> type) {
        return indexedResult.values().stream()
                .map(r -> buildOne(type, r));
    }

    public <T> T build(Class<T> type) {
        return buildOne(type, indexedResult.values().stream().findFirst().orElse(null));
    }

    private <T> T buildOne(Class<T> type, Record record) {
        JooqTable table = type.getDeclaredAnnotation(JooqTable.class);
        if (table == null) {
            throw new RuntimeException(String.format(TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG, JooqTable.class.getName()));
        }


        T target = instantiate(type);
        Arrays.stream(type.getDeclaredFields()).forEach(f -> {
            if (isLeafProperty(f)) {
                injectProperty(record, table, f, target);
            }
        });
        return target;
    }

    private <T> T instantiate(Class<T> type) {
        T target = null;
        try {
            target = type.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(String.format(CANNOT_INSTANTIATE_TYPE_TEMPLATE_MSG, type.getName()));
        }
        return target;
    }

    private <T> void injectProperty(Record r, JooqTable table, Field targetField, T target) {
        JooqTableProperty annotation = targetField.getAnnotation(JooqTableProperty.class);
        Field tableFieldMember = null;
        try {
            tableFieldMember = table.value().getDeclaredField(annotation.value());
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(String.format(FIELD_NOT_FOUND_TEMPLATE_MSG, annotation.value(), table.value().getName()));
        }

        TableField tableField = cast(extractFieldValue(instantiate(tableFieldMember.getDeclaringClass()), tableFieldMember), TableField.class);
        injectValue(r, targetField, target, tableField);


    }

    private <T> void injectValue(Record r, Field targetField, T target, TableField tableField) {
        Object recordFieldValue = null;
        try {
            boolean accessible = targetField.isAccessible();
            targetField.setAccessible(true);
            recordFieldValue = r.get(tableField);
            targetField.set(target, recordFieldValue);
            targetField.setAccessible(accessible);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IllegalArgumentException iae) {
            throw new RuntimeException(String.format(ASSIGNMENT_ERROR_TEMPLATE_MSG, recordFieldValue.getClass().getName(),
                    targetField.getName(),
                    targetField.getType().getName()));
        }
    }

    private <T> T cast(Object obj, Class<T> type) {
        if (type.isAssignableFrom(obj.getClass())) {
            return type.cast(obj);
        } else {
            throw new ClassCastException(String.format("Cannot assign %s to %s", obj.getClass().getName(), type.getName()));
        }
    }

    private Object extractFieldValue(Object instance, Field f) {
        boolean accessible = f.isAccessible();
        f.setAccessible(true);
        try {
            Object result = f.get(instance);
            f.setAccessible(accessible);
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean isLeafProperty(Field f) {
        return f.getAnnotation(JooqTableProperty.class) != null;
    }

}
