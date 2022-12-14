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

package org.apache.ignite.internal.managers.encryption;

import java.io.Serializable;

/**
 * Cache group encryption key with identifier. Key is encrypted.
 */
public class GroupKeyEncrypted implements Serializable {
    /** Serial version UID. */
    private static final long serialVersionUID = 0L;

    /** Encryption key ID. */
    private final int id;

    /** Encryption key. */
    private final byte[] key;

    /**
     * @param id Encryption key ID.
     * @param key Encryption key.
     */
    public GroupKeyEncrypted(int id, byte[] key) {
        this.id = id;
        this.key = key;
    }

    /**
     * @return Encryption key ID.
     */
    public int id() {
        return id;
    }

    /**
     * @return Encryption key.
     */
    public byte[] key() {
        return key;
    }
}
