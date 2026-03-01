package com.softwareag.naturalone.natural.pal.external;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

public final class ObjectType {
    public static final int NONE = 0;
    public static final int GDA = 1;
    public static final int LDA = 2;
    public static final int PDA = 4;
    public static final int DDM = 8;
    public static final int PROGRAM = 16;
    public static final int SUBPROGRAM = 32;
    public static final int MAP = 64;
    public static final int COPYCODE = 128;
    public static final int SUBROUTINE = 256;
    public static final int HELPROUTINE = 512;
    public static final int CLASS = 1024;
    public static final int DIALOG = 2048;
    public static final int TEXT = 4096;
    public static final int NCP = 8192;
    public static final int ADAPTVIEW = 16384;
    public static final int ERRMSG = 32768;
    public static final int RESOURCE = 65536;
    public static final int ANY = 131072;
    public static final int FUNCTION = 524288;
    public static final int ADAPTER = 2097152;

    public static final String GDA_EXT = "NSG";
    public static final String LDA_EXT = "NSL";
    public static final String PDA_EXT = "NSA";
    public static final String DDM_EXT = "NSD";
    public static final String PROGRAM_EXT = "NSP";
    public static final String SUBPROGRAM_EXT = "NSN";
    public static final String MAP_EXT = "NSM";
    public static final String COPYCODE_EXT = "NSC";
    public static final String SUBROUTINE_EXT = "NSS";
    public static final String HELPROUTINE_EXT = "NSH";
    public static final String CLASS_EXT = "NS4";
    public static final String DIALOG_EXT = "NS3";
    public static final String TEXT_EXT = "NST";
    public static final String NCP_EXT = "NS5";
    public static final String ADAPTVIEW_EXT = "NS6";
    public static final String FUNCTION_EXT = "NS7";
    public static final String ADAPTER_EXT = "NS8";

    public static final String PROFILER_RES_EXT_FLAT = "NPRF";
    public static final String PROFILER_RES_EXT_CONSOLIDATED = "NPRC";
    public static final String COVERGAGE_RES_EXT = "NCVF";

    public static final List<String> BREAKPOINT_EXTENSIONS = Collections.unmodifiableList(
            Arrays.asList("NSP", "NSN", "NS7", "NS3", "NSM", "NSS", "NSC", "NSH", "NSG"));

    private static Hashtable<Integer, String> idZuDateiendung;
    private static Hashtable<String, Integer> dateiendungZuId;
    private static Hashtable<String, String> dateiendungZuName;
    private static Hashtable<Integer, String> idZuName;
    private static Hashtable<Integer, String> idZuGruppenName;
    private static List<String> sprachListe;

