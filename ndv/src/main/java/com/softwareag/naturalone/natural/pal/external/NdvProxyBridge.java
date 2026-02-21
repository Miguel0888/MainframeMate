package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.external.PalConnectResultException;
import com.softwareag.naturalone.natural.pal.external.PalResultException;
import com.softwareag.naturalone.natural.pal.external.PalDate;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentraler Bridge zwischen den Stub-Interfaces im ndv-Modul und der
 * echten ndvserveraccess-Implementierung.
 *
 * Hält einen eigenen URLClassLoader für das JAR — kein addURL-Hack,
 * keine Mutation des App-Classloaders.
 *
 * Muss vor dem ersten Aufruf von PalTransactionsFactory.newInstance()
 * initialisiert werden via {@link #init(File...)}.
 */
public final class NdvProxyBridge {

    private static volatile URLClassLoader classLoader;

    private NdvProxyBridge() {}

    // ── Initialisierung ──────────────────────────────────────────────────────

    /**
     * Initialisiert den ClassLoader mit den gegebenen JAR-Dateien.
     * Mehrfachaufruf ist sicher (idempotent solange dieselben JARs übergeben werden).
     */
    public static synchronized void init(File... jars) throws Exception {
        if (classLoader != null) return;
        List<URL> urls = new ArrayList<>();
        for (File jar : jars) {
            if (!jar.isFile()) {
                throw new IllegalArgumentException("JAR nicht gefunden: " + jar.getAbsolutePath());
            }
            urls.add(jar.toURI().toURL());
        }
        // Parent = null → isolierter ClassLoader, kein Delegation-Konflikt
        classLoader = new URLClassLoader(urls.toArray(new URL[0]), null);
    }

    /** Setzt den ClassLoader zurück (für Tests oder Reload). */
    public static synchronized void reset() {
        classLoader = null;
    }

    /** Gibt den initialisierten ClassLoader zurück. */
    public static ClassLoader getClassLoader() {
        if (classLoader == null) {
            throw new IllegalStateException(
                    "NdvProxyBridge nicht initialisiert. Bitte zuerst NdvProxyBridge.init(jarFiles) aufrufen.");
        }
        return classLoader;
    }

    // ── Methoden-Suche ───────────────────────────────────────────────────────

    /**
     * Sucht eine Methode auf der echten Klasse, wobei Stub-Parametertypen auf
     * ihre echten Gegenstücke gemappt werden (Name-basierter Match).
     */
    public static Method findMethod(Class<?> realClass, String name, Class<?>[] stubParamTypes) {
        for (Method m : realClass.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] realParams = m.getParameterTypes();
            if (realParams.length != (stubParamTypes == null ? 0 : stubParamTypes.length)) continue;
            if (parametersMatch(stubParamTypes, realParams)) return m;
        }
        // Fallback: nur Name und Anzahl
        for (Method m : realClass.getMethods()) {
            if (m.getName().equals(name)
                    && m.getParameterCount() == (stubParamTypes == null ? 0 : stubParamTypes.length)) {
                return m;
            }
        }
        throw new RuntimeException("Methode nicht gefunden: " + realClass.getName() + "." + name);
    }

    private static boolean parametersMatch(Class<?>[] stubTypes, Class<?>[] realTypes) {
        if (stubTypes == null) return realTypes.length == 0;
        for (int i = 0; i < stubTypes.length; i++) {
            if (stubTypes[i].equals(realTypes[i])) continue;
            // Stub-Interface-Name == Real-Interface-Name → OK
            if (stubTypes[i].getName().equals(realTypes[i].getName())) continue;
            // Primitive oder andere Typen → müssen exakt stimmen
            return false;
        }
        return true;
    }

    // ── Argument-Mapping ─────────────────────────────────────────────────────

    /**
     * Mappt Stub-Argumente auf die von der echten Methode erwarteten Typen.
     * Proxy-Objekte werden "entpackt", primitiv-Typen und Strings bleiben unverändert.
     */
    public static Object[] mapArgs(Object[] args, Class<?>[] realParamTypes, ClassLoader cl) {
        if (args == null) return null;
        Object[] mapped = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            mapped[i] = mapArg(args[i], realParamTypes[i], cl);
        }
        return mapped;
    }

    private static Object mapArg(Object arg, Class<?> realType, ClassLoader cl) {
        if (arg == null) return null;

        // Bereits der richtige Typ (z.B. String, Integer, Map, Set, Enum)
        if (realType.isInstance(arg)) return arg;

        // Stub-Proxy → echtes Objekt dahinter holen
        if (Proxy.isProxyClass(arg.getClass())) {
            // InvocationHandler enthält ggf. das echte Objekt
            java.lang.reflect.InvocationHandler h = Proxy.getInvocationHandler(arg);
            if (h instanceof RealObjectHolder) {
                return ((RealObjectHolder) h).getRealObject();
            }
        }

        // Enum-Mapping: Stub-Enum → echtes Enum per Namen
        if (arg instanceof Enum && realType.isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object realEnum = Enum.valueOf((Class<Enum>) realType, ((Enum<?>) arg).name());
                return realEnum;
            } catch (IllegalArgumentException ignored) {}
        }

        // Enum-Array (z.B. Set<EDownLoadOption>)
        // Sets/Maps werden direkt durchgereicht — die echten Enum-Werte müssen schon drin sein
        // (EnumSet.of(EDownLoadOption.NONE) → String-Name "NONE" → echter Enum)
        if (arg instanceof java.util.Set) {
            return mapEnumSet((java.util.Set<?>) arg, realType, cl);
        }

        // Class-Objekt (createTransactionContext(Class))
        if (arg instanceof Class) {
            Class<?> stubClass = (Class<?>) arg;
            try {
                return cl.loadClass(stubClass.getName());
            } catch (ClassNotFoundException e) {
                return arg; // fallback
            }
        }

        return arg;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object mapEnumSet(java.util.Set<?> stubSet, Class<?> realType, ClassLoader cl) {
        // realType ist java.util.Set — wir müssen die Enum-Werte mappen
        try {
            // Lade die echte Enum-Klasse per Namen des ersten Elements
            if (stubSet.isEmpty()) {
                // EnumSet.noneOf(realEnumClass)
                return java.util.Collections.emptySet();
            }
            Object first = stubSet.iterator().next();
            if (first instanceof Enum) {
                String enumClassName = ((Enum<?>) first).getDeclaringClass().getName();
                Class realEnumClass = cl.loadClass(enumClassName);
                java.util.Set realSet = java.util.EnumSet.noneOf(realEnumClass);
                for (Object e : stubSet) {
                    realSet.add(Enum.valueOf(realEnumClass, ((Enum<?>) e).name()));
                }
                return realSet;
            }
        } catch (Exception ignored) {}
        return stubSet;
    }

    // ── Ergebnis-Mapping ─────────────────────────────────────────────────────

    /**
     * Mappt ein Ergebnis der echten Methode zurück auf den Stub-Typ.
     */
    public static Object mapResult(Object result, Class<?> stubReturnType, ClassLoader cl) {
        if (result == null) return null;
        if (stubReturnType == void.class || stubReturnType == Void.class) return null;

        // Primitiv / String / bekannte Typen direkt zurück
        if (stubReturnType.isPrimitive() || stubReturnType == Boolean.class
                || stubReturnType == Integer.class || stubReturnType == String.class) {
            return result;
        }

        // Array von Stub-Interfaces → Array von Proxys
        if (stubReturnType.isArray()) {
            Class<?> componentType = stubReturnType.getComponentType();
            if (componentType.isInterface()) {
                Object[] realArray = (Object[]) result;
                Object[] stubArray = (Object[]) java.lang.reflect.Array.newInstance(componentType, realArray.length);
                for (int i = 0; i < realArray.length; i++) {
                    stubArray[i] = wrapAsProxy(realArray[i], componentType, cl);
                }
                return stubArray;
            }
            return result;
        }

        // Stub-Interface → Proxy
        if (stubReturnType.isInterface()) {
            return wrapAsProxy(result, stubReturnType, cl);
        }

        // PalDate (spezieller Fall: Wrapper-Klasse)
        if (stubReturnType == PalDate.class) {
            return new PalDate(result);
        }

        return result;
    }

    private static Object wrapAsProxy(Object real, Class<?> stubInterface, ClassLoader cl) {
        if (real == null) return null;
        if (stubInterface.isInstance(real)) return real;
        return Proxy.newProxyInstance(
                NdvProxyBridge.class.getClassLoader(),
                new Class<?>[]{ stubInterface },
                new ReflectiveHandler(real, stubInterface, cl)
        );
    }

    // ── InvocationHandler ────────────────────────────────────────────────────

    /** Handler der alle Methodenaufrufe per Reflection an das echte Objekt delegiert. */
    public static final class ReflectiveHandler implements InvocationHandler, RealObjectHolder {

        private final Object real;
        private final Class<?> stubInterface;
        private final ClassLoader cl;

        public ReflectiveHandler(Object real, Class<?> stubInterface, ClassLoader cl) {
            this.real = real;
            this.stubInterface = stubInterface;
            this.cl = cl;
        }

        @Override
        public Object getRealObject() { return real; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method realMethod = findMethod(real.getClass(), method.getName(), method.getParameterTypes());
            Object[] mappedArgs = mapArgs(args, realMethod.getParameterTypes(), cl);
            Object result;
            try {
                result = realMethod.invoke(real, mappedArgs);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw mapException(e.getCause());
            }
            return mapResult(result, method.getReturnType(), cl);
        }
    }

    /** Marker-Interface damit Proxys ihr echtes Objekt preisgeben können. */
    public interface RealObjectHolder {
        Object getRealObject();
    }

    // ── Exception-Mapping ────────────────────────────────────────────────────

    /**
     * Mappt echte PAL-Exceptions auf die Stub-Exceptions.
     * So können catch-Blöcke in NdvClient wie gewohnt funktionieren.
     */
    public static Throwable mapException(Throwable real) {
        if (real == null) return new RuntimeException("unknown error");
        String className = real.getClass().getName();
        String msg = real.getMessage();
        if (className.contains("PalConnectResultException")) {
            return new PalConnectResultException(msg, real);
        }
        if (className.contains("PalResultException")) {
            return new PalResultException(msg, real);
        }
        return real;
    }
}


