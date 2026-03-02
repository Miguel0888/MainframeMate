package de.bund.zrb.ndv.transaction.internal.services;

import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.pal.type.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;
import de.bund.zrb.ndv.core.api.*;
import de.bund.zrb.ndv.core.type.*;
import de.bund.zrb.ndv.transaction.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bibliotheks-Browsing:
 * getSystemFiles, getLibrariesFirst/Next, getNumberOfLibraries, isLibraryEmpty,
 * getLibraryStatistics, getLibraryInfo, getLibraryOfObject.
 */
public class LibraryBrowseService {

    private static final PalTypeSystemFile[] LEERES_SYSTEMFILE_ARRAY = new PalTypeSystemFile[0];
    private static final PalTypeLibrary[] LEERES_BIBLIOTHEK_ARRAY = new PalTypeLibrary[0];

    /** Art der laufenden Abfrage: 0=keine, 1=Bibliotheken, 2=Objekte */
    private static final int KEINE_ABFRAGE = 0;
    private static final int BIBLIOTHEKS_ABFRAGE = 1;

    private final PalSessionContext ctx;

    /** Aktueller Benachrichtigungscode vom Server (0=nicht aktiv, 6=weiter, 7=fertig) */
    private int aktuellerBenachrichtigungscode = 0;

    /** Art der laufenden Abfrage */
    private int abfrageArt = KEINE_ABFRAGE;

    /** Ob Duplikate durch Semikolon-Filter moeglich sind (Mainframe) */
    private boolean duplikateMoeglich = false;

    /** Menge bereits gelieferter Bibliotheksnamen zur Duplikat-Erkennung */
    private Set<String> bereitsGeliefert = new TreeSet<>();

    public LibraryBrowseService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ═══════════════════════════════════════════════
    //  getSystemFiles
    // ═══════════════════════════════════════════════

    public IPalTypeSystemFile[] getSystemFiles() throws IOException, PalResultException {
        if (ctx.getPal() == null) {
            throw new IllegalStateException("connection to Ndv server not available");
        }

        PalTrace.header("getSystemFiles");
        PalTypeOperation operation = new PalTypeOperation(6);
        ctx.getPal().add((IPalType) operation);
        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        PalTypeSystemFile[] ergebnis = (PalTypeSystemFile[]) ctx.getPal().retrieve(3);
        return ergebnis == null ? LEERES_SYSTEMFILE_ARRAY : (PalTypeSystemFile[]) ergebnis.clone();
    }

    // ═══════════════════════════════════════════════
    //  getLibrariesFirst
    // ═══════════════════════════════════════════════

    public IPalTypeLibrary[] getLibrariesFirst(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (filter.length() == 0) throw new IllegalArgumentException("filter parameter must not be empty");

        PalTrace.header("getLibrariesFirst");
        this.abfrageArt = BIBLIOTHEKS_ABFRAGE;
        this.duplikateMoeglich = duplikatePruefen(filter);
        this.bereitsGeliefert.clear();

        // Operation senden: Bibliotheken auflisten (Op=4)
        PalTypeOperation operation = new PalTypeOperation(4);
        ctx.getPal().add((IPalType) operation);

        // LibId mit Filter senden
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                filter, sysFile.getPassword(), sysFile.getCipher(), 30);
        ctx.getPal().add((IPalType) libId);

        // Notify-Request: Chunked-Modus anfordern (17 = Server soll chunked liefern)
        PalTypeNotify notify = new PalTypeNotify(17);
        ctx.getPal().add((IPalType) notify);

        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        // Pruefen ob chunked Modus aktiv
        IPalTypeNotify[] benachrichtigung = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        IPalTypeGeneric[] generisch = (IPalTypeGeneric[]) ctx.getPal().retrieve(20);
        boolean weitereBloecke = false;
        if (generisch != null && benachrichtigung != null) {
            int anzahl = generisch[0].getData();
            weitereBloecke = anzahl > 0;
        }

        PalTypeLibrary[] ergebnis = naechsterBibliotheksBlock(weitereBloecke);
        if (ergebnis == null) {
            this.aktuellerBenachrichtigungscode = 0;
        }

