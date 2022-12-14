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

package org.apache.ignite.spi.metric;

import java.util.function.Predicate;
import org.apache.ignite.spi.IgniteSpi;
import org.apache.ignite.spi.metric.jmx.JmxMetricExporterSpi;

/**
 * Exporter of metric information to the external recepient.
 * Expected, that each implementation would support some specific protocol.
 *
 * Implementation of this Spi should work by pull paradigm.
 * So after start SPI should respond to some incoming request.
 * HTTP servlet or JMX bean are good examples of expected implementations.
 *
 * @see ReadOnlyMetricManager
 * @see ReadOnlyMetricRegistry
 * @see Metric
 * @see BooleanMetric
 * @see DoubleMetric
 * @see IntMetric
 * @see LongMetric
 * @see ObjectMetric
 * @see JmxMetricExporterSpi
 */
public interface MetricExporterSpi extends IgniteSpi {
    /**
     * Sets metrics registry that SPI should export.
     * This method called before {@link #spiStart(String)}.
     *
     * @param registry Metric registry.
     */
    public void setMetricRegistry(ReadOnlyMetricManager registry);

    /**
     * Sets export filter.
     * Metric registry that not satisfy {@code filter} shouldn't be exported.
     *
     * @param filter Filter.
     */
    public void setExportFilter(Predicate<ReadOnlyMetricRegistry> filter);
}
