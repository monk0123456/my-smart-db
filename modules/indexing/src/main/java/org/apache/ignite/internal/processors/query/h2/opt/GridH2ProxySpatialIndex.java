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

package org.apache.ignite.internal.processors.query.h2.opt;

import java.util.List;
import org.gridgain.internal.h2.command.dml.AllColumnsForPlan;
import org.gridgain.internal.h2.engine.Session;
import org.gridgain.internal.h2.index.Cursor;
import org.gridgain.internal.h2.index.Index;
import org.gridgain.internal.h2.index.SpatialIndex;
import org.gridgain.internal.h2.index.SpatialTreeIndex;
import org.gridgain.internal.h2.result.SearchRow;
import org.gridgain.internal.h2.result.SortOrder;
import org.gridgain.internal.h2.table.IndexColumn;
import org.gridgain.internal.h2.table.TableFilter;

/**
 * Allows to have 'free' spatial index for alias columns
 * Delegates the calls to underlying normal index
 */
public class GridH2ProxySpatialIndex extends GridH2ProxyIndex implements SpatialIndex {
    /**
     *
     * @param tbl Table.
     * @param name Name of the proxy index.
     * @param colsList Column list for the proxy index.
     * @param idx Target index.
     */
    public GridH2ProxySpatialIndex(GridH2Table tbl,
                                   String name,
                                   List<IndexColumn> colsList,
                                   Index idx) {
        super(tbl, name, colsList, idx);
    }

    /** {@inheritDoc} */
    @Override public double getCost(Session ses, int[] masks, TableFilter[] filters, int filter,
        SortOrder sortOrder, AllColumnsForPlan cols) {
        return SpatialTreeIndex.getCostRangeIndex(masks, columns) / 10;
    }

    /** {@inheritDoc} */
    @Override public Cursor findByGeometry(TableFilter filter, SearchRow first, SearchRow last, SearchRow intersection) {
        GridH2RowDescriptor desc = ((GridH2Table)idx.getTable()).rowDescriptor();

        return ((SpatialIndex)idx).findByGeometry(filter,
                desc.prepareProxyIndexRow(first),
                desc.prepareProxyIndexRow(last),
                desc.prepareProxyIndexRow(intersection));
    }
}