    private ObjectType() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceIdExtension() {
        if (idZuDateiendung == null) {
            idZuDateiendung = new Hashtable<>();
            idZuDateiendung.put(GDA, "NSG");
            idZuDateiendung.put(LDA, "NSL");
            idZuDateiendung.put(PDA, "NSA");
            idZuDateiendung.put(DDM, "NSD");
            idZuDateiendung.put(PROGRAM, "NSP");
            idZuDateiendung.put(SUBPROGRAM, "NSN");
            idZuDateiendung.put(MAP, "NSM");
            idZuDateiendung.put(COPYCODE, "NSC");
            idZuDateiendung.put(SUBROUTINE, "NSS");
            idZuDateiendung.put(HELPROUTINE, "NSH");
            idZuDateiendung.put(CLASS, "NS4");
            idZuDateiendung.put(DIALOG, "NS3");
            idZuDateiendung.put(TEXT, "NST");
            idZuDateiendung.put(NCP, "NS5");
            idZuDateiendung.put(ADAPTVIEW, "NS6");
            idZuDateiendung.put(FUNCTION, "NS7");
            idZuDateiendung.put(ADAPTER, "NS8");
            idZuDateiendung.put(ERRMSG, "ERR");
            idZuDateiendung.put(RESOURCE, "Resources");
        }
        return (Hashtable) idZuDateiendung.clone();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceExtensionId() {
        if (dateiendungZuId == null) {
            dateiendungZuId = new Hashtable<>();
            dateiendungZuId.put("NSG", GDA);
            dateiendungZuId.put("NSL", LDA);
            dateiendungZuId.put("NSA", PDA);
            dateiendungZuId.put("NSD", DDM);
            dateiendungZuId.put("NSP", PROGRAM);
            dateiendungZuId.put("NSN", SUBPROGRAM);
            dateiendungZuId.put("NSM", MAP);
            dateiendungZuId.put("NSC", COPYCODE);
            dateiendungZuId.put("NSS", SUBROUTINE);
            dateiendungZuId.put("NSH", HELPROUTINE);
            dateiendungZuId.put("NS4", CLASS);
            dateiendungZuId.put("NS3", DIALOG);
            dateiendungZuId.put("NST", TEXT);
            dateiendungZuId.put("NS5", NCP);
            dateiendungZuId.put("NS6", ADAPTVIEW);
            dateiendungZuId.put("NS7", FUNCTION);
            dateiendungZuId.put("NS8", ADAPTER);
        }
        return (Hashtable) dateiendungZuId.clone();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceExtensionName() {
        if (dateiendungZuName == null) {
            dateiendungZuName = new Hashtable<>();
            dateiendungZuName.put("NSG", "Global Data Area");
            dateiendungZuName.put("NSL", "Local Data Area");
            dateiendungZuName.put("NSA", "Parameter Data Area");
            dateiendungZuName.put("NSD", "DDM");
            dateiendungZuName.put("NSP", "Program");
            dateiendungZuName.put("NSN", "Subprogram");
            dateiendungZuName.put("NSM", "Map");
            dateiendungZuName.put("NSC", "Copycode");
            dateiendungZuName.put("NSS", "Subroutine");
            dateiendungZuName.put("NSH", "Helproutine");
            dateiendungZuName.put("NS4", "Class");
            dateiendungZuName.put("NS3", "Dialog");
            dateiendungZuName.put("NST", "Text");
            dateiendungZuName.put("NS5", "Command Processor");
            dateiendungZuName.put("NS6", "Adapt View");
            dateiendungZuName.put("NS7", "Function");
            dateiendungZuName.put("NS8", "Adapter");
        }
        return (Hashtable) dateiendungZuName.clone();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceIdName() {
        if (idZuName == null) {
            idZuName = new Hashtable<>();
            idZuName.put(GDA, "Global Data Area");
            idZuName.put(LDA, "Local Data Area");
            idZuName.put(PDA, "Parameter Data Area");
            idZuName.put(DDM, "DDM");
            idZuName.put(PROGRAM, "Program");
            idZuName.put(SUBPROGRAM, "Subprogram");
            idZuName.put(MAP, "Map");
            idZuName.put(COPYCODE, "Copycode");
            idZuName.put(SUBROUTINE, "Subroutine");
            idZuName.put(HELPROUTINE, "Helproutine");
            idZuName.put(CLASS, "Class");
            idZuName.put(DIALOG, "Dialog");
            idZuName.put(TEXT, "Text");
            idZuName.put(NCP, "Command Processor");
            idZuName.put(ADAPTVIEW, "Adapt View");
            idZuName.put(FUNCTION, "Function");
            idZuName.put(ADAPTER, "Adapter");
            idZuName.put(ERRMSG, "Error Messages");
            idZuName.put(RESOURCE, "Resources");
        }
        return (Hashtable) idZuName.clone();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized Hashtable getInstanceIdGroupName() {
        if (idZuGruppenName == null) {
            idZuGruppenName = new Hashtable<>();
            idZuGruppenName.put(GDA, "Global Data Areas");
            idZuGruppenName.put(LDA, "Local Data Areas");
            idZuGruppenName.put(PDA, "Parameter Data Areas");
            idZuGruppenName.put(DDM, "DDMs");
            idZuGruppenName.put(PROGRAM, "Programs");
            idZuGruppenName.put(SUBPROGRAM, "Subprograms");
            idZuGruppenName.put(MAP, "Maps");
            idZuGruppenName.put(COPYCODE, "Copycodes");
            idZuGruppenName.put(SUBROUTINE, "Subroutines");
            idZuGruppenName.put(HELPROUTINE, "Helproutines");
            idZuGruppenName.put(CLASS, "Classes");
            idZuGruppenName.put(DIALOG, "Dialogs");
            idZuGruppenName.put(TEXT, "Texts");
            idZuGruppenName.put(NCP, "Command Processors");
            idZuGruppenName.put(ADAPTVIEW, "Adapt Views");
            idZuGruppenName.put(FUNCTION, "Functions");
            idZuGruppenName.put(ADAPTER, "Adapters");
            idZuGruppenName.put(ERRMSG, "Error Messages");
            idZuGruppenName.put(RESOURCE, "Resources");
        }
        return (Hashtable) idZuGruppenName.clone();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final List getUnmodifiableLanguageList() {
        if (sprachListe == null) {
            String[] langs = new String[60];
            langs[0] = "English";
            langs[1] = "German";
            langs[2] = "French";
            langs[3] = "Spanish";
            langs[4] = "Italian";
            langs[5] = "Dutch";
            langs[6] = "Turkish";
            langs[7] = "Danish";
            langs[8] = "Norwegian";
            langs[9] = "Albanian";
            langs[10] = "Portuguese";
            langs[11] = "Russian";
            langs[12] = "Czech";
            langs[13] = "Finnish";
            langs[14] = "Swedish";
            langs[15] = "Slovenian";
            langs[16] = "Polish";
            langs[17] = "Hungarian";
            langs[18] = "Greek";
            langs[19] = "Icelandic";
            langs[20] = "Croatian";
            langs[21] = "Romanian";
            langs[22] = "Serbian";
            langs[23] = "Bulgarian";
            langs[24] = "Slovak";
            langs[25] = "Hebrew";
            langs[26] = "Arabic";
            langs[27] = "Persian";
            langs[28] = "Urdu";
            langs[29] = "Katakana";
            langs[30] = "Latvian";
            langs[31] = "Lithuanian";
            langs[32] = "Estonian";
            langs[33] = "Ukrainian";
            for (int i = 34; i <= 49; i++) {
                langs[i] = "reserved";
            }
            langs[50] = "Hindi";
            langs[51] = "Malayan";
            langs[52] = "Thai";
            langs[53] = "reserved";
            langs[54] = "reserved";
            langs[55] = "reserved";
            langs[56] = "Chinese (People's Republic of China)";
            langs[57] = "Chinese (Republic of China)";
            langs[58] = "Japanese (Kanji)";
            langs[59] = "Korean";
            sprachListe = Collections.unmodifiableList(Arrays.asList(langs));
        }
        return sprachListe;
    }

    public static String langToFileName(String langIndex, boolean withExtension) {
        String idx = langIndex;
        if (idx.length() < 2) {
            idx = "0" + idx;
        }
        if (withExtension) {
            return "N" + idx + "APMSL.MSG";
        } else {
            return "N" + idx + "APMSL";
        }
    }
}
