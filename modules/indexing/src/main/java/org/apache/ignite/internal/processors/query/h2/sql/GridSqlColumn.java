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

import java.util.Collections;
import org.apache.ignite.internal.util.typedef.F;
import org.gridgain.internal.h2.command.Parser;
import org.gridgain.internal.h2.expression.Expression;
import org.gridgain.internal.h2.table.Column;

/**
 * Column.
 */
public class GridSqlColumn extends GridSqlElement {
    /** */
    private GridSqlAst from;

    /** */
    private String schema;

    /** */
    private String tblAlias;

    /** */
    private String colName;

    /** */
    private Column col;

    /**
     * @param col Column.
     * @param from From.
     * @param colName Column name.
     */
    public GridSqlColumn(Column col, GridSqlAst from, String colName) {
        this(col, from, null, null, colName);
    }

    /**
     * @param col Column.
     * @param from From.
     * @param schema Schema name.
     * @param tblAlias Table alias.
     * @param colName Column name.
     */
    public GridSqlColumn(Column col, GridSqlAst from, String schema, String tblAlias, String colName) {
        super(Collections.<GridSqlAst>emptyList());

        assert !F.isEmpty(colName) : colName;

        this.col = col;
        this.from = from;

        this.colName = colName;
        this.schema = schema;
        this.tblAlias = tblAlias;
    }

    /**
     * @return Column name.
     */
    public String columnName() {
        return colName;
    }

    /**
     * @return Schema name.
     */
    public String schema() {
        return schema;
    }

    /**
     * @param tblAlias Table alias.
     */
    public void tableAlias(String tblAlias) {
        this.tblAlias = tblAlias;
    }

    /**
     * @return Table alias.
     */
    public String tableAlias() {
        return tblAlias;
    }

    /** {@inheritDoc}  */
    @Override public String getSQL() {
        StringBuilder sb = new StringBuilder();

        if (schema != null) {
            Parser.quoteIdentifier(sb, schema, true);

            sb.append(".");
        }

        if (tblAlias != null) {
            Parser.quoteIdentifier(sb, tblAlias, true);

            sb.append(".");
        }

        Parser.quoteIdentifier(sb, colName, true);

        return sb.toString();
    }

    /**
     * @return Expression in from.
     */
    public GridSqlAst expressionInFrom() {
        return from;
    }

    /**
     * @param from Expression in from.
     */
    public void expressionInFrom(GridSqlAlias from) {
        this.from = from;
    }

    /**
     * @return Default value.
     */
    public Object defaultValue() {
        Expression dfltExpr = col.getDefaultExpression();

        return dfltExpr != null ? col.convert(dfltExpr.getValue(null)).getObject() : null;
    }

    /**
     * @return Precision.
     */
    public int precision() {
        return (int) col.getType().getPrecision();
    }

    /**
     * @return Scale.
     */
    public int scale() {
        return col.getType().getScale();
    }

    /**
     * @return H2 Column.
     */
    public Column column() {
        return col;
    }
}
