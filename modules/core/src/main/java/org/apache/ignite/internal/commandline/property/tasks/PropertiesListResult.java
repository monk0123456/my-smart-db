/*
 * Copyright 2020 GridGain Systems, Inc. and Contributors.
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

package org.apache.ignite.internal.commandline.property.tasks;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import org.apache.ignite.internal.dto.IgniteDataTransferObject;
import org.apache.ignite.internal.processors.task.GridInternal;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 * List of the distributed properties names.
 */
@GridInternal
public class PropertiesListResult extends IgniteDataTransferObject {
    /** */
    private static final long serialVersionUID = 0L;

    /** Properties names. */
    private Collection<String> props = Collections.emptyList();

    /**
     * Constructor for optimized marshaller.
     */
    public PropertiesListResult() {
        // No-op.
    }

    /**
     * @param props Properties.
     */
    public PropertiesListResult(Collection<String> props) {
        this.props = props;
    }

    /** {@inheritDoc} */
    @Override protected void writeExternalData(ObjectOutput out) throws IOException {
        U.writeCollection(out, props);
    }

    /** {@inheritDoc} */
    @Override protected void readExternalData(
        byte protoVer,
        ObjectInput in
    ) throws IOException, ClassNotFoundException {
        props = U.readCollection(in);
    }

    /**
     * @return Properties (name, description) collection.
     */
    public Collection<String> properties() {
        return props;
    }
}
