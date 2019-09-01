package com.github.qzagarese.jooqmapper.util;

import io.vavr.Tuple;
import org.jooq.Record;
import org.jooq.TableField;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordUtils {


    public static <K, R extends Record> Map<K, Record> indexBy(Stream<Record> input,
                                                                   TableField<R, K> indexKey) {
        return input.map(r -> Tuple.of(r.get(indexKey), r))
                .collect(Collectors.toMap(t -> t._1(), t -> t._2()));
    }

}
