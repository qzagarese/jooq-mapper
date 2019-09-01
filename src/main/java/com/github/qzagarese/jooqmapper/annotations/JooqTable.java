package com.github.qzagarese.jooqmapper.annotations;


import org.jooq.impl.TableImpl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JooqTable {

    Class<? extends TableImpl> value();

}
