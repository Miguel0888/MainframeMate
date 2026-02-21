package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import java.util.regex.Pattern;
import java.util.List;

public class RenumberSource {

    private RenumberSource() {
    }

    private static Pattern getLineReferencePattern() {
        return null;
    }

    public static StringBuffer[] addLineNumbers(String[] var0, int var1, String var2, boolean var3, boolean var4, boolean var5) {
        return null;
    }

    public static String[] removeLineNumbers(List var0, boolean var1, boolean var2, int var3, int var4, IInsertLabels var5) {
        return null;
    }

    public static String[] updateLineReferences(String[] var0, int var1, boolean var2) {
        return null;
    }

    public static boolean isLineNumberReference(int var0, String var1, boolean var2, boolean var3, boolean var4) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static boolean isLineReference(int var0, String var1) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private static String getExistingLabel(String var0) {
        return null;
    }

    private static boolean searchStringInSource(List var0, String var1) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}