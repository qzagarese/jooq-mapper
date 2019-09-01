package com.github.qzagarese.jooqmapper;


import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;
import com.github.qzagarese.jooqmapper.annotations.OneToOne;
import com.github.qzagarese.jooqmapper.util.RecordUtils;
import org.jooq.Record;
import org.jooq.TableField;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


public class  JooqMapper<T> {
    private static final String TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG = "Target entity %s must declare an annotation of type %s";
    private static final String CANNOT_INSTANTIATE_TYPE_TEMPLATE_MSG = "Cannot instantiate type %s. Make sure your type provides a zero-args public constructor.";
    private static final String FIELD_NOT_FOUND_TEMPLATE_MSG = "Could not find field %s on class %s. Please check your jooq generated classes.";
    private static final String ASSIGNMENT_ERROR_TEMPLATE_MSG = "Cannot assign value of type %s to field (%s) of type %s";

    private final Stream<Record> result;
    private final TableField pivot;

    private Map<T, Set<Record>> indexedResult;


    public JooqMapper(Stream<Record> result, TableField<? extends Record,T> pivot) {
        this.result = result;
        this.pivot = pivot;
        indexedResult = RecordUtils.aggregateBy(result, pivot);
    }

    public <T> Stream<T> buildStream(Class<T> type) {
        return indexedResult.values().stream()
                .map(r -> buildOne(type, r));
    }

    public <T> T build(Class<T> type) {
        return buildOne(type, indexedResult.values().stream().findFirst().orElse(null));
    }

    private <T> T buildOne(Class<T> type, Set<Record> records) {
        JooqTable table = getJooqTable(type);
        T target = instantiate(type);
        Arrays.stream(type.getDeclaredFields()).forEach(f -> {
            if (isLeafProperty(f)) {
                injectProperty(records.stream().findFirst().orElse(null), table, f, target);
            } else if (isOneToOne(f)) {
                injectOneToOne(records, table, f, target);
            }
        });
        return target;
    }

    private <T> JooqTable getJooqTable(Class<T> type) {
        JooqTable table = type.getDeclaredAnnotation(JooqTable.class);
        if (table == null) {
            throw new RuntimeException(String.format(TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG, type, JooqTable.class.getName()));
        }
        return table;
    }

    private <T> void injectProperty(Record r, JooqTable table, Field targetField, T target) {
        if (r == null) {
            return;
        }
        TableField tableField = retrieveTableField(table, targetField.getAnnotation(JooqTableProperty.class).value());
        injectValue(targetField, target, r.get(tableField));
    }

    private <T> void injectOneToOne(Set<Record> records, JooqTable table, Field targetField, T target) {
        if (records == null || records.isEmpty()) {
            return;
        }
        OneToOne annotation = targetField.getAnnotation(OneToOne.class);
        TableField tableField;
        if (annotation.targetTable().equals(OneToOne.TargetTable.THIS)) {
            tableField = retrieveTableField(table, annotation.column());
        } else {
            tableField = retrieveTableField(getJooqTable(targetField.getType()), annotation.column());
        }
        Object injectableValue = new JooqMapper<>(records.stream(), tableField).build(targetField.getType());
        injectValue(targetField, target, injectableValue);
    }

    private TableField retrieveTableField(JooqTable table, String columnName) {
        Field tableFieldMember = null;
        try {
            tableFieldMember = table.value().getDeclaredField(columnName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(String.format(FIELD_NOT_FOUND_TEMPLATE_MSG, columnName, table.value().getName()));
        }
        return cast(extractFieldValue(instantiate(tableFieldMember.getDeclaringClass()), tableFieldMember), TableField.class);
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

    private <T> void injectValue(Field targetField, T target, Object injectableValue) {
        try {
            boolean accessible = targetField.isAccessible();
            targetField.setAccessible(true);
            targetField.set(target, injectableValue);
            targetField.setAccessible(accessible);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IllegalArgumentException iae) {
            throw new RuntimeException(String.format(ASSIGNMENT_ERROR_TEMPLATE_MSG, injectableValue.getClass().getName(),
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

    private boolean isOneToOne(Field f) {
        return f.getAnnotation(OneToOne.class) != null;
    }

}
