package com.softwareag.naturalone.natural.pal.external;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stub-Factory für IPalTransactions.
 *
 * Erzeugt einen Java-Proxy der zur Laufzeit alle Methoden per Reflection
 * an die echte Implementierung aus dem ndvserveraccess-JAR delegiert.
 */
public class PalTransactionsFactory {

    private PalTransactionsFactory() {}

    /**
     * Erzeugt eine neue IPalTransactions-Instanz als dynamischen Proxy.
     * Das JAR muss vorher über {@link NdvProxyBridge#getClassLoader()} geladen worden sein.
     */
    public static IPalTransactions newInstance() {
        return (IPalTransactions) Proxy.newProxyInstance(
                PalTransactionsFactory.class.getClassLoader(),
                new Class<?>[]{ IPalTransactions.class },
                new PalTransactionsHandler()
        );
    }

    private static final class PalTransactionsHandler implements InvocationHandler {

        // Die echte Instanz — lazy initialisiert beim ersten Aufruf
        private volatile Object realInstance;

        private Object getRealInstance() throws Exception {
            if (realInstance == null) {
                synchronized (this) {
                    if (realInstance == null) {
                        ClassLoader cl = NdvProxyBridge.getClassLoader();
                        Thread currentThread = Thread.currentThread();
                        ClassLoader previousCL = currentThread.getContextClassLoader();
                        currentThread.setContextClassLoader(cl);
                        try {
                            Class<?> factoryClass = cl.loadClass(
                                    "com.softwareag.naturalone.natural.paltransactions.external.PalTransactionsFactory");
                            Method m = factoryClass.getMethod("newInstance");
                            realInstance = m.invoke(null);
                        } finally {
                            currentThread.setContextClassLoader(previousCL);
                        }
                    }
                }
            }
            return realInstance;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object real = getRealInstance();
            ClassLoader cl = NdvProxyBridge.getClassLoader();

            Method realMethod = NdvProxyBridge.findMethod(real.getClass(), method.getName(),
                    method.getParameterTypes());
            Object[] mappedArgs = NdvProxyBridge.mapArgs(args, realMethod.getParameterTypes(), cl);

            // Thread-Context-ClassLoader setzen, damit SPI-Lookups
            // (z.B. ICU4J CharsetProvider) die JARs finden.
            Thread currentThread = Thread.currentThread();
            ClassLoader previousCL = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(cl);

            Object result;
            try {
                result = realMethod.invoke(real, mappedArgs);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                System.err.println("[PalProxy] InvocationTargetException for " + method.getName()
                        + ": " + (cause != null ? cause.getClass().getName() + ": " + cause.getMessage() : "null"));
                throw NdvProxyBridge.mapException(cause);
            } finally {
                currentThread.setContextClassLoader(previousCL);
            }

            return NdvProxyBridge.mapResult(result, method.getReturnType(), cl);
        }
    }
}
