package com.github.qzagarese.jooqmapper;

public interface PropertyConverter<IN, OUT> {

    OUT convert(IN input);

}
