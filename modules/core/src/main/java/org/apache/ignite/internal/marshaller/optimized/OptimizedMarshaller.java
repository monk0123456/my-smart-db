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

package org.apache.ignite.internal.marshaller.optimized;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.binary.GridBinaryMarshaller;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.marshaller.AbstractNodeNameAwareMarshaller;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_OPTIMIZED_MARSHALLER_USE_DEFAULT_SUID;

/**
 * Optimized implementation of {@link org.apache.ignite.marshaller.Marshaller}.
 * Unlike {@link org.apache.ignite.marshaller.jdk.JdkMarshaller},
 * which is based on standard {@link ObjectOutputStream}, this marshaller does not
 * enforce that all serialized objects implement {@link Serializable} interface. It is also
 * about 20 times faster as it removes lots of serialization overhead that exists in
 * default JDK implementation.
 * <p>
 * {@code OptimizedMarshaller} is tested only on Java HotSpot VM on other VMs
 * it could yield unexpected results. It is the default marshaller on Java HotSpot VMs
 * and will be used if no other marshaller was explicitly configured.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This marshaller has no mandatory configuration parameters.
 * <h2 class="header">Java Example</h2>
 * <pre name="code" class="java">
 * OptimizedMarshaller marshaller = new OptimizedMarshaller();
 *
 * // Enforce Serializable interface.
 * marshaller.setRequireSerializable(true);
 *
 * IgniteConfiguration cfg = new IgniteConfiguration();
 *
 * // Override marshaller.
 * cfg.setMarshaller(marshaller);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridOptimizedMarshaller can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.apache.ignite.configuration.IgniteConfiguration" singleton="true"&gt;
 *     ...
 *     &lt;property name="marshaller"&gt;
 *         &lt;bean class="OptimizedMarshaller"&gt;
 *             &lt;property name="requireSerializable"&gt;true&lt;/property&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="https://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 */
public class OptimizedMarshaller extends AbstractNodeNameAwareMarshaller {
    /** Use default {@code serialVersionUID} for {@link Serializable} classes. */
    public static final boolean USE_DFLT_SUID =
        IgniteSystemProperties.getBoolean(IGNITE_OPTIMIZED_MARSHALLER_USE_DEFAULT_SUID, false);

    /** Default class loader. */
    private final ClassLoader dfltClsLdr = getClass().getClassLoader();

    /** Whether or not to require an object to be serializable in order to be marshalled. */
    private boolean requireSer = true;

    /** ID mapper. */
    private OptimizedMarshallerIdMapper mapper;

    /** Class descriptors by class. */
    private final ConcurrentMap<Class, OptimizedClassDescriptor> clsMap = new ConcurrentHashMap<>();

    /** */
    private OptimizedObjectStreamRegistry registry = new OptimizedObjectSharedStreamRegistry();

    /** Non cached registry. */
    private OptimizedObjectSharedStreamRegistry nonCachedRegistry = new OptimizedObjectSharedStreamRegistry();

    /**
     * Creates new marshaller will all defaults.
     *
     * @throws IgniteException If this marshaller is not supported on the current JVM.
     */
    public OptimizedMarshaller() {
        if (!available())
            throw new IgniteException("Using OptimizedMarshaller on unsupported JVM version (some of " +
                "JVM-private APIs required for the marshaller to work are missing).");
    }

    /**
     * Creates new marshaller providing whether it should
     * require {@link Serializable} interface or not.
     *
     * @param requireSer Whether to require {@link Serializable}.
     */
    public OptimizedMarshaller(boolean requireSer) {
        this.requireSer = requireSer;
    }

    /**
     * Sets whether marshaller should require {@link Serializable} interface or not.
     *
     * @param requireSer Whether to require {@link Serializable}.
     * @return {@code this} for chaining.
     */
    public OptimizedMarshaller setRequireSerializable(boolean requireSer) {
        this.requireSer = requireSer;

        return this;
    }

    /**
     * Sets ID mapper.
     *
     * @param mapper ID mapper.
     * @return {@code this} for chaining.
     */
    public OptimizedMarshaller setIdMapper(OptimizedMarshallerIdMapper mapper) {
        this.mapper = mapper;

        return this;
    }

    /**
     * Specifies size of cached object streams used by marshaller. Object streams are cached for
     * performance reason to avoid costly recreation for every serialization routine. If {@code 0} (default),
     * pool is not used and each thread has its own cached object stream which it keeps reusing.
     * <p>
     * Since each stream has an internal buffer, creating a stream for each thread can lead to
     * high memory consumption if many large messages are marshalled or unmarshalled concurrently.
     * Consider using pool in this case. This will limit number of streams that can be created and,
     * therefore, decrease memory consumption.
     * <p>
     * NOTE: Using streams pool can decrease performance since streams will be shared between
     * different threads which will lead to more frequent context switching.
     *
     * @param poolSize Streams pool size. If {@code 0}, pool is not used.
     * @return {@code this} for chaining.
     */
    public OptimizedMarshaller setPoolSize(int poolSize) {
        registry = poolSize > 0 ?
            new OptimizedObjectPooledStreamRegistry(poolSize) :
            new OptimizedObjectSharedStreamRegistry();

        return this;
    }

