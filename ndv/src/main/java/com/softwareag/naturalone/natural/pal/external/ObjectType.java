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

    public static final int PROGRAM      = 1;
    public static final int SUBPROGRAM   = 2;
    public static final int SUBROUTINE   = 3;
    public static final int COPYCODE     = 4;
    public static final int MAP          = 5;
    public static final int GDA          = 6;
    public static final int LDA          = 7;
    public static final int PDA          = 8;
    public static final int DDM          = 9;
    public static final int HELPROUTINE  = 10;
    public static final int TEXT         = 11;
    public static final int CLASS        = 12;
    public static final int FUNCTION     = 13;
    public static final int ADAPTER      = 14;
    public static final int RESOURCE     = 15;

    private static final Hashtable<Integer, String> ID_NAMES;
    private static final Hashtable<Integer, String> ID_EXTENSIONS;

    static {
        ID_NAMES = new Hashtable<>();
        ID_NAMES.put(PROGRAM,     "Program");
        ID_NAMES.put(SUBPROGRAM,  "Subprogram");
        ID_NAMES.put(SUBROUTINE,  "Subroutine");
        ID_NAMES.put(COPYCODE,    "Copycode");
        ID_NAMES.put(MAP,         "Map");
        ID_NAMES.put(GDA,         "GDA");
        ID_NAMES.put(LDA,         "LDA");
        ID_NAMES.put(PDA,         "PDA");
        ID_NAMES.put(DDM,         "DDM");
        ID_NAMES.put(HELPROUTINE, "Helproutine");
        ID_NAMES.put(TEXT,        "Text");
        ID_NAMES.put(CLASS,       "Class");
        ID_NAMES.put(FUNCTION,    "Function");
        ID_NAMES.put(ADAPTER,     "Adapter");
        ID_NAMES.put(RESOURCE,    "Resource");

        ID_EXTENSIONS = new Hashtable<>();
        ID_EXTENSIONS.put(PROGRAM,     "NSP");
        ID_EXTENSIONS.put(SUBPROGRAM,  "NSN");
        ID_EXTENSIONS.put(SUBROUTINE,  "NSS");
        ID_EXTENSIONS.put(COPYCODE,    "NSC");
        ID_EXTENSIONS.put(MAP,         "NSM");
        ID_EXTENSIONS.put(GDA,         "NSG");
        ID_EXTENSIONS.put(LDA,         "NSL");
        ID_EXTENSIONS.put(PDA,         "NSA");
        ID_EXTENSIONS.put(DDM,         "NSD");
        ID_EXTENSIONS.put(HELPROUTINE, "NSH");
        ID_EXTENSIONS.put(TEXT,        "NST");
        ID_EXTENSIONS.put(CLASS,       "NSX");
        ID_EXTENSIONS.put(FUNCTION,    "NSF");
        ID_EXTENSIONS.put(ADAPTER,     "NSU");
        ID_EXTENSIONS.put(RESOURCE,    "NSR");
    }

    private ObjectType() {}

    public static Hashtable getInstanceIdName() {
        return ID_NAMES;
    }

    public static Hashtable getInstanceIdExtension() {
        return ID_EXTENSIONS;
    }
}

