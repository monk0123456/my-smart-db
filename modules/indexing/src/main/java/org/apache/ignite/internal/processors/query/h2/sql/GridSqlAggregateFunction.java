/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.h2.sql;

import org.apache.ignite.internal.util.typedef.F;
import org.gridgain.internal.h2.expression.aggregate.AggregateType;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.AVG;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.COUNT;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.COUNT_ALL;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.GROUP_CONCAT;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.MAX;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.MIN;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.SUM;
import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.UNKNOWN_FUNCTION;

/**
 * Aggregate function.
 */
public class GridSqlAggregateFunction extends GridSqlFunction {
    /** */
    private static final GridSqlFunctionType[] TYPE_INDEX = new GridSqlFunctionType[] {
        COUNT_ALL, COUNT, GROUP_CONCAT, SUM, MIN, MAX, AVG,
//        STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP, BOOL_OR, BOOL_AND, SELECTIVITY, HISTOGRAM,
    };

    /**
     * Map type.
     *
     * @param type H2 type.
     * @return Ignite type, {@code null} if not supported.
     */
    @Nullable private static GridSqlFunctionType mapType(AggregateType type) {
        switch (type) {
            case COUNT_ALL:
                return COUNT_ALL;

            case COUNT:
                return COUNT;

            case LISTAGG:
                return GROUP_CONCAT;

            case SUM:
                return SUM;

            case MIN:
                return MIN;

            case MAX:
                return MAX;

            case AVG:
                return AVG;
        }

        return null;
    }

    /** */
    private final boolean distinct;

    /** */
    private GridSqlElement groupConcatSeparator;

    /** */
    private GridSqlElement[] groupConcatOrderExpression;

    /** */
    private boolean[] groupConcatOrderDesc;

    /**
     * @param distinct Distinct.
     * @param type Type.
     */
    public GridSqlAggregateFunction(boolean distinct, GridSqlFunctionType type) {
        super(type);

        this.distinct = distinct;
    }

    /**
     * @param distinct Distinct.
     * @param type Type.
     */
    public GridSqlAggregateFunction(boolean distinct, AggregateType type) {
        this(distinct, mapType(type));
    }

    /**
     * @param type Type.
     * @param name Name.
     * @param distinct Distinct.
     */
    public GridSqlAggregateFunction(GridSqlFunctionType type, String name, boolean distinct) {
        super(null, type, name);

        this.distinct = distinct;
    }

    /**
     * Checks if the aggregate type is valid.
     *
     * @param type Aggregate type.
     * @return True is valid, otherwise false.
     */
    protected static boolean isValidType(AggregateType type) {
        return mapType(type) != null;
    }

    /**
     * @return Distinct.
     */
    public boolean distinct() {
        return distinct;
    }

    /**
     * @param orderExpression Order expression.
     * @param orderDesc Order descending flag.
     * @return {@code this} for chaining.
     */
    public GridSqlAggregateFunction setGroupConcatOrder(GridSqlElement[] orderExpression, boolean[] orderDesc) {
        groupConcatOrderExpression = orderExpression;
        groupConcatOrderDesc = orderDesc;

        return this;
    }

    /**
     * @return {@code true} in case GROUP_CONCAT function contains ORDER BY expressions.
     */
    public boolean hasGroupConcatOrder() {
        return !F.isEmpty(groupConcatOrderExpression);
    }

    /**
     * @param separator Separator expression.
     * @return {@code this} for chaining.
     */
    public GridSqlAggregateFunction setGroupConcatSeparator(GridSqlElement separator) {
        groupConcatSeparator = separator;

        return this;
    }

    /**
     * @return Separator expression.
     */
    public GridSqlElement getGroupConcatSeparator() {
        return groupConcatSeparator;
    }

    /** {@inheritDoc}  */
    @Override public String getSQL() {
        if (type == COUNT_ALL)
            return "COUNT(*)";

        StringBuilder buff = new StringBuilder(name()).append('(');

        if (distinct)
            buff.append("DISTINCT ");

        if (type == UNKNOWN_FUNCTION) {
            for (int i = 0; i < size(); i++) {
                if (i > 0)
                    buff.append(", ");

                buff.append(child(i).getSQL());
            }
        }
        else {
            buff.append(child().getSQL());
        }

        if (!F.isEmpty(groupConcatOrderExpression)) {
            buff.append(" ORDER BY ");

            for (int i = 0; i < groupConcatOrderExpression.length; i++) {
                if (i > 0)
                    buff.append(", ");

                buff.append(groupConcatOrderExpression[i].getSQL());

                if (groupConcatOrderDesc[i])
                    buff.append(" DESC");
            }
        }

        if (groupConcatSeparator != null)
            buff.append(" SEPARATOR ").append(groupConcatSeparator.getSQL());

        buff.append(')');

        return buff.toString();
    }
}