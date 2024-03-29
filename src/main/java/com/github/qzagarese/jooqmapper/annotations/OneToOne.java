package com.github.qzagarese.jooqmapper.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToOne {

    String value() default "";

    String column();

    TargetTable targetTable() default TargetTable.THIS;

    enum TargetTable {
        THIS, OTHER;
    }

}
