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

package org.apache.ignite.internal.processors.schedule;

import java.util.concurrent.Callable;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.GridProcessorAdapter;
import org.apache.ignite.scheduler.SchedulerFuture;

/**
 * Schedules cron-based execution of grid tasks and closures. Abstract class was introduced to
 * avoid mandatory runtime dependency on cron library.
 */
public abstract class IgniteScheduleProcessorAdapter extends GridProcessorAdapter {
    /**
     * @param ctx Kernal context.
     */
    protected IgniteScheduleProcessorAdapter(GridKernalContext ctx) {
        super(ctx);
    }

    /**
     * @param c Closure to schedule to run as a background cron-based job.
     * @param pattern Scheduling pattern in UNIX cron format with prefix "{n1, n2} " where n1 is delay of scheduling
     *      and n2 is the number of task calls.
     * @return Descriptor of the scheduled execution.
     */
    public abstract SchedulerFuture<?> schedule(final String name, final Runnable c, String pattern);

    /**
     * @param c Closure to schedule to run as a background cron-based job.
     * @param pattern Scheduling pattern in UNIX cron format with prefix "{n1, n2} " where n1 is delay of scheduling
     *      and n2 is the number of task calls.
     * @return Descriptor of the scheduled execution.
     */
    public abstract <R> SchedulerFuture<R> schedule(final String name, Callable<R> c, String pattern);
}