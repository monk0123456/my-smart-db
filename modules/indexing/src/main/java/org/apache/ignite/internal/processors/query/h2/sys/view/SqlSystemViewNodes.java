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

package org.apache.ignite.internal.processors.query.h2.sys.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.util.typedef.F;
import org.gridgain.internal.h2.engine.Session;
import org.gridgain.internal.h2.result.Row;
import org.gridgain.internal.h2.result.SearchRow;
import org.gridgain.internal.h2.value.Value;

/**
 * System view: nodes.
 */
public class SqlSystemViewNodes extends SqlAbstractLocalSystemView {
    /**
     * @param ctx Grid context.
     */
    public SqlSystemViewNodes(GridKernalContext ctx) {
        super("NODES", "Topology nodes", ctx, "NODE_ID",
            newColumn("NODE_ID", Value.UUID),
            newColumn("CONSISTENT_ID"),
            newColumn("VERSION"),
            newColumn("IS_CLIENT", Value.BOOLEAN),
            newColumn("IS_DAEMON", Value.BOOLEAN),
            newColumn("NODE_ORDER", Value.INT),
            newColumn("ADDRESSES"),
            newColumn("HOSTNAMES")
        );
    }

    /** {@inheritDoc} */
    @Override public Iterator<Row> getRows(Session ses, SearchRow first, SearchRow last) {
        List<Row> rows = new ArrayList<>();

        Collection<ClusterNode> nodes;

        SqlSystemViewColumnCondition idCond = conditionForColumn("NODE_ID", first, last);

        if (idCond.isEquality()) {
            UUID nodeId = uuidFromValue(idCond.valueForEquality());

            nodes = nodeId == null ? Collections.emptySet() : Collections.singleton(ctx.discovery().node(nodeId));
        }
        else
            nodes = F.concat(false, ctx.discovery().allNodes(), ctx.discovery().daemonNodes());

        for (ClusterNode node : nodes) {
            if (node != null)
                rows.add(
                    createRow(
                        ses,
                        node.id(),
                        toStringSafe(node.consistentId()),
                        node.version(),
                        node.isClient(),
                        node.isDaemon(),
                        node.order(),
                        node.addresses(),
                        node.hostNames()
                    )
                );
        }

        return rows.iterator();
    }

    /** {@inheritDoc} */
    @Override public boolean canGetRowCount() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public long getRowCount() {
        return ctx.discovery().allNodes().size() + ctx.discovery().daemonNodes().size();
    }
}
