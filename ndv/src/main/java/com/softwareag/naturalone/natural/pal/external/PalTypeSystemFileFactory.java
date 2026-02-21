package com.softwareag.naturalone.natural.pal.external;


import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stub-Factory für IPalTypeSystemFile.
 */
public class PalTypeSystemFileFactory {

    private PalTypeSystemFileFactory() {}

    /** Erzeugt eine (0,0,0)-Instanz (Server-Defaults). */
    public static IPalTypeSystemFile newInstance() {
        return newInstance(0, 0, 0);
    }

    /** Erzeugt eine Instanz mit expliziten DBID/FNR/Kind-Werten. */
    public static IPalTypeSystemFile newInstance(int dbid, int fnr, int kind) {
        try {
            ClassLoader cl = NdvProxyBridge.getClassLoader();
            Class<?> factoryClass = cl.loadClass(
                    "com.softwareag.naturalone.natural.pal.external.PalTypeSystemFileFactory");

            Object realSysFile;
            if (dbid == 0 && fnr == 0 && kind == 0) {
                Method m = factoryClass.getMethod("newInstance");
                realSysFile = m.invoke(null);
            } else {
                Method m = factoryClass.getMethod("newInstance", int.class, int.class, int.class);
                realSysFile = m.invoke(null, dbid, fnr, kind);
            }

            return wrapSysFile(realSysFile, cl);
        } catch (Exception e) {
            throw new RuntimeException("PalTypeSystemFileFactory.newInstance() fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Wickelt ein echtes sysFile-Objekt in einen Stub-Proxy. */
    static IPalTypeSystemFile wrapSysFile(final Object real, ClassLoader cl) {
        if (real == null) return null;
        if (real instanceof IPalTypeSystemFile) return (IPalTypeSystemFile) real;
        return (IPalTypeSystemFile) Proxy.newProxyInstance(
                PalTypeSystemFileFactory.class.getClassLoader(),
                new Class<?>[]{ IPalTypeSystemFile.class, NdvProxyBridge.RealObjectHolder.class },
                (proxy, method, args) -> {
                    if ("getRealObject".equals(method.getName()) && method.getParameterCount() == 0) {
                        return real;
                    }
                    Method realMethod = real.getClass().getMethod(method.getName(),
                            method.getParameterTypes());
                    return realMethod.invoke(real, args);
                }
        );
    }
}

