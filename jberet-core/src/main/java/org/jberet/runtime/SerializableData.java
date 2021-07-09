/*
 * Copyright (c) 2015 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.jberet.runtime;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.ServiceLoader;
import jakarta.batch.operations.BatchRuntimeException;

import org.jberet._private.BatchLogger;
import org.jberet._private.BatchMessages;
import org.jberet.spi.SerializableDataProvider;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Holds optionally serialized data. If the type of the serialized data is of specific types the data is not actually
 * serialized. If the type is not part of the JDK then the data is serialized.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SerializableData implements Serializable {
    private static final PrivilegedAction<SerializableDataProvider> loaderAction = new PrivilegedAction<SerializableDataProvider>() {
        @Override
        public SerializableDataProvider run() {
            final ServiceLoader<SerializableDataProvider> serviceLoader = ServiceLoader.load(SerializableDataProvider.class);
            if (serviceLoader.iterator().hasNext()) {
                return serviceLoader.iterator().next();
            }
            return new SerializableDataProvider.DefaultSerializableDataProvider();
        }
    };

    private static final SerializableDataProvider provider =
        WildFlySecurityManager.isChecking() ? AccessController.doPrivileged(loaderAction) : loaderAction.run();

    private final byte[] serialized;
    private final Serializable raw;
    private final Class<?> klass;

    private SerializableData(final byte[] serialized, final Serializable raw, final Class<?> klass) {
        this.serialized = serialized;
        this.raw = raw;
        this.klass = klass;
    }

    /**
     * Creates a new instance.
     *
     * @param data the data to be serialized
     *
     * @return a new instance
     *
     * @throws BatchRuntimeException if a failure to serialize the data occurs
     */
    public static SerializableData of(final Serializable data) {
        if (data instanceof SerializableData) {
            return (SerializableData) data;
        }
        if (data instanceof byte[]) {
            return new SerializableData((byte[]) data, null, byte[].class);
        }
        if (data == null) {
            return new SerializableData(null, null, null);
        }

        Class<?> c = data.getClass();
        if (c.isArray()) {
            c = c.getComponentType();
        }

        if (requiresSerialization(c)) {
            try {
                return new SerializableData(provider.objectToBytes(data), null, c);
            } catch (IOException e) {
                if (data instanceof Throwable) {
                    //if failed to serialize step exception data, try to preserve original
                    //step exception message and stack trace
                    final Throwable exceptionData = (Throwable) data;
                    BatchLogger.LOGGER.failedToSerializeException(e, exceptionData);
                    final BatchRuntimeException replacementException = new BatchRuntimeException(exceptionData.getMessage());
                    replacementException.setStackTrace(exceptionData.getStackTrace());
                    try {
                        return new SerializableData(provider.objectToBytes(replacementException), null, c);
                    } catch (final IOException e1) {
                        throw BatchMessages.MESSAGES.failedToSerialize(e1, replacementException);
                    }
                }
                throw BatchMessages.MESSAGES.failedToSerialize(e, data);
            }
        }
        return new SerializableData(null, data, c);
    }

    /**
     * If the data was previously serialized it's deserialized using the TCCL. If the TCCL is not set the class loader
     * for this class will be used.
     *
     * @return the serialized data or {@code null} if the data was {@code null} when created
     *
     * @throws BatchRuntimeException if there was a failure to desrialize the value
     */
    public Serializable deserialize() throws BatchRuntimeException {
        if (raw != null) {
            return raw;
        }
        if (serialized != null) {
            // In an EE container the TCCL should be set to the class loader required for the deployment, if it's not
            // set we'll default to the class loader for this class to deserialize the data
            ClassLoader cl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
            if (cl == null) {
                cl = WildFlySecurityManager.getClassLoaderPrivileged(SerializableData.class);
            }
            try {
                return provider.bytesToObject(serialized, klass, cl);
            } catch (IOException | ClassNotFoundException e) {
                throw BatchMessages.MESSAGES.failedToDeserialize(e, Arrays.toString(serialized));
            }
        }
        return null;
    }

    byte[] getSerialized() throws BatchRuntimeException {
        if (serialized != null) {
            return serialized;
        }
        try {
            return provider.objectToBytes(raw);
        } catch (final IOException e) {
            throw BatchMessages.MESSAGES.failedToSerialize(e, raw);
        }
    }

    @Override
    public int hashCode() {
        int hash = 17;
        if (raw != null) {
            hash = 31 * hash + raw.hashCode();
        }
        if (serialized != null) {
            hash = 31 * hash + Arrays.hashCode(serialized);
        }
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SerializableData)) {
            return false;
        }
        final SerializableData other = (SerializableData) obj;
        return (raw == null ? other.raw == null : raw.equals(other.raw)) && Arrays.equals(serialized, other.serialized);
    }

    private static boolean requiresSerialization(final Class<?> c) {
        return !c.isPrimitive() &&
                c != String.class &&
                c != Byte.class &&
                c != Double.class &&
                c != Float.class &&
                c != Integer.class &&
                c != Long.class &&
                c != Short.class &&
                c != BigDecimal.class &&
                c != BigInteger.class;
    }
}