    /** {@inheritDoc} */
    @Override protected void marshal0(@Nullable Object obj, OutputStream out) throws IgniteCheckedException {
        assert out != null;

        OptimizedObjectOutputStream objOut = null;

        try {
            objOut = registry.out();

            objOut.context(clsMap, ctx, mapper, requireSer);

            objOut.out().outputStream(out);

            objOut.writeObject(obj);
        }
        catch (IOException e) {
            throw new IgniteCheckedException("Failed to serialize object: " + obj, e);
        }
        finally {
            registry.closeOut(objOut);
        }
    }

    /** {@inheritDoc} */
    @Override protected byte[] marshal0(@Nullable Object obj) throws IgniteCheckedException {
        OptimizedObjectOutputStream objOut = null;

        try {
            objOut = registry.out();

            objOut.context(clsMap, ctx, mapper, requireSer);

            objOut.writeObject(obj);

            return objOut.out().array();
        }
        catch (Exception e) {
            throw new IgniteCheckedException("Failed to serialize object: " + obj, e);
        }
        finally {
            registry.closeOut(objOut);
        }
    }

    /** {@inheritDoc} */
    @Override protected <T> T unmarshal0(InputStream in, @Nullable ClassLoader clsLdr) throws IgniteCheckedException {
        return unmarshal0(in, clsLdr, GridBinaryMarshaller.USE_CACHE.get());
    }

    /**
     * Unmarshals object from the input stream using given class loader.
     * This method should not close given input stream.
     *
     * @param <T> Type of unmarshalled object.
     * @param in Input stream.
     * @param clsLdr Class loader to use.
     * @param useCache True if class loader cache will be used, false otherwise.
     * @return Unmarshalled object.
     * @throws IgniteCheckedException If unmarshalling failed.
     */
    protected <T> T unmarshal0(InputStream in, @Nullable ClassLoader clsLdr, boolean useCache) throws IgniteCheckedException {
        assert in != null;

        OptimizedObjectInputStream objIn = null;

        try {
            objIn = !useCache ? nonCachedRegistry.in() : registry.in();

            objIn.context(clsMap, ctx, mapper, clsLdr != null ? clsLdr : dfltClsLdr, useCache);

            objIn.in().inputStream(in);

            return (T)objIn.readObject();
        }
        catch (ClassNotFoundException e) {
            throw new OptimizedMarshallerInaccessibleClassException(
                "Failed to find class with given class loader for unmarshalling " +
                "(make sure same versions of all classes are available on all nodes or enable peer-class-loading) " +
                "[clsLdr=" + clsLdr + ", cls=" + e.getMessage() + "]", e);
        }
        catch (Exception e) {
            throw new IgniteCheckedException("Failed to deserialize object with given class loader: " +
                "[clsLdr=" + clsLdr + ", err=" + e.getMessage() + "]", e);
        }
        finally {
            if (!useCache)
                nonCachedRegistry.closeNotCachedIn(objIn);
            else
                registry.closeIn(objIn);
        }
    }

    /** {@inheritDoc} */
    @Override protected <T> T unmarshal0(byte[] arr, @Nullable ClassLoader clsLdr) throws IgniteCheckedException {
        assert arr != null;

        OptimizedObjectInputStream objIn = null;

        try {
            objIn = registry.in();

            objIn.context(clsMap, ctx, mapper,
                clsLdr != null ? clsLdr : dfltClsLdr, GridBinaryMarshaller.USE_CACHE.get());

            objIn.in().bytes(arr, arr.length);

            return (T)objIn.readObject();
        }
        catch (ClassNotFoundException e) {
            throw new IgniteCheckedException("Failed to find class with given class loader for unmarshalling " +
                "(make sure same version of all classes are available on all nodes or enable peer-class-loading)" +
                " [clsLdr=" + clsLdr + ", cls=" + e.getMessage() + "]", e);
        }
        catch (Exception e) {
            throw new IgniteCheckedException("Failed to deserialize object with given class loader: " + clsLdr, e);
        }
        finally {
            registry.closeIn(objIn);
        }
    }

    /**
     * Checks whether {@code GridOptimizedMarshaller} is able to work on the current JVM.
     * <p>
     * As long as {@code GridOptimizedMarshaller} uses JVM-private API, which is not guaranteed
     * to be available on all JVM, this method should be called to ensure marshaller could work properly.
     * <p>
     * Result of this method is automatically checked in constructor.
     *
     * @return {@code true} if {@code GridOptimizedMarshaller} can work on the current JVM or
     *  {@code false} if it can't.
     */
    @SuppressWarnings({"TypeParameterExtendsFinalClass", "ErrorNotRethrown"})
    public static boolean available() {
        try {
            Class<? extends Unsafe> unsafeCls = Unsafe.class;

            unsafeCls.getMethod("allocateInstance", Class.class);
            unsafeCls.getMethod("copyMemory", Object.class, long.class, Object.class, long.class, long.class);

            return true;
        }
        catch (Exception ignored) {
            return false;
        }
        catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    /**
     * Undeployment callback invoked when class loader is being undeployed.
     *
     * @param ldr Class loader being undeployed.
     */
    @Override public void onUndeploy(ClassLoader ldr) {
        for (Class<?> cls : clsMap.keySet()) {
            if (ldr.equals(cls.getClassLoader()))
                clsMap.remove(cls);
        }

        U.clearClassCache(ldr);
    }

    /**
     * Clears the optimized class descriptors cache. This is essential for the clients
     * on disconnect in order to make them register their user types again (server nodes may
     * lose previously registered types).
     */
    public void clearClassDescriptorsCache() {
        clsMap.clear();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(OptimizedMarshaller.class, this);
    }
}
