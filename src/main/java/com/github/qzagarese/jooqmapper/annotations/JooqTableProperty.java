package com.github.qzagarese.jooqmapper.annotations;

import org.jooq.TableField;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JooqTableProperty {

    String value();

}
