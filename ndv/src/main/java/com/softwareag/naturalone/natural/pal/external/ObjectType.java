package com.softwareag.naturalone.natural.pal.external;

import java.util.Hashtable;

/**
 * Stub für ObjectType — enthält nur die Konstanten und Lookup-Tabellen
 * die NdvObjectInfo und NdvClient benötigen.
 *
 * Die echten int-Werte stammen aus dem ndvserveraccess-JAR und sind hier
 * als bekannte Konstanten hartcodiert (stabil über alle Versionen).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectType {

    public static final int GDA          = 1;
    public static final int LDA          = 2;
    public static final int PDA          = 4;
    public static final int DDM          = 8;
    public static final int PROGRAM      = 16;
    public static final int SUBPROGRAM   = 32;
    public static final int MAP          = 64;
    public static final int COPYCODE     = 128;
    public static final int SUBROUTINE   = 256;
    public static final int HELPROUTINE  = 512;
    public static final int CLASS        = 1024;
    public static final int DIALOG       = 2048;
    public static final int TEXT         = 4096;
    public static final int FUNCTION     = 524288;
    public static final int ADAPTER      = 2097152;
    public static final int RESOURCE     = 65536;

    private static final Hashtable<Integer, String> ID_NAMES;
    private static final Hashtable<Integer, String> ID_EXTENSIONS;

    static {
        ID_NAMES = new Hashtable<>();
        ID_NAMES.put(GDA,          "Global Data Area");
        ID_NAMES.put(LDA,          "Local Data Area");
        ID_NAMES.put(PDA,          "Parameter Data Area");
        ID_NAMES.put(DDM,          "DDM");
        ID_NAMES.put(PROGRAM,      "Program");
        ID_NAMES.put(SUBPROGRAM,   "Subprogram");
        ID_NAMES.put(MAP,          "Map");
        ID_NAMES.put(COPYCODE,     "Copycode");
        ID_NAMES.put(SUBROUTINE,   "Subroutine");
        ID_NAMES.put(HELPROUTINE,  "Helproutine");
        ID_NAMES.put(CLASS,        "Class");
        ID_NAMES.put(DIALOG,       "Dialog");
        ID_NAMES.put(TEXT,         "Text");
        ID_NAMES.put(FUNCTION,     "Function");
        ID_NAMES.put(ADAPTER,      "Adapter");
        ID_NAMES.put(RESOURCE,     "Resources");

        ID_EXTENSIONS = new Hashtable<>();
        ID_EXTENSIONS.put(GDA,          "NSG");
        ID_EXTENSIONS.put(LDA,          "NSL");
        ID_EXTENSIONS.put(PDA,          "NSA");
        ID_EXTENSIONS.put(DDM,          "NSD");
        ID_EXTENSIONS.put(PROGRAM,      "NSP");
        ID_EXTENSIONS.put(SUBPROGRAM,   "NSN");
        ID_EXTENSIONS.put(MAP,          "NSM");
        ID_EXTENSIONS.put(COPYCODE,     "NSC");
        ID_EXTENSIONS.put(SUBROUTINE,   "NSS");
        ID_EXTENSIONS.put(HELPROUTINE,  "NSH");
        ID_EXTENSIONS.put(CLASS,        "NS4");
        ID_EXTENSIONS.put(DIALOG,       "NS3");
        ID_EXTENSIONS.put(TEXT,         "NST");
        ID_EXTENSIONS.put(FUNCTION,     "NS8");
        ID_EXTENSIONS.put(ADAPTER,      "NS6");
        ID_EXTENSIONS.put(RESOURCE,     "NS7");
    }

    private ObjectType() {}

    public static Hashtable getInstanceIdName() {
        return ID_NAMES;
    }

    public static Hashtable getInstanceIdExtension() {
        return ID_EXTENSIONS;
    }
}

