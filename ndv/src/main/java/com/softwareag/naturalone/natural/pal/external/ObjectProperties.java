package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.paltransactions.external.IFileProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stub für ObjectProperties und seinen Builder.
 */
public class ObjectProperties {

    private ObjectProperties() {}

    public static final class Builder {

        private final String name;
        private final int type;

        public Builder(String name, int type) {
            this.name = name;
            this.type = type;
        }

        public IFileProperties build() {
            try {
                ClassLoader cl = NdvProxyBridge.getClassLoader();
                // Die ECHTE ObjectProperties liegt in paltransactions.external, nicht pal.external!
                Class<?> opClass = cl.loadClass(
                        "com.softwareag.naturalone.natural.paltransactions.external.ObjectProperties");
                Class<?> builderClass = cl.loadClass(
                        "com.softwareag.naturalone.natural.paltransactions.external.ObjectProperties$Builder");
                Object realBuilder = builderClass.getConstructor(String.class, int.class)
                        .newInstance(name, type);
                Method buildMethod = builderClass.getMethod("build");
                Object realProps = buildMethod.invoke(realBuilder);

                // Proxy zurück auf IFileProperties
                return (IFileProperties) Proxy.newProxyInstance(
                        Builder.class.getClassLoader(),
                        new Class<?>[]{ IFileProperties.class },
                        (proxy, method, args) -> {
                            Method m = realProps.getClass().getMethod(method.getName(),
                                    method.getParameterTypes());
                            return m.invoke(realProps, args);
                        }
                );
            } catch (Exception e) {
                throw new RuntimeException("ObjectProperties.Builder.build() fehlgeschlagen: " + e.getMessage(), e);
            }
        }
    }
}