        return ergebnis == null ? LEERES_BIBLIOTHEK_ARRAY : ergebnis;
    }

    // ═══════════════════════════════════════════════
    //  getLibrariesNext
    // ═══════════════════════════════════════════════

    public IPalTypeLibrary[] getLibrariesNext() throws IOException, PalResultException {
        PalTypeLibrary[] ergebnis = null;

        ctx.requirePal();
        if (this.aktuellerBenachrichtigungscode == 0) {
            throw new IllegalStateException("getLibrariesNext cannot be used without a getLibrariesFirst call");
        }
        if (this.abfrageArt != BIBLIOTHEKS_ABFRAGE) {
            throw new IllegalStateException("getLibrariesFirst must be called first");
        }

        PalTrace.header("getLibrariesNext");
        if (this.aktuellerBenachrichtigungscode != 7) {
            do {
                ergebnis = naechsterBibliotheksBlock(true);
            } while (ergebnis != null && ergebnis.length == 0);
        }

        if (ergebnis == null) {
            this.aktuellerBenachrichtigungscode = 0;
        }

        return ergebnis == null ? LEERES_BIBLIOTHEK_ARRAY : ergebnis;
    }

    // ═══════════════════════════════════════════════
    //  getNumberOfLibraries
    // ═══════════════════════════════════════════════

    public int getNumberOfLibraries(IPalTypeSystemFile sysFile, String filter)
            throws IOException, PalResultException {
        int anzahl = -1;

        ctx.requirePal();
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (filter.length() == 0) throw new IllegalArgumentException("filter parameter must not be empty");

        PalTrace.header("getNumberOfLibraries");
        this.abfrageArt = BIBLIOTHEKS_ABFRAGE;

        PalTypeOperation operation = new PalTypeOperation(4);
        ctx.getPal().add((IPalType) operation);

        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                filter, sysFile.getPassword(), sysFile.getCipher(), 30);
        ctx.getPal().add((IPalType) libId);

        PalTypeNotify notify = new PalTypeNotify(17);
        ctx.getPal().add((IPalType) notify);

        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        IPalTypeNotify[] benachrichtigung = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        IPalTypeGeneric[] generisch = (IPalTypeGeneric[]) ctx.getPal().retrieve(20);
        boolean weitereBloecke = false;
        if (generisch != null && benachrichtigung != null) {
            anzahl = generisch[0].getData();
            weitereBloecke = anzahl > 0;
        }

        naechsterBibliotheksBlock(weitereBloecke);
        abfrageBeenden();

        return anzahl;
    }

    // ═══════════════════════════════════════════════
    //  isLibraryEmpty
    // ═══════════════════════════════════════════════

    public boolean isLibraryEmpty(IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");

        PalTrace.header("isLibraryEmpty");

        ctx.getPal().add((IPalType) (new PalTypeOperation(46, 0)));
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 6);
        ctx.getPal().add((IPalType) libId);
        ctx.getPal().commit();

        int fehlerCode = ctx.getError();
        return fehlerCode != 0;
    }

    // ═══════════════════════════════════════════════
    //  getLibraryStatistics
    // ═══════════════════════════════════════════════

    public IPalTypeLibraryStatistics getLibraryStatistics(Set<ELibraryStatisticsOption> options,
                                                          IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (library.length() == 0) throw new IllegalArgumentException("library parameter must not be empty");

        PalTrace.header("getLibraryStatistics");

        int opFlags = options.contains(ELibraryStatisticsOption.REBUILD_STATISTICS_RECORD) ? 5 : 3;
        if (options.contains(ELibraryStatisticsOption.GET_LINKED_DDMS)) {
            opFlags |= 16;
        }

        PalTypeOperation operation = new PalTypeOperation(5, opFlags);
        ctx.getPal().add((IPalType) operation);

        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 30);
        ctx.getPal().add((IPalType) libId);

        ctx.getPal().commit();

        IPalTypeNotify[] benachrichtigung = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        IPalTypeGeneric[] generisch = (IPalTypeGeneric[]) ctx.getPal().retrieve(20);
        boolean weitereBloecke = (generisch != null && benachrichtigung != null);

        PalTypeLibraryStatistics[] statistik = naechsterStatistikBlock(weitereBloecke);
        this.aktuellerBenachrichtigungscode = 0;
        return statistik != null ? statistik[0] : null;
    }

    // ═══════════════════════════════════════════════
    //  getLibraryInfo
    // ═══════════════════════════════════════════════

    public ILibraryInfo getLibraryInfo(int option, IPalTypeSystemFile sysFile, String library)
            throws IOException, PalResultException {

        ctx.requirePal();

        PalTrace.header("getLibraryInfo");
        ctx.getPal().add((IPalType) (new PalTypeOperation(56, option)));
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 6);
        ctx.getPal().add((IPalType) libId);
        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        PalTypeCmdGuard[] cmdGuard = (PalTypeCmdGuard[]) ctx.getPal().retrieve(27);
        PalTypeLibrary[] libs = (PalTypeLibrary[]) ctx.getPal().retrieve(5);

        LibraryInfo info = (new LibraryInfo.Builder(library)).prefix(libs).cmdGuard(cmdGuard).build();
        return info;
    }

    // ═══════════════════════════════════════════════
    //  getLibraryOfObject
    // ═══════════════════════════════════════════════

    public IPalTypeLibId getLibraryOfObject(IPalTypeLibId libId, String objectName, Set<EObjectKind> kinds)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (libId == null) throw new IllegalArgumentException("libId parameter must not be null");
        if (objectName == null) throw new IllegalArgumentException("objectName must not be null");
        if (objectName.length() == 0) throw new IllegalArgumentException("objectName parameter must not be empty");

        PalTrace.header("getLibraryOfObject");
        IPalTypeLibId ergebnis = null;

        try {
            int kindFlags = 0;
            if (kinds.contains(EObjectKind.SOURCE) && kinds.contains(EObjectKind.GP)) {
                kindFlags = 3;
            } else if (kinds.contains(EObjectKind.SOURCE)) {
                kindFlags = 1;
            } else if (kinds.contains(EObjectKind.GP)) {
                kindFlags = 2;
            }

            IPalTypeSystemFile sysFile = PalTypeSystemFileFactory.newInstance(
                    libId.getDatabaseId(), libId.getFileNumber(), 0);

            // objectsFirst-Logik inline: Op=3, Flags=7
            ctx.getPal().add((IPalType) (new PalTypeOperation(3, 7)));
            PalTypeLibId suchLibId = new PalTypeLibId(
                    sysFile.getDatabaseId(), sysFile.getFileNumber(),
                    libId.getLibrary(), sysFile.getPassword(), sysFile.getCipher(), 6);
            ctx.getPal().add((IPalType) suchLibId);
            PalTypeObjDesc2 objDesc = new PalTypeObjDesc2(131072, kindFlags, objectName);
            ctx.getPal().add((IPalType) objDesc);
            PalTypeNotify notify = new PalTypeNotify(17);
            ctx.getPal().add((IPalType) notify);
            ctx.getPal().commit();

            PalResultException fehler = ctx.getResultException();
            if (fehler != null) {
                if (fehler.getErrorNumber() != 82) throw fehler;
                // Fehler 82 = nicht gefunden, ergebnis bleibt null
            } else {
                IPalTypeNotify[] benachrichtigung = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
                IPalTypeGeneric[] generisch = (IPalTypeGeneric[]) ctx.getPal().retrieve(20);
                boolean weitereBloecke = false;
                if (benachrichtigung != null && generisch != null && generisch[0].getData() > 0) {
                    weitereBloecke = true;
                }

                // Objekte abholen (nextObjectsChunk)
                if (weitereBloecke) {
                    this.aktuellerBenachrichtigungscode = weiterAbfragen();
                }
                IPalTypeObject[] objekte = (IPalTypeObject[]) ctx.getPal().retrieve(8);

                if (objekte != null) {
                    IPalTypeLibId[] gefundeneLibs = (IPalTypeLibId[]) ctx.getPal().retrieve(6);
                    if (gefundeneLibs != null) {
                        ergebnis = gefundeneLibs[0];
                        if (ergebnis.getDatabaseId() == 0 && ergebnis.getFileNumber() == 0) {
                            ergebnis.setDatabaseId(libId.getDatabaseId());
                            ergebnis.setFileNumber(libId.getFileNumber());
                        }
                    } else {
                        ergebnis = libId;
                    }
                }
            }
        } finally {
            this.aktuellerBenachrichtigungscode = 0;
        }

        return ergebnis;
    }

    // ═══════════════════════════════════════════════
    //  terminateRetrieval
    // ═══════════════════════════════════════════════

    /**
     * Beendet eine laufende Bibliotheks- oder Objekt-Abfrage von aussen.
     */
    public void terminateRetrieval() throws IOException, PalResultException {
        if (this.abfrageArt == KEINE_ABFRAGE) {
            // Nichts zu tun, wenn keine Abfrage aktiv
            return;
        }
        abfrageBeenden();
    }

    // ═══════════════════════════════════════════════
    //  Interne Hilfsmethoden
    // ═══════════════════════════════════════════════

    /**
     * Naechsten Block von Bibliotheken vom Server abholen.
     * Bei aktivem Chunked-Modus wird zunaechst eine Fortsetzungs-Benachrichtigung gesendet.
     */
    private PalTypeLibrary[] naechsterBibliotheksBlock(boolean weitereBloecke)
            throws IOException, PalResultException {
        PalTypeLibrary[] ergebnis = null;

        if (weitereBloecke) {
            this.aktuellerBenachrichtigungscode = weiterAbfragen();
        }

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        ergebnis = (PalTypeLibrary[]) ctx.getPal().retrieve(5);

        // Bei Mainframe-Semikolon-Filtern koennen Duplikate auftreten
        if (this.duplikateMoeglich && ergebnis != null) {
            ArrayList<PalTypeLibrary> bereinigt = new ArrayList<>();
            for (PalTypeLibrary lib : ergebnis) {
                if (!this.bereitsGeliefert.contains(lib.getLibrary())) {
                    bereinigt.add(lib);
                    this.bereitsGeliefert.add(lib.getLibrary());
                }
            }
            ergebnis = bereinigt.toArray(new PalTypeLibrary[bereinigt.size()]);
        }

        return ergebnis;
    }

    /**
     * Naechsten Block von Bibliotheks-Statistiken abholen.
     */
    private PalTypeLibraryStatistics[] naechsterStatistikBlock(boolean weitereBloecke)
            throws IOException, PalResultException {

        if (weitereBloecke) {
            this.aktuellerBenachrichtigungscode = weiterAbfragen();
        }

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        return (PalTypeLibraryStatistics[]) ctx.getPal().retrieve(4);
    }

    /**
     * Sendet eine Fortsetzungs-Benachrichtigung an den Server
     * und gibt den Benachrichtigungscode der Antwort zurueck.
     */
    private int weiterAbfragen() throws IOException, PalResultException {
        PalTypeNotify notify = new PalTypeNotify(4);
        ctx.getPal().add((IPalType) notify);
        ctx.getPal().commit();
        IPalTypeNotify[] antwort = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        return antwort[0].getNotification();
    }

    /**
     * Beendet eine laufende Bibliotheks- oder Objekt-Abfrage.
     * Sendet bei Bedarf ein Abbruch-Signal an den Server.
     */
    private void abfrageBeenden() throws IOException, PalResultException {
        if (ctx.getPal() == null) {
            throw new IllegalStateException("connection to ndv server not available");
        }
        if (this.abfrageArt == KEINE_ABFRAGE) {
            throw new IllegalStateException("retrieval is not active");
        }

        if (this.aktuellerBenachrichtigungscode == 6) {
            PalTypeNotify abbruch = new PalTypeNotify(5);
            ctx.getPal().add((IPalType) abbruch);
            ctx.getPal().commit();
            IPalTypeNotify[] antwort = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
            PalResultException fehler = ctx.getResultException();
            if (fehler != null) throw fehler;
            antwort[0].getNotification();
        }

        this.aktuellerBenachrichtigungscode = 0;
        this.abfrageArt = KEINE_ABFRAGE;
        this.duplikateMoeglich = false;
        this.bereitsGeliefert.clear();
    }

    /**
     * Prueft ob im Mainframe-Modus Duplikate moeglich sind
     * (Semikolon-getrennte Filter liefern Ergebnisse aus mehreren Bibliotheks-Dateien).
     */
    private boolean duplikatePruefen(String filter) {
        return ctx.getPalProperties() != null
                && ctx.getPalProperties().getNdvType() == 1
                && filter.contains(";");
    }
}
