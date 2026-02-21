package com.softwareag.naturalone.natural.pal.external;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stub-Factory für IPalTransactions.
 *
 * Nach {@link NdvProxyBridge#ensureInitialized()} liegen die echten Klassen
 * im App-ClassLoader. Die Factory ruft die echte Factory per Reflection auf
 * und wrappet das Ergebnis in einen einfachen Proxy auf {@link IPalTransactions}.
 *
 * Da alle Klassen im selben ClassLoader sind, gibt es keine ClassCast-Probleme.
 */
public class PalTransactionsFactory {

    private PalTransactionsFactory() {}

    /**
     * Erzeugt eine neue IPalTransactions-Instanz.
     */
    public static IPalTransactions newInstance() {
        NdvProxyBridge.ensureInitialized();
        try {
            Class<?> factoryClass = Class.forName(
                    "com.softwareag.naturalone.natural.paltransactions.external.PalTransactionsFactory",
                    true,
                    NdvProxyBridge.getClassLoader());
            Method m = factoryClass.getMethod("newInstance");
            Object real = m.invoke(null);

            if (real == null) {
                throw new IllegalStateException(
                        "Echte PalTransactionsFactory.newInstance() hat null zurückgegeben");
            }

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
     * Einfacher Handler: sucht die Methode auf dem echten Objekt per Name + Parameter-Typen
     * und ruft sie direkt auf. Keine Typ-Konvertierung nötig.
     */
    private static final class DirectDelegateHandler implements InvocationHandler {
        private final Object real;

        DirectDelegateHandler(Object real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                // Methode auf der echten Klasse finden
                Method realMethod = findMethod(real.getClass(), method.getName(), method.getParameterTypes());
                return realMethod.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                // PAL-Exceptions direkt durchwerfen — sie sind jetzt im selben ClassLoader
                throw cause != null ? cause : e;
            }
        }

        private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) throws NoSuchMethodException {
            // Erst exakt suchen
            try {
                return clazz.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}

            // Fallback: Name + Anzahl (für Fälle wo Stub-Typ ≠ echter Typ, z.B. Object vs ITransactionContext)
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == (paramTypes == null ? 0 : paramTypes.length)) {
                    return m;
                }
            }
            throw new NoSuchMethodException(clazz.getName() + "." + name);
        }
    }
}
