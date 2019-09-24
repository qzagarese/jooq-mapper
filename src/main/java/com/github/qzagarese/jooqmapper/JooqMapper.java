package com.github.qzagarese.jooqmapper;


import com.github.qzagarese.jooqmapper.annotations.*;
import com.github.qzagarese.jooqmapper.util.RecordUtils;
import com.github.qzagarese.jooqmapper.util.RelationshipGraphNode;
import io.vavr.Tuple3;
import org.jooq.Record;
import org.jooq.TableField;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class  JooqMapper<K> {
    private static final String TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG = "Target entity %s must declare an annotation of type %s";
    private static final String CANNOT_INSTANTIATE_TYPE_TEMPLATE_MSG = "Cannot instantiate type %s. Make sure your type provides a zero-args public constructor.";
    private static final String FIELD_NOT_FOUND_TEMPLATE_MSG = "Could not find field %s on class %s. Please check your jooq generated classes.";
    private static final String ASSIGNMENT_ERROR_TEMPLATE_MSG = "Cannot assign value of type %s to field (%s) of type %s";
    private static final String NO_LIST_OR_SET_FOUND_TEMPLATE_MSG = "Field %s must be a subtype of %s or %s.";

    private final Stream<Record> result;
    private final TableField pivot;
    private final List<RelationshipGraphNode> predecessors;

    private Map<K, Set<Record>> indexedResult;


    public JooqMapper(Stream<Record> result, TableField<? extends Record, K> pivot) {
        this(result, pivot, new ArrayList<>());
    }

    private JooqMapper(Stream<Record> result, TableField<? extends Record, K> pivot, List<RelationshipGraphNode> predecessors) {
        this.result = result;
        this.pivot = pivot;
        indexedResult = RecordUtils.aggregateBy(result, pivot);
        this.predecessors = predecessors;
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
            } else if (isOneToMany(f)) {
                injectOneToMany(records, f, target);
            } else if (isEmbedded(f)) {
                injectEmbedded(records.stream().findFirst().orElse(null), table, f, target);
            }
        });
        return target;
    }

    private <T> void injectProperty(Record r, JooqTable table, Field targetField, T target) {
        if (r == null) {
            return;
        }
        TableField tableField = retrieveTableField(table, targetField.getAnnotation(JooqTableProperty.class).value());
        Object injectableValue = r.get(tableField);
        if (hasConverter(targetField)) {
            Converter converter = targetField.getAnnotation(Converter.class);
            injectableValue = cast(instantiate(converter.value()), PropertyConverter.class).convert(injectableValue);
        }
        injectValue(targetField, target, injectableValue);
    }

    private <T> void injectEmbedded(Record r, JooqTable table, Field targetField, T target) {
        if (r == null) {
            return;
        }
        Object injectableValue = instantiate(targetField.getType());
        Arrays.stream(targetField.getType().getDeclaredFields()).forEach(innerField -> {
            if (isLeafProperty(innerField)) {
                injectProperty(r, table, innerField, injectableValue);
            }
        });
        injectValue(targetField, target, injectableValue);
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




        Object injectableValue = findAmongPredecessors(targetField.getType(), records.stream().findFirst(), getJooqTable(targetField.getType()));

        injectableValue = injectableValue == null
                ? new JooqMapper<>(records.stream(), tableField, buildPredecessors(table, records, this.pivot, target))
                    .build(targetField.getType())
                : injectableValue;

        injectValue(targetField, target, injectableValue);
    }

    private Object findAmongPredecessors(Class<?> type, Optional<Record> optionalRecord, JooqTable table) {
        if (!optionalRecord.isPresent()) {
            return Optional.empty();
        }

        Record r = optionalRecord.get();
        return predecessors.stream()
                .filter(p -> {
                    return p.getEntityType().equals(type)
                            && p.getTable().equals(table)
                            && r.get(p.getPrinaryField()).equals(p.getPrimaryFieldValue());
                })
                .map(p -> p.getTarget())
                .findFirst()
                .orElse(null);
    }

    private <T> List<RelationshipGraphNode> buildPredecessors(JooqTable table, Set<Record> records, TableField tableField, T target) {
        RelationshipGraphNode node = RelationshipGraphNode.builder()
                .entityType(target.getClass())
                .prinaryField(tableField)
                .target(target)
                .primaryFieldValue(records.stream().findFirst().get().get(tableField))
                .table(table)
                .build();
        return Stream.concat(predecessors.stream(), Stream.of(node)).collect(Collectors.toList());
    }

    private <T> void injectOneToMany(Set<Record> records, Field targetField, T target) {
        if (records == null || records.isEmpty()) {
            return;
        }
        OneToMany annotation = targetField.getAnnotation(OneToMany.class);
        JooqTable table = getJooqTable(annotation.targetEntity());
        TableField tableField = retrieveTableField(table, annotation.otherPrimaryKeyColumn());
        Stream<?> entitiesStream = new JooqMapper<>(records.stream(), tableField, buildPredecessors(getJooqTable(target.getClass()), records, this.pivot, target))
                .buildStream(annotation.targetEntity());
        Collection collection = convertStream(entitiesStream, targetField);
        injectValue(targetField, target, collection);
    }

    private Collection convertStream(Stream<?> entitiesStream, Field targetField) {
         if (Set.class.isAssignableFrom(targetField.getType())) {
             return entitiesStream.collect(Collectors.toSet());
         } else if (List.class.isAssignableFrom(targetField.getType())) {
             return entitiesStream.collect(Collectors.toList());
         } else {
             throw new RuntimeException(String.format(NO_LIST_OR_SET_FOUND_TEMPLATE_MSG, targetField.getName(),
                     List.class.getName(),
                     Set.class.getName()));
         }

    }

    private <T> JooqTable getJooqTable(Class<T> type) {
        JooqTable table = type.getDeclaredAnnotation(JooqTable.class);
        if (table == null) {
            throw new RuntimeException(String.format(TABLE_ANNOTATION_NOT_FOUND_TEMPLATE_MSG, type, JooqTable.class.getName()));
        }
        return table;
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
        return f.isAnnotationPresent(JooqTableProperty.class);
    }

    private boolean isOneToOne(Field f) {
        return f.isAnnotationPresent(OneToOne.class);
    }

    private boolean isOneToMany(Field f) {
        return f.isAnnotationPresent(OneToMany.class);
    }

    private boolean isEmbedded(Field f) {
        return f.isAnnotationPresent(Embedded.class);
    }

    private boolean hasConverter(Field targetField) {
        return targetField.isAnnotationPresent(Converter.class);
    }

}
