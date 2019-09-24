package com.github.qzagarese.jooqmapper.util;

import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import lombok.Builder;
import lombok.Getter;
import org.jooq.TableField;

@Builder
@Getter
public class RelationshipGraphNode {


    private TableField prinaryField;
    private JooqTable table;
    private Class<?> entityType;
    private Object primaryFieldValue;
    private Object target;

}
