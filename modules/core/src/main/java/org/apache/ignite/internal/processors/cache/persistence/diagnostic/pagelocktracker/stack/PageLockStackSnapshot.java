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

package org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.stack;

import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.PageLockDump;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.PageMetaInfoStore;

/**
 * Page lock stack snapshot.
 */
public class PageLockStackSnapshot extends PageLockDump {

    /** */
    public final PageMetaInfoStore pageIdLocksStack;

    /**
     *
     */
    public PageLockStackSnapshot(
        String name,
        long time,
        int headIdx,
        PageMetaInfoStore pageIdLocksStack,
        int nextOp,
        int nextOpStructureId,
        long nextOpPageId
    ) {
        super(name, time, headIdx, nextOp, nextOpStructureId, nextOpPageId);
        this.pageIdLocksStack = pageIdLocksStack;
    }
}
