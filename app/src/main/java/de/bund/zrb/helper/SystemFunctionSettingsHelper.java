package de.bund.zrb.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.model.SystemFunctionEntry;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence helper for system function definitions (IDCAMS, IEFBR14, SORT, etc.).
 * Stored as {@code system_functions.json} in the app settings folder.
 */
public class SystemFunctionSettingsHelper {

    private static final File FUNCTIONS_FILE =
            new File(SettingsHelper.getSettingsFolder(), "system_functions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SystemFunctionEntry>>() {}.getType();

    /**
     * Load system functions from disk. Returns an empty list if the file doesn't exist.
     */
    public static List<SystemFunctionEntry> load() {
        if (!FUNCTIONS_FILE.exists()) return new ArrayList<SystemFunctionEntry>();
        try (Reader reader = new InputStreamReader(new FileInputStream(FUNCTIONS_FILE), StandardCharsets.UTF_8)) {
            List<SystemFunctionEntry> result = GSON.fromJson(reader, LIST_TYPE);
            return result != null ? result : new ArrayList<SystemFunctionEntry>();
        } catch (JsonSyntaxException e) {
            System.err.println("⚠ Fehler beim Parsen von system_functions.json: " + e.getMessage());
            return new ArrayList<SystemFunctionEntry>();
        } catch (Exception e) {
            System.err.println("⚠ Fehler beim Laden von system_functions.json: " + e.getMessage());
            return new ArrayList<SystemFunctionEntry>();
        }
    }

    /**
     * Save system functions to disk.
     */
    public static void save(List<SystemFunctionEntry> entries) {
        try {
            FUNCTIONS_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(FUNCTIONS_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Build a lookup map: PGM name (uppercase) → SystemFunctionEntry.
     * Used at runtime to check whether a JCL PGM reference is a known system function.
     */
    public static Map<String, SystemFunctionEntry> buildLookup() {
        List<SystemFunctionEntry> entries = load();
        Map<String, SystemFunctionEntry> map = new LinkedHashMap<String, SystemFunctionEntry>();
        for (SystemFunctionEntry entry : entries) {
            if (entry.getName() != null && !entry.getName().isEmpty()) {
                map.put(entry.getName().toUpperCase(), entry);
            }
        }
        return map;
    }

    /**
     * Returns a list of commonly used z/OS system utilities with Wikipedia links.
     * Users can extend/delete these. Called when the file doesn't exist yet.
     */
    public static List<SystemFunctionEntry> getDefaults() {
        List<SystemFunctionEntry> defaults = new ArrayList<SystemFunctionEntry>();

        // Access Method Services (dataset management)
        defaults.add(new SystemFunctionEntry("IDCAMS",
                "Access_Method_Services", "Access_Method_Services",
                "Access Method Services — VSAM-Verwaltung (DEFINE, DELETE, REPRO, LISTCAT, ALTER)"));

        // Do-nothing utility
        defaults.add(new SystemFunctionEntry("IEFBR14",
                "IEFBR14", "IEFBR14",
                "Null-Programm — allokiert/löscht Datasets ohne Verarbeitung"));

        // Sort/Merge
        defaults.add(new SystemFunctionEntry("SORT",
                "DFSORT", "DFSORT",
                "DFSORT — Sortieren, Mischen, Kopieren von Datasets"));
        defaults.add(new SystemFunctionEntry("ICEMAN",
                "DFSORT", "DFSORT",
                "DFSORT ICEMAN — alternativer Aufrufer für DFSORT"));
        defaults.add(new SystemFunctionEntry("SYNCSORT",
                "Syncsort", "Syncsort",
                "Syncsort — Sortier-Utility (Drittanbieter)"));

        // TSO/REXX batch
        defaults.add(new SystemFunctionEntry("IKJEFT01",
                "Time_Sharing_Option", "Time_Sharing_Option",
                "TSO/E Batch — führt TSO-Befehle und REXX im Batch aus"));
        defaults.add(new SystemFunctionEntry("IKJEFT1A",
                "Time_Sharing_Option", "Time_Sharing_Option",
                "TSO/E Batch (IKJEFT1A) — wie IKJEFT01, setzt RC=12 bei ABEND"));
        defaults.add(new SystemFunctionEntry("IKJEFT1B",
                "Time_Sharing_Option", "Time_Sharing_Option",
                "TSO/E Batch (IKJEFT1B) — wie IKJEFT01, setzt RC=16 bei ABEND"));
        defaults.add(new SystemFunctionEntry("IRXJCL",
                "REXX", "Rexx",
                "REXX im Batch — führt REXX-Prozeduren unter TSO aus"));

        // Dataset utilities
        defaults.add(new SystemFunctionEntry("IEBGENER",
                "IEBGENER", "IEBGENER",
                "Dataset Copy/Print — kopiert sequentielle Datasets"));
        defaults.add(new SystemFunctionEntry("IEBCOPY",
                "IEBCOPY", "IEBCOPY",
                "PDS Copy/Compress — kopiert und komprimiert PDS-Bibliotheken"));
        defaults.add(new SystemFunctionEntry("IEBUPDTE",
                "IEBUPDTE", "IEBUPDTE",
                "Sequential Update — fügt Members in PDS ein oder aktualisiert sie"));
        defaults.add(new SystemFunctionEntry("IEHPROGM",
                "IEHPROGM", "IEHPROGM",
                "Katalogverwaltung — Scratch, Rename, Catalog, Uncatalog"));
        defaults.add(new SystemFunctionEntry("IEHLIST",
                "IEHLIST", "IEHLIST",
                "Catalog Listing — listet VTOC und Katalogeinträge"));
        defaults.add(new SystemFunctionEntry("IEHMOVE",
                "IEHMOVE", "IEHMOVE",
                "Dataset Move/Copy — verschiebt oder kopiert Datasets"));

        // Linkage Editor / Binder
        defaults.add(new SystemFunctionEntry("IEWL",
                "Linkage_Editor", "Linkage_editor",
                "Linkage Editor — bindet Object Modules zu Load Modules"));
        defaults.add(new SystemFunctionEntry("HEWL",
                "Linkage_Editor", "Linkage_editor",
                "Linkage Editor (alias HEWL)"));

        // Compiler
        defaults.add(new SystemFunctionEntry("IGYCRCTL",
                "COBOL", "COBOL",
                "Enterprise COBOL Compiler"));
        defaults.add(new SystemFunctionEntry("ASMA90",
                "Assembler_(Informatik)", "Assembly_language#Assembler",
                "High Level Assembler (HLASM)"));
        defaults.add(new SystemFunctionEntry("CBCDRVR",
                "C_(Programmiersprache)", "C_(programming_language)",
                "z/OS XL C/C++ Compiler Driver"));

        // DB2
        defaults.add(new SystemFunctionEntry("DSNTEP2",
                "Db2", "IBM_Db2",
                "DB2 Dynamic SQL Batch — führt SQL-Anweisungen im Batch aus"));
        defaults.add(new SystemFunctionEntry("DSNUPROC",
                "Db2", "IBM_Db2",
                "DB2 Utilities — LOAD, UNLOAD, REORG, RUNSTATS, COPY, RECOVER"));
        defaults.add(new SystemFunctionEntry("DSNTIAD",
                "Db2", "IBM_Db2",
                "DB2 Dynamic SQL Processor"));

        // CICS
        defaults.add(new SystemFunctionEntry("DFHCSDUP",
                "Customer_Information_Control_System", "CICS",
                "CICS CSD Utility — verwaltet CICS System Definition Datasets"));

        // JES2
        defaults.add(new SystemFunctionEntry("IEFBR14",
                "IEFBR14", "IEFBR14",
                "Null-Programm"));

        // FTP
        defaults.add(new SystemFunctionEntry("FTP",
                "File_Transfer_Protocol", "File_Transfer_Protocol",
                "z/OS FTP Client — Dateiübertragung"));

        // Print/Report
        defaults.add(new SystemFunctionEntry("ICETOOL",
                "DFSORT", "DFSORT",
                "DFSORT ICETOOL — erweiterte Sortier- und Berichtsoperationen"));

        // RACF / Security
        defaults.add(new SystemFunctionEntry("ICHUT100",
                "Resource_Access_Control_Facility", "Resource_Access_Control_Facility",
                "RACF Utility — Datenbank-Entlade-Utility"));
        defaults.add(new SystemFunctionEntry("ICHUT200",
                "Resource_Access_Control_Facility", "Resource_Access_Control_Facility",
                "RACF Utility — Cross-Reference-Utility"));

        // SMS / DFSMS
        defaults.add(new SystemFunctionEntry("ADRDSSU",
                "DFSMSdss", "DFSMSdss",
                "DFSMSdss — Dump, Restore, Copy, Print von Datasets und Volumes"));

        // General utilities
        defaults.add(new SystemFunctionEntry("GIMSMP",
                "System_Modification_Program/Extended", "System_Modification_Program/Extended",
                "SMP/E — System Modification Program/Extended"));
        defaults.add(new SystemFunctionEntry("ICKDSF",
                "ICKDSF", "ICKDSF",
                "Device Support Facilities — DASD-Initialisierung und -Verwaltung"));

        // Remove duplicates (IEFBR14 was added twice above — keep first)
        Map<String, SystemFunctionEntry> dedup = new LinkedHashMap<String, SystemFunctionEntry>();
        for (SystemFunctionEntry e : defaults) {
            if (!dedup.containsKey(e.getName())) {
                dedup.put(e.getName(), e);
            }
        }
        return new ArrayList<SystemFunctionEntry>(dedup.values());
    }
}

