package com.softwareag.naturalone.natural.pal.external;

import java.util.Hashtable;

public class PalTools {
    private static Hashtable<Integer, String> formatZuordnung;

    private PalTools() {
    }

    public static synchronized byte[] getIPBytes(String address) {
        String[] parts = address.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        byte[] result = new byte[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.valueOf(parts[i]).byteValue();
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceFormat() {
        if (formatZuordnung == null) {
            formatZuordnung = new Hashtable<>();
            formatZuordnung.put(1, "N");
            formatZuordnung.put(2, "P");
            formatZuordnung.put(3, "I");
            formatZuordnung.put(4, "F");
            formatZuordnung.put(5, "B");
            formatZuordnung.put(6, "D");
            formatZuordnung.put(7, "T");
            formatZuordnung.put(8, "L");
            formatZuordnung.put(9, "C");
            formatZuordnung.put(10, "A");
            formatZuordnung.put(11, "H");
            formatZuordnung.put(17, "U");
        }
        return (Hashtable) formatZuordnung.clone();
    }
}
