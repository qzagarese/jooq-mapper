package com.github.qzagarese.jooqmapper.util;

import io.vavr.Tuple;
import org.jooq.Record;
import org.jooq.TableField;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordUtils {


    public static <K, R extends Record> Map<K, Record> indexBy(Stream<Record> input,
                                                                   TableField<R, K> indexKey) {
        return input.map(r -> Tuple.of(r.get(indexKey), r))
                .collect(Collectors.toMap(t -> t._1(), t -> t._2()));
    }

    public static <K, R extends Record> Map<K, Set<Record>> aggregateBy(Stream<Record> source, TableField<R, K> indexKey) {
        return source.collect(Collectors.groupingBy(r -> Optional.ofNullable(r.get(indexKey)), Collectors.toSet()))
                .entrySet()
                .stream()
                .filter(e -> e.getKey().isPresent())
                .map(e -> Tuple.of(e.getKey().get(), e.getValue()))
                .collect(Collectors.toMap(t -> t._1(), t -> t._2()));
    }

}
