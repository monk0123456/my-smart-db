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

package org.apache.ignite.mxbean;

import java.util.List;

/**
 * MBean that provides ability to terminate worker that registered in the workers registry.
 */
@MXBeanDescription("MBean that provides ability to terminate worker that registered in the workers registry.")
public interface WorkersControlMXBean {
    /**
     * Returns names of all registered workers.
     *
     * @return Worker names.
     */
    @MXBeanDescription("Names of registered workers.")
    public List<String> getWorkerNames();

    /**
     * Terminates worker.
     *
     * @param name Worker name.
     * @return {@code True} if worker has been terminated successfully, {@code false} otherwise.
     */
    @MXBeanDescription("Terminates worker.")
    public boolean terminateWorker(
        @MXBeanParameter(name = "name", description = "Name of worker to terminate.") String name
    );

    /**
     * Stops thread by {@code name}, if exists and unique.
     *
     * @param name Thread name.
     * @return {@code True} if thread has been stopped successfully, {@code false} otherwise.
     */
    @MXBeanDescription("Stops thread by unique name.")
    public boolean stopThreadByUniqueName(
        @MXBeanParameter(name = "name", description = "Name of thread to stop.") String name
    );

    /**
     * Stops thread by {@code id}, if exists.
     *
     * @param id Thread id.
     * @return {@code True} if thread has been stopped successfully, {@code false} otherwise.
     */
    @MXBeanDescription("Stops thread by id.")
    public boolean stopThreadById(
        @MXBeanParameter(name = "id", description = "Id of thread to stop.") long id
    );
}
