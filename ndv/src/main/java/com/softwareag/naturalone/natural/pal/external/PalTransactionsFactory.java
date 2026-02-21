package com.softwareag.naturalone.natural.pal.external;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Factory die eine {@link IPalTransactions}-Instanz erzeugt.
 *
 * Delegiert per Reflection an die echte PalTransactionsFactory aus dem
 * ndvserveraccess-JAR (das per {@link NdvProxyBridge} dynamisch geladen wird).
 * Der Proxy ist nötig, weil unser IPalTransactions (pal.external) und das
 * echte IPalTransactions (paltransactions.external) verschiedene Interfaces sind.
 */
public class PalTransactionsFactory {

    private PalTransactionsFactory() {}

    public static IPalTransactions newInstance() {
        NdvProxyBridge.ensureInitialized();
        try {
            Class<?> factoryClass = Class.forName(
                    "com.softwareag.naturalone.natural.paltransactions.external.PalTransactionsFactory");
            Method m = factoryClass.getMethod("newInstance");
            Object real = m.invoke(null);

            if (real == null) {
                throw new IllegalStateException(
                        "Echte PalTransactionsFactory.newInstance() hat null zurückgegeben");
            }

            // Proxy nötig: echtes Objekt implementiert paltransactions.external.IPalTransactions,
            // NdvClient erwartet aber pal.external.IPalTransactions.
            return (IPalTransactions) Proxy.newProxyInstance(
                    PalTransactionsFactory.class.getClassLoader(),
                    new Class<?>[]{ IPalTransactions.class },
                    new DirectDelegateHandler(real)
            );
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("PalTransactionsFactory.newInstance() fehlgeschlagen: "
                    + (cause != null ? cause.getClass().getName() + ": " + cause.getMessage() : "null"),
                    cause != null ? cause : e);
        } catch (Exception e) {
            throw new RuntimeException("PalTransactionsFactory.newInstance() fehlgeschlagen: "
                    + e.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Delegiert Methodenaufrufe direkt an das echte Objekt.
     * Da alle Klassen im selben ClassLoader liegen (addURL), gibt es keine
     * ClassCast-Probleme bei Parametern und Rückgabewerten.
     */
    private static final class DirectDelegateHandler implements InvocationHandler {
        private final Object real;

        DirectDelegateHandler(Object real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Method realMethod = findMethod(real.getClass(), method.getName(), method.getParameterTypes());
                return realMethod.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw cause != null ? cause : e;
            }
        }

        private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) throws NoSuchMethodException {
            try {
                return clazz.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}

            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)
                        && m.getParameterCount() == (paramTypes == null ? 0 : paramTypes.length)) {
                    return m;
                }
            }
            throw new NoSuchMethodException(clazz.getName() + "." + name);
        }
    }
}
