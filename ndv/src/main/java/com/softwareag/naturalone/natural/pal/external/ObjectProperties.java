package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.paltransactions.external.IFileProperties;

import java.lang.reflect.Method;

/**
 * Stub für ObjectProperties und seinen Builder.
 *
 * Nach addURL-Injection liegt die echte ObjectProperties im selben ClassLoader.
 * Der Builder delegiert per Reflection an den echten Builder und gibt das echte
 * IFileProperties-Objekt zurück (direkter Cast, kein Proxy nötig).
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
            NdvProxyBridge.ensureInitialized();
            try {
                Class<?> builderClass = Class.forName(
                        "com.softwareag.naturalone.natural.paltransactions.external.ObjectProperties$Builder");
                Object realBuilder = builderClass.getConstructor(String.class, int.class)
                        .newInstance(name, type);
                Method buildMethod = builderClass.getMethod("build");
                Object realProps = buildMethod.invoke(realBuilder);

                // Selber ClassLoader → direkter Cast
                return (IFileProperties) realProps;
            } catch (Exception e) {
                throw new RuntimeException("ObjectProperties.Builder.build() fehlgeschlagen: " + e.getMessage(), e);
            }
        }
    }
}
