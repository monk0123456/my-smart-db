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

package org.apache.ignite.internal.managers.systemview.walker;

import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.spi.systemview.view.SystemViewRowAttributeWalker;
import org.apache.ignite.spi.systemview.view.datastructures.SetView;

/**
 * Generated by {@code org.apache.ignite.codegen.SystemViewRowAttributeWalkerGenerator}.
 * {@link SetView} attributes walker.
 * 
 * @see SetView
 */
public class SetViewWalker implements SystemViewRowAttributeWalker<SetView> {
    /** {@inheritDoc} */
    @Override public void visitAll(AttributeVisitor v) {
        v.accept(0, "id", IgniteUuid.class);
        v.accept(1, "name", String.class);
        v.accept(2, "size", int.class);
        v.accept(3, "groupName", String.class);
        v.accept(4, "groupId", int.class);
        v.accept(5, "collocated", boolean.class);
        v.accept(6, "removed", boolean.class);
    }

    /** {@inheritDoc} */
    @Override public void visitAll(SetView row, AttributeWithValueVisitor v) {
        v.accept(0, "id", IgniteUuid.class, row.id());
        v.accept(1, "name", String.class, row.name());
        v.acceptInt(2, "size", row.size());
        v.accept(3, "groupName", String.class, row.groupName());
        v.acceptInt(4, "groupId", row.groupId());
        v.acceptBoolean(5, "collocated", row.collocated());
        v.acceptBoolean(6, "removed", row.removed());
    }

    /** {@inheritDoc} */
    @Override public int count() {
        return 7;
    }
}
