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

package org.apache.ignite.spi.checkpoint.noop;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.resources.LoggerResource;
import org.apache.ignite.spi.IgniteSpiAdapter;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.IgniteSpiMultipleInstancesSupport;
import org.apache.ignite.spi.IgniteSpiNoop;
import org.apache.ignite.spi.checkpoint.CheckpointListener;
import org.apache.ignite.spi.checkpoint.CheckpointSpi;
import org.jetbrains.annotations.Nullable;

/**
 * No-op implementation of {@link org.apache.ignite.spi.checkpoint.CheckpointSpi}. This is default implementation
 * since {@code 4.5.0} version.
 */
@IgniteSpiNoop
@IgniteSpiMultipleInstancesSupport(true)
public class NoopCheckpointSpi extends IgniteSpiAdapter implements CheckpointSpi {
    /** Logger. */
    @LoggerResource
    private IgniteLogger log;

    /** Whether a warning about disabled checkpoints was already logged. */
    private final AtomicBoolean warnedAboutDisabledCheckpoints = new AtomicBoolean(false);

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String igniteInstanceName) throws IgniteSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws IgniteSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Nullable @Override public byte[] loadCheckpoint(String key) throws IgniteSpiException {
        warnOnceAboutDisabledCheckpoints();
        return null;
    }

    /** {@inheritDoc} */
    @Override public boolean saveCheckpoint(String key, byte[] state, long timeout, boolean overwrite) {
        warnOnceAboutDisabledCheckpoints();
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean removeCheckpoint(String key) {
        warnOnceAboutDisabledCheckpoints();
        return false;
    }

    /** {@inheritDoc} */
    @Override public void setCheckpointListener(CheckpointListener lsnr) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public NoopCheckpointSpi setName(String name) {
        super.setName(name);

        return this;
    }

    /**
     * Logs a warning that checkpoints are disabled. Only does so on first invocation, subsequent ones are ignored.
     */
    private void warnOnceAboutDisabledCheckpoints() {
        if (warnedAboutDisabledCheckpoints.compareAndSet(false, true))
            U.warn(log, "Checkpoints are disabled (to enable configure any GridCheckpointSpi implementation)");
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(NoopCheckpointSpi.class, this);
    }
}