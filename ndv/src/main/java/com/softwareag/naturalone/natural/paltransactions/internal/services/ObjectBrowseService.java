package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

/**
 * Objekt-Browsing und -Suche:
 * getObjectsFirst/Next, getNumberOfObjects, exists, getObjectByLongName, getObjectByName.
 */
public class ObjectBrowseService {

    private static final IPalTypeObject[] LEERES_OBJEKT_ARRAY = new IPalTypeObject[0];
    private static final int OBJEKT_ABFRAGE = 2;

    private final PalSessionContext ctx;

    /** Aktueller Benachrichtigungscode (0=nicht aktiv, 6=weiter, 7=fertig) */
    private int aktuellerBenachrichtigungscode = 0;

    /** Ob Duplikate moeglich (Mainframe mit Semikolon-Filter) */
    private boolean duplikateMoeglich = false;

    /** Bereits gelieferte Objektnamen zur Duplikat-Erkennung */
    private Set<String> bereitsGeliefert = new TreeSet<>();

    public ObjectBrowseService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ═══════════════════════════════════════════════
    //  getObjectsFirst (5-Parameter)
    // ═══════════════════════════════════════════════

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library,
                                            String filter, int kind, int type)
            throws IOException, PalResultException {

        ctx.requirePal();

        // Validierung
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (filter.length() == 0) throw new IllegalArgumentException("filter parameter must not be empty");
        if (kind < 0) throw new IllegalArgumentException("kind must be one of the ids defined inside utility class 'sag.pal.ObjectKind'");

        // kind-Validierung: nur SOURCE(1), GP(2), SOURCE_OR_GP(3), ERRMSG(64) erlaubt
        if (kind != 1 && kind != 2 && kind != 3 && kind != 64) {
            throw new IllegalArgumentException("kind  must be ObjectKind.GP or ObjectKind.SOURCE_OR_GP or ObjectKind.SOURCE");
        }

        // Bei ERRMSG (64) darf type nur NONE (0 oder 8) sein
        if (kind == 64 && type != 0 && type != 8) {
            throw new IllegalArgumentException("kind 'ObjectKind.ERRMSG' only allowed inconjunction with type 'ObjectType.NONE'");
        }

        // type-Validierung
        if (type != 0 && type != 8) {
            Hashtable validTypes = ObjectType.getInstanceIdExtension();
            if (!validTypes.containsKey(Integer.valueOf(type))) {
                throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");
            }
        }

        // leerer Filter bei type=NONE(8) erlaubt, sonst muss library gefuellt sein
        if (library.length() == 0 && type != 8) {
            throw new IllegalArgumentException("library parameter must not be empty");
        }

        PalTrace.header("getObjectsFirst");
        this.duplikateMoeglich = duplikatePruefen(filter);
        this.bereitsGeliefert.clear();

        // Mapping: kind + type => operationFlags + internalKind
        int operationFlags = 3; // Default: SOURCE_OR_GP
        int internerKindFuerObjDesc = kind;

        if (type == 8) {
            // ObjectType.NONE: Alle Typen, Art aus kind ableiten
            if (kind == 64) {
                // ERRMSG => spezielle Behandlung
                internerKindFuerObjDesc = 0;
                operationFlags = 32768; // spezial-Flag fuer Error-Messages
            } else if (sysFile.getKind() == 6) {
                // StepLib -> leerer Bibliotheksname fuer Open Systems
                library = ctx.isOpenSystemsServer() ? "SYSTEM" : "";
                operationFlags = 23; // spezieller Ops-Flag
            }
        } else if (kind == 64) {
            internerKindFuerObjDesc = 0;
            operationFlags = 32768;
        }

        // PAL-Transaktion senden
        IPalTypeObject[] ergebnis = sendeObjektAbfrage(
                operationFlags, sysFile, library, filter, internerKindFuerObjDesc, type);

        if (ergebnis == null) {
            this.aktuellerBenachrichtigungscode = 0;
            return LEERES_OBJEKT_ARRAY;
        }
        return ergebnis;
    }

    // ═══════════════════════════════════════════════
    //  getObjectsFirst (3-Parameter: ohne filter/type)
    // ═══════════════════════════════════════════════

    public IPalTypeObject[] getObjectsFirst(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        return getObjectsFirst(sysFile, library, "*", kind, 0);
    }

    // ═══════════════════════════════════════════════
    //  getObjectsNext
    // ═══════════════════════════════════════════════

    public IPalTypeObject[] getObjectsNext() throws IOException, PalResultException {
        IPalTypeObject[] ergebnis = null;

        ctx.requirePal();
        if (this.aktuellerBenachrichtigungscode == 0) {
            throw new IllegalStateException("getObjectsFirst must be called first");
        }
        if (ctx.getRetrievalKind() != OBJEKT_ABFRAGE) {
            throw new IllegalStateException("getObjectsNext cannot be used without a getObjectsFirst call");
        }

        PalTrace.header("getObjectsNext");
        if (this.aktuellerBenachrichtigungscode == 7) {
            // Keine weiteren Daten
            ergebnis = null;
        } else {
            ergebnis = naechsterObjektBlock(true);
        }

        if (ergebnis == null) {
            this.aktuellerBenachrichtigungscode = 0;
            return LEERES_OBJEKT_ARRAY;
        }
        return ergebnis != null ? ergebnis : LEERES_OBJEKT_ARRAY;
    }

    // ═══════════════════════════════════════════════
    //  getNumberOfObjects (5-Parameter)
    // ═══════════════════════════════════════════════

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library,
                                  String filter, int kind, int type)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (filter == null) throw new IllegalArgumentException("filter must not be null");

        PalTrace.header("getNumberOfObjects");

        // Gleiche Logik wie getObjectsFirst, aber wir lesen nur die Anzahl
        IPalTypeObject[] objekte = getObjectsFirst(sysFile, library, filter, kind, type);
        int anzahl = 0;

        // Alle Bloecke durchgehen und zaehlen
        if (objekte != null) {
            anzahl += objekte.length;
        }
        while (this.aktuellerBenachrichtigungscode != 0 && this.aktuellerBenachrichtigungscode != 7) {
            IPalTypeObject[] naechste = naechsterObjektBlock(true);
            if (naechste != null) {
                anzahl += naechste.length;
            } else {
                break;
            }
        }

        this.aktuellerBenachrichtigungscode = 0;
        return anzahl;
    }

    // ═══════════════════════════════════════════════
    //  getNumberOfObjects (3-Parameter)
    // ═══════════════════════════════════════════════

    public int getNumberOfObjects(IPalTypeSystemFile sysFile, String library, int kind)
            throws IOException, PalResultException {
        return getNumberOfObjects(sysFile, library, "*", kind, 0);
    }

    // ═══════════════════════════════════════════════
    //  exists (4-Parameter)
    // ═══════════════════════════════════════════════

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String library, String name, int type)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (name == null) throw new IllegalArgumentException("name must not be null");

        PalTrace.header("exists");

        ctx.getPal().add((IPalType) new PalTypeOperation(3, 3));
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 6);
        ctx.getPal().add((IPalType) libId);
        PalTypeObjDesc2 objDesc = new PalTypeObjDesc2(type, 3, name);
        ctx.getPal().add((IPalType) objDesc);
        PalTypeNotify notify = new PalTypeNotify(17);
        ctx.getPal().add((IPalType) notify);
        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) {
            if (fehler.getErrorNumber() == 82) {
                return null; // Objekt nicht gefunden
            }
            throw fehler;
        }

        IPalTypeObject[] objekte = (IPalTypeObject[]) ctx.getPal().retrieve(8);
        this.aktuellerBenachrichtigungscode = 0;
        return (objekte != null && objekte.length > 0) ? objekte[0] : null;
    }

    // ═══════════════════════════════════════════════
    //  exists (3-Parameter: ohne library)
    // ═══════════════════════════════════════════════

    public IPalTypeObject exists(IPalTypeSystemFile sysFile, String name, int type)
            throws IOException, PalResultException {
        String library = ctx.isOpenSystemsServer() ? "SYSTEM" : "";
        return exists(sysFile, library, name, type);
    }

    // ═══════════════════════════════════════════════
    //  getObjectByLongName
    // ═══════════════════════════════════════════════

    public ISourceLookupResult getObjectByLongName(IPalTypeSystemFile sysFile, String library,
                                                    String longName, int type, boolean withSource)
            throws IOException, PalResultException {

        return getObjectByNameInternal(sysFile, library, longName, type, withSource, true);
    }

    // ═══════════════════════════════════════════════
    //  getObjectByName
    // ═══════════════════════════════════════════════

    public ISourceLookupResult getObjectByName(IPalTypeSystemFile sysFile, String library,
                                                String name, int type, boolean withSource)
            throws IOException, PalResultException {

        return getObjectByNameInternal(sysFile, library, name, type, withSource, false);
    }

    // ═══════════════════════════════════════════════
    //  Interne Hilfsmethoden
    // ═══════════════════════════════════════════════

    /**
     * Gemeinsame Implementierung fuer getObjectByName / getObjectByLongName.
     */
    private ISourceLookupResult getObjectByNameInternal(IPalTypeSystemFile sysFile, String library,
                                                         String name, int type, boolean withSource,
                                                         boolean isLongName)
            throws IOException, PalResultException {

        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (name == null) throw new IllegalArgumentException("name must not be null");

        PalTrace.header(isLongName ? "getObjectsByLongName" : "getObjectByName");

        // Bestimme den operationFlags-Wert
        int operationFlags = 3;
        if (type == 256) {
            // DDM-Typ: spezieller Lookup
            operationFlags = 3;
        }

        // Sende PAL-Transaktion
        ctx.getPal().add((IPalType) new PalTypeOperation(operationFlags, withSource ? 8 : 0));
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 6);
        ctx.getPal().add((IPalType) libId);
        PalTypeObjDesc2 objDesc = new PalTypeObjDesc2(type, 3, name);
        ctx.getPal().add((IPalType) objDesc);
        PalTypeNotify notify = new PalTypeNotify(17);
        ctx.getPal().add((IPalType) notify);
        ctx.getPal().commit();

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) {
            if (fehler.getErrorNumber() == 82) {
                return null; // Objekt nicht gefunden
            }
            throw fehler;
        }

        IPalTypeObject[] objekte = (IPalTypeObject[]) ctx.getPal().retrieve(8);
        IPalTypeObject gefunden = (objekte != null && objekte.length > 0) ? objekte[0] : null;

        // Bibliotheks-ID vom Server abholen
        String ergebnisLib = library;
        int ergebnisDatabaseId = sysFile.getDatabaseId();
        int ergebnisFileNumber = sysFile.getFileNumber();

        IPalTypeLibId[] libIds = (IPalTypeLibId[]) ctx.getPal().retrieve(6);
        if (libIds != null && libIds.length > 0) {
            ergebnisLib = libIds[0].getLibrary();
            if (libIds[0].getDatabaseId() != 0) {
                ergebnisDatabaseId = libIds[0].getDatabaseId();
            }
            if (libIds[0].getFileNumber() != 0) {
                ergebnisFileNumber = libIds[0].getFileNumber();
            }
        }

        this.aktuellerBenachrichtigungscode = 0;

        return new SourceLookupResult(gefunden, ergebnisLib, ergebnisDatabaseId, ergebnisFileNumber);
    }

    /**
     * Sendet die eigentliche Objekt-Abfrage an den Server und liefert den ersten Block.
     */
    private IPalTypeObject[] sendeObjektAbfrage(int operationFlags, IPalTypeSystemFile sysFile,
                                                 String library, String filter,
                                                 int kind, int type)
            throws IOException, PalResultException {

        ctx.setRetrievalKind(OBJEKT_ABFRAGE);

        // Operation senden
        ctx.getPal().add((IPalType) new PalTypeOperation(operationFlags, kind));

        // LibId senden
        PalTypeLibId libId = new PalTypeLibId(
                sysFile.getDatabaseId(), sysFile.getFileNumber(),
                library, sysFile.getPassword(), sysFile.getCipher(), 6);
        ctx.getPal().add((IPalType) libId);

        // ObjDesc2 senden (Art, Typ, Filter)
        PalTypeObjDesc2 objDesc = new PalTypeObjDesc2(kind, type, filter);
        ctx.getPal().add((IPalType) objDesc);

        // Notify senden: Chunked-Modus anfordern
        PalTypeNotify notify = new PalTypeNotify(17);
        ctx.getPal().add((IPalType) notify);

        ctx.getPal().commit();

        // Fehlerauswertung
        PalResultException fehler = ctx.getResultException();
        if (fehler != null) {
            if (fehler.getErrorNumber() == 82) {
                // Nicht gefunden -> leeres Ergebnis, kein Fehler
                return null;
            }
            throw fehler;
        }

        // Notify + Generic auslesen fuer Chunked-Modus
        IPalTypeNotify[] benachrichtigung = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        IPalTypeGeneric[] generisch = (IPalTypeGeneric[]) ctx.getPal().retrieve(20);
        boolean weitereBloecke = false;
        if (benachrichtigung != null && generisch != null) {
            int anzahl = generisch[0].getData();
            weitereBloecke = anzahl > 0;
        }

        // Ersten Block abholen
        return naechsterObjektBlock(weitereBloecke);
    }

    /**
     * Naechsten Block von Objekten vom Server abholen.
     */
    private IPalTypeObject[] naechsterObjektBlock(boolean weitereBloecke)
            throws IOException, PalResultException {

        if (weitereBloecke) {
            this.aktuellerBenachrichtigungscode = weiterAbfragen();
        }

        PalResultException fehler = ctx.getResultException();
        if (fehler != null) throw fehler;

        IPalTypeObject[] ergebnis = (IPalTypeObject[]) ctx.getPal().retrieve(8);

        // Duplikat-Bereinigung bei Mainframe-Semikolon-Filtern
        if (this.duplikateMoeglich && ergebnis != null) {
            ArrayList<IPalTypeObject> bereinigt = new ArrayList<>();
            for (IPalTypeObject obj : ergebnis) {
                String schluessel;
                if (obj.getType() == 8) {
                    // Typ NONE: LongName als Schluessel
                    schluessel = obj.getLongName();
                } else {
                    schluessel = obj.getName();
                }
                if (schluessel != null && !this.bereitsGeliefert.contains(schluessel)) {
                    bereinigt.add(obj);
                    this.bereitsGeliefert.add(schluessel);
                }
            }
            ergebnis = bereinigt.toArray(new IPalTypeObject[0]);
        }

        return ergebnis;
    }

    /**
     * Sendet eine Fortsetzungs-Benachrichtigung und gibt den Antwort-Code zurueck.
     */
    private int weiterAbfragen() throws IOException, PalResultException {
        PalTypeNotify notify = new PalTypeNotify(4); // CONTINUE
        ctx.getPal().add((IPalType) notify);
        ctx.getPal().commit();
        IPalTypeNotify[] antwort = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        return antwort[0].getNotification();
    }

    /**
     * Prueft ob Duplikate moeglich sind (Mainframe + Semikolon im Filter).
     */
    private boolean duplikatePruefen(String filter) {
        return ctx.getPalProperties() != null
                && ctx.getPalProperties().getNdvType() == 1
                && filter.contains(";");
    }

    // ═══════════════════════════════════════════════
    //  SourceLookupResult (innere Klasse)
    // ═══════════════════════════════════════════════

    /**
     * Ergebnis einer Objektsuche mit zugehoeriger Bibliothek und System-File-IDs.
     */
    private static class SourceLookupResult implements ISourceLookupResult {
        private final IPalTypeObject object;
        private final String library;
        private final int databaseId;
        private final int fileNumber;

        SourceLookupResult(IPalTypeObject object, String library, int databaseId, int fileNumber) {
            this.object = object;
            this.library = library;
            this.databaseId = databaseId;
            this.fileNumber = fileNumber;
        }

        public IPalTypeObject getObject() { return object; }
        public String getLibrary() { return library; }
        public int getDatabaseId() { return databaseId; }
        public int getFileNumber() { return fileNumber; }
    }
}

