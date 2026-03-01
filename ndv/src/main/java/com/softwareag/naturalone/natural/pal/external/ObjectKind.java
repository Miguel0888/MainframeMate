package com.softwareag.naturalone.natural.pal.external;

public final class ObjectKind {
    public static final int NONE = 0;
    public static final int SOURCE = 1;
    public static final int GP = 2;
    public static final int RESOURCE = 16;
    public static final int ERRORMSG = 64;
    public static final int SOURCE_OR_GP = 3;
    public static final int ANY = 83;

    private ObjectKind() {
    }

    public static String get(int kind) {
        switch (kind) {
            case SOURCE:
                return "SOURCE";
            case GP:
                return "GP";
            case SOURCE_OR_GP:
                return "SOURCE/GP";
            case RESOURCE:
                return "RESOURCE";
            case ERRORMSG:
                return "ERRORMSG";
            default:
                return "";
        }
    }
}
