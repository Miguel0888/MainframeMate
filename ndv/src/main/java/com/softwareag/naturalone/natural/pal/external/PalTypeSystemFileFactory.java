package com.softwareag.naturalone.natural.pal.external;

import java.lang.reflect.Method;

/**
 * Stub-Factory für IPalTypeSystemFile.
 *
 * Nach addURL-Injection liegen die echten Klassen im selben ClassLoader.
 * Die Factory delegiert direkt per Reflection an die echte Factory.
 */
public class PalTypeSystemFileFactory {

    private PalTypeSystemFileFactory() {}

    /** Erzeugt eine (0,0,0)-Instanz (Server-Defaults). */
    public static IPalTypeSystemFile newInstance() {
        return newInstance(0, 0, 0);
    }

    /** Erzeugt eine Instanz mit expliziten DBID/FNR/Kind-Werten. */
    public static IPalTypeSystemFile newInstance(int dbid, int fnr, int kind) {
        NdvProxyBridge.ensureInitialized();
        try {
            Class<?> factoryClass = Class.forName(
                    "com.softwareag.naturalone.natural.pal.external.PalTypeSystemFileFactory");

            Object realSysFile;
            if (dbid == 0 && fnr == 0 && kind == 0) {
                Method m = factoryClass.getMethod("newInstance");
                realSysFile = m.invoke(null);
            } else {
                Method m = factoryClass.getMethod("newInstance", int.class, int.class, int.class);
                realSysFile = m.invoke(null, dbid, fnr, kind);
            }

            // Im selben ClassLoader → direkter Cast möglich
            return (IPalTypeSystemFile) realSysFile;
        } catch (Exception e) {
            throw new RuntimeException("PalTypeSystemFileFactory.newInstance() fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}
