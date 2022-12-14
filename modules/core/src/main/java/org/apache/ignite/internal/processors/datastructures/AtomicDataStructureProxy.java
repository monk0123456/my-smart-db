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

package org.apache.ignite.internal.processors.datastructures;

import java.io.Externalizable;
import javax.cache.processor.EntryProcessorException;
import org.apache.ignite.IgniteCacheRestartingException;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.IgniteInternalCache;
import org.apache.ignite.internal.processors.cluster.IgniteChangeGlobalStateSupport;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.future.IgniteFutureImpl;
import org.apache.ignite.internal.util.tostring.GridToStringExclude;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 * Represents base class for atomic data structures.
 */
public abstract class AtomicDataStructureProxy<V extends AtomicDataStructureValue>
    implements GridCacheRemovable,IgniteChangeGlobalStateSupport {
    /** Logger. */
    protected IgniteLogger log;

    /** Removed flag. */
    @GridToStringInclude
    protected volatile boolean rmvd;

    @GridToStringExclude
    /** Suspended future. */
    private volatile GridFutureAdapter<Void> suspendFut;

    @GridToStringExclude
    /** Check removed flag. */
    private volatile boolean rmvCheck;

    /** Structure name. */
    @GridToStringInclude
    protected String name;

    /** Structure key. */
    @GridToStringInclude
    protected GridCacheInternalKey key;

    /** Structure projection. */
    protected IgniteInternalCache<GridCacheInternalKey, V> cacheView;

    /** Cache context. */
    @GridToStringInclude
    protected volatile GridCacheContext<GridCacheInternalKey, V> ctx;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public AtomicDataStructureProxy() {
        // No-op.
    }

    /**
     * Default constructor.
     *
     * @param name Structure name.
     * @param key Structure key.
     * @param cacheView Cache projection.
     */
    public AtomicDataStructureProxy(String name,
        GridCacheInternalKey key,
        IgniteInternalCache<GridCacheInternalKey, V> cacheView)
    {
        assert key != null;
        assert cacheView != null;

        this.ctx = cacheView.context();
        this.key = key;
        this.cacheView = cacheView;
        this.name = name;

        log = ctx.logger(getClass());
    }

    /**
     * @return Datastructure name.
     */
    public String name() {
        return name;
    }

    /**
     * @return Key value.
     */
    public GridCacheInternalKey key() {
        return key;
    }

    /**
     * @return Removed flag.
     */
    public boolean removed() {
        return rmvd;
    }

    /**
     * Check removed status.
     *
     * @throws IgniteException If removed.
     */
    protected void checkRemoved() throws IgniteException {
        if (rmvd)
            throw removedError();

        GridFutureAdapter<Void> suspendFut0 = suspendFut;

        if (suspendFut0 != null && !suspendFut0.isDone())
            throw suspendedError();

        if (rmvCheck) {
            try {
                rmvd = cacheView.get(key) == null;
            }
            catch (IgniteCheckedException e) {
                throw U.convertException(e);
            }

            rmvCheck = false;

            if (rmvd) {
                ctx.kernalContext().dataStructures().onRemoved(key, this);

                throw removedError();
            }
        }
    }

    /**
     * Checks removed status after fail.
     *
     * @param cause Initial exception.
     * @return Ignite runtime exception that corresponds the original {@code cause}.
     */
    protected IgniteException checkRemovedAfterFail(Exception cause) {
        assert cause != null : "The original cause must not be null.";

        needCheckNotRemoved();

        try {
            checkRemoved();
        }
        catch (Exception e) {
            // The original exception should be returned.
        }

        if (cause instanceof IgniteCheckedException)
            return U.convertException((IgniteCheckedException) cause);
        else if (cause instanceof EntryProcessorException)
            return new IgniteException(cause.getMessage(), cause);
        else {
            assert cause instanceof IgniteException;

            return (IgniteException)cause;
        }
    }

    /**
     * @return Error.
     */
    private IgniteException removedError() {
        return new IgniteException("Sequence was removed from cache: " + name);
    }

    /**
     * @return Error.
     */
    private IllegalStateException suspendedError() {
        throw new IgniteCacheRestartingException(new IgniteFutureImpl<>(suspendFut), ctx.name());
    }

    /** {@inheritDoc} */
    @Override public boolean onRemoved() {
        rmvd = true;

        if (log.isDebugEnabled())
            log.debug("DataStructure has been removed [" + this + "].");

        return rmvd;
    }

    /** {@inheritDoc} */
    @Override public void needCheckNotRemoved() {
        rmvCheck = true;
    }

    /** {@inheritDoc} */
    @Override public void suspend() {
        suspendFut = new GridFutureAdapter<>();
    }

    /** {@inheritDoc} */
    @Override public void restart(IgniteInternalCache cache) {
        invalidateLocalState();

        cacheView = cache;
        ctx = cache.context();
        rmvCheck = true;
        suspendFut.onDone();
    }

    /** {@inheritDoc} */
    @Override public void onActivate(GridKernalContext kctx) throws IgniteCheckedException {
        this.ctx = kctx.cache().<GridCacheInternalKey, V>context().cacheContext(ctx.cacheId());
        this.cacheView = ctx.cache();
    }

    /** {@inheritDoc} */
    @Override public void onDeActivate(GridKernalContext kctx) {
        // No-op.
    }

    /**
     * Invalidates local state.
     */
    protected void invalidateLocalState() {
        // No-op
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(AtomicDataStructureProxy.class, this);
    }

}
