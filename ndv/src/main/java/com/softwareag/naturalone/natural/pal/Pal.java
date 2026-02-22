package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Pal {

    // --- Paketstruktur-Abmessungen ---
    private static final int TRANSAKTIONSGROESSE_LAENGE = 12;
    private static final int DATENSATZANZAHL_LAENGE = 12;
    private static final int ENDMARKER_LAENGE = 6;
    private static final int AUFSCHLAG_ERSTER_DATENSATZ = 42;
    private static final int AUFSCHLAG_FOLGE_DATENSATZ = 24;
    private static final int MAX_PAKETGROESSE = 4000;

    // --- Protokoll-Kopfzeile ---
    private static final String PROTOKOLL_SIGNATUR = "NATSPOD";
    private static final int SIGNATUR_LAENGE = 7;
    private static final int PAKETGROESSE_FELDBREITE = 8;
    private static final int EINTRAEGE_FELDBREITE = 3;
    private static final int NUTZDATEN_FELDBREITE = 8;
    private static final int NEUES_KOPFFORMAT_ERKENNUNG = 2;
    private static final int FESTWERT_EINTRAEGE = 1;
    private static final int ALTES_KOPFFORMAT_MARKIERUNG = 1;
    private static final int KOPFZEILEN_LAENGE = 26;
    private static final byte[] QUITTUNG_NAECHSTES_SEGMENT = "NATSPODNEXTCHUNK          ".getBytes();
    private static final byte[] TRENNUNGSSIGNAL = "NATSPODDISCONNECT         ".getBytes();

    // --- Protokoll-Endmarker ---
    private static final int KEIN_AKTIVER_TYP = 65535;
    private static final int PUFFER_VOLL_ZWISCHENSENDUNG = 32001;
    private static final int SEGMENT_FORTSETZUNG = 32002;
    private static final int DATENSATZ_ENDE = 32003;
    private static final int TRANSAKTION_ENDE = 32004;
    private static final int ERSTE_SCHREIBPOSITION = 38;

    // --- Typ-Schlüssel-Tabelle (FQCN) ---
    private static final String PAKET_PRAEFIX = "com.softwareag.naturalone.natural.pal.";
    private static final String[] DATENSATZ_KLASSEN = {
            PAKET_PRAEFIX + "PalTypeEnviron",            // 0
            PAKET_PRAEFIX + "PalTypeConnect",            // 1
            PAKET_PRAEFIX + "PalTypeOperation",          // 2
            PAKET_PRAEFIX + "PalTypeSystemFile",         // 3
            PAKET_PRAEFIX + "PalTypeLibraryStatistics",  // 4
            PAKET_PRAEFIX + "PalTypeLibrary",            // 5
            PAKET_PRAEFIX + "PalTypeLibId",              // 6
            PAKET_PRAEFIX + "PalTypeObjDesc",            // 7
            PAKET_PRAEFIX + "PalTypeObject",             // 8
            PAKET_PRAEFIX + "PalTypeStackCmd",           // 9
            PAKET_PRAEFIX + "PalTypeResult",             // 10
            PAKET_PRAEFIX + "PalTypeResultEx",           // 11
            PAKET_PRAEFIX + "PalTypeSourceCodePage",     // 12
            PAKET_PRAEFIX + "PalTypeStream",             // 13
            PAKET_PRAEFIX + "PalTypeUtility",            // 14
            PAKET_PRAEFIX + "PalTypeSrcDesc",            // 15
            PAKET_PRAEFIX + "PalTypeSrvAppList",         // 16
            PAKET_PRAEFIX + "PalTypeAppId",              // 17
            PAKET_PRAEFIX + "PalTypeCatallDesc",         // 18
            PAKET_PRAEFIX + "PalTypeNotify",             // 19
            PAKET_PRAEFIX + "PalTypeGeneric",            // 20
            PAKET_PRAEFIX + "PalTypeAttrList",           // 21
            PAKET_PRAEFIX + "PalTypeDescrip",            // 22
            PAKET_PRAEFIX + "PalTypeFileId",             // 23
            PAKET_PRAEFIX + "PalTypeStream",             // 24
            PAKET_PRAEFIX + "PalTypeNatParm",            // 25
            PAKET_PRAEFIX + "PalTypeSQLAuthentification",// 26
            PAKET_PRAEFIX + "PalTypeCmdGuard",           // 27
            PAKET_PRAEFIX + "PalTypeSysVar",             // 28
            PAKET_PRAEFIX + "PalTypeObjDesc2",           // 29
            PAKET_PRAEFIX + "PalTypeLibId",              // 30
            PAKET_PRAEFIX + "PalTypeFindInfo",           // 31
            PAKET_PRAEFIX + "PalTypeFindResult",         // 32
            PAKET_PRAEFIX + "PalTypeFindStatus",         // 33
            PAKET_PRAEFIX + "PalTypeDbgStackFrame",      // 34
            PAKET_PRAEFIX + "PalTypeDbgStatus",          // 35
            PAKET_PRAEFIX + "PalTypeDbgVarContainer",    // 36
            PAKET_PRAEFIX + "PalTypeDbgSyt",             // 37
            PAKET_PRAEFIX + "PalTypeDbgVarDesc",         // 38
            PAKET_PRAEFIX + "PalTypeDbgVarValue",        // 39
            PAKET_PRAEFIX + "PalTypeDbgSpy",             // 40
            PAKET_PRAEFIX + "PalTypeDbgVarDescHdl",      // 41
            PAKET_PRAEFIX + "PalTypeSourceUnicode",      // 42
            PAKET_PRAEFIX + "PalTypeVarValueHdl",        // 43
            PAKET_PRAEFIX + "PalTypeProxyConnect",       // 44
            PAKET_PRAEFIX + "PalTypeCP",                 // 45
            PAKET_PRAEFIX + "PalTypeLibId",              // 46
            PAKET_PRAEFIX + "PalTypeSuppressLine",       // 47
            PAKET_PRAEFIX + "PalTypeSourceCP",           // 48
            PAKET_PRAEFIX + "PalTypeDbmsInfo",           // 49
            PAKET_PRAEFIX + "PalTypeClientConfig",       // 50
            PAKET_PRAEFIX + "PalTypeEnviron1",           // 51
            PAKET_PRAEFIX + "PalTypeDevEnv",             // 52
            PAKET_PRAEFIX + "PalTypeDbgNatStack",        // 53
            PAKET_PRAEFIX + "PalTypeTimeStamp",          // 54
            PAKET_PRAEFIX + "PalTypeDbgaRecord",         // 55
            PAKET_PRAEFIX + "PalTypeMonitorInfo"         // 56
    };

    // --- Instanz-Felder ---
    private boolean verbindungVerloren = false;
    private IPalTimeoutHandler wartezeitRueckruf;
    private int socketWartezeit;
    private volatile Exception empfangsThreadFehler;
    private String serverAdresse = "";
    private String serverPort = null;
    private int aktuellerRecordTyp;
    private Set gepufferteTypSchluessel;
    private boolean empfangsphaseAktiv;
    private boolean neuesKopfFormat;
    private int schreibPosition;
    private byte[] sendePuffer;
    private byte[] empfangsPuffer;
    private int datensaetzeImBlock;
    @SuppressWarnings("unchecked")
    private ArrayList[] empfangsZwischenspeicher;
    private int anzahlNachtragsPosition;
    private int gesendeteDatensaetze;
    private boolean naechsterBlockNoetig;
    private int protokollVersion;
    private String sitzungsKennung = "";
    private String benutzerKennung = "";
    private Socket tcpVerbindung;
    private DataOutputStream sendeDatenStrom;
    private DataInputStream empfangsDatenStrom;
    private Uebergabebereich uebergabeBereich;
    private int serverTyp;
    private String serverZeichensatz;
    private boolean ersterLeseversuch = true;

    // =================================================================
    //  Konstruktor
    // =================================================================
    public Pal(int wartezeitInSekunden, IPalTimeoutHandler zeitRueckruf) {
        this.socketWartezeit = wartezeitInSekunden * 1000;
        this.wartezeitRueckruf = zeitRueckruf;
    }

    // =================================================================
    //  init()
    // =================================================================
    public void init() {
        this.aktuellerRecordTyp = KEIN_AKTIVER_TYP;
        this.gepufferteTypSchluessel = new HashSet();
        this.empfangsZwischenspeicher = new ArrayList[57];
        this.sendePuffer = new byte[MAX_PAKETGROESSE];
        this.schreibPosition = ERSTE_SCHREIBPOSITION;
        this.uebergabeBereich = new Uebergabebereich();
        this.naechsterBlockNoetig = true;
        this.empfangsThreadFehler = null;
        this.empfangsphaseAktiv = false;
    }

    // =================================================================
    //  connect(host, port)
    // =================================================================
    public void connect(String zielAdresse, String zielPort) throws IOException, UnknownHostException {
        init();

        int portNummer;
        try {
            portNummer = Integer.valueOf(zielPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port is invalid", e);
        }

        byte[] ipBytes = PalTools.getIPBytes(zielAdresse);
        if (ipBytes != null) {
            this.tcpVerbindung = new Socket(InetAddress.getByAddress(ipBytes), portNummer);
        } else {
            this.tcpVerbindung = new Socket(zielAdresse, portNummer);
        }

        if (this.socketWartezeit != 0) {
            this.tcpVerbindung.setSoTimeout(this.socketWartezeit);
        }
        this.tcpVerbindung.setSoLinger(true, 1);

        this.sendeDatenStrom = new DataOutputStream(this.tcpVerbindung.getOutputStream());
        this.empfangsDatenStrom = new DataInputStream(this.tcpVerbindung.getInputStream());

        this.ersterLeseversuch = true;

        Thread empfangsThread = new Thread("PalReceiveThread") {
            public void run() {
                empfangsSchleife();
            }
        };
        empfangsThread.setDaemon(true);
        empfangsThread.start();

        this.serverPort = zielPort;
        this.serverAdresse = zielAdresse;
    }

    // =================================================================
    //  disconnect()
    // =================================================================
    public void disconnect() throws IOException {
        if (this.tcpVerbindung == null) {
            return;
        }
        if (this.protokollVersion >= 47) {
            try {
                this.sendeDatenStrom.write(TRENNUNGSSIGNAL);
                PalTrace.buffer(TRENNUNGSSIGNAL, false, this.sitzungsKennung);
            } catch (Exception ignoriert) {
            }
        }
        closeSocket();
    }

    // =================================================================
    //  closeSocket()
    // =================================================================
    public void closeSocket() throws IOException {
        if (this.tcpVerbindung != null) {
            this.tcpVerbindung.close();
            this.tcpVerbindung = null;
        }
    }

    // =================================================================
    //  add(IPalType) - Einzelner Datensatz
    // =================================================================
    public void add(IPalType datensatz) throws IOException {
        empfangsphaseDrainieren();

        if (datensatz instanceof IPalTypeOperation) {
            ((IPalTypeOperation) datensatz).setClientId(this.sitzungsKennung);
            ((IPalTypeOperation) datensatz).setUserId(this.benutzerKennung);
        }

        if (!this.gepufferteTypSchluessel.add(datensatz.get())) {
            throw new IllegalStateException(
                    "The last PAL transaction was not commited (Type " + datensatz.get() + " is still in queue)");
        }

        PalTrace.type(DATENSATZ_KLASSEN[datensatz.get()], false);

        datensatz.setRecord(new ArrayList());
        datensatz.setServerCodePage(this.serverZeichensatz);
        datensatz.serialize();

        int typSchluessel = datensatz.get();
        ArrayList nutzdatenListe = datensatz.getRecord();
        boolean ersterTypVorkommen = true;
        int verbleibendeGroesse = nutzdatenListe.size();
        int leseOffset = 0;
        byte[] nutzdatenBytes = byteListeZuArray(nutzdatenListe);

        while (true) {
            int aufschlag = ersterTypVorkommen ? AUFSCHLAG_ERSTER_DATENSATZ : AUFSCHLAG_FOLGE_DATENSATZ;

            if (this.schreibPosition + aufschlag + verbleibendeGroesse < this.sendePuffer.length) {
                if (ersterTypVorkommen) {
                    typKopfSchreiben(typSchluessel);
                }
                this.schreibPosition += ganzzahlInPuffer(verbleibendeGroesse, this.sendePuffer, this.schreibPosition);
                System.arraycopy(nutzdatenBytes, leseOffset, this.sendePuffer, this.schreibPosition, verbleibendeGroesse);
                this.schreibPosition += verbleibendeGroesse;
                this.schreibPosition += ganzzahlInPuffer(DATENSATZ_ENDE, this.sendePuffer, this.schreibPosition);
                this.gesendeteDatensaetze++;
                break;
            }

            int verfuegbarerPlatz = this.sendePuffer.length - (this.schreibPosition + aufschlag);

            if (verfuegbarerPlatz > 0) {
                if (ersterTypVorkommen) {
                    typKopfSchreiben(typSchluessel);
                }
                this.schreibPosition += ganzzahlInPuffer(verfuegbarerPlatz, this.sendePuffer, this.schreibPosition);
                System.arraycopy(nutzdatenBytes, leseOffset, this.sendePuffer, this.schreibPosition, verfuegbarerPlatz);
                this.schreibPosition += verfuegbarerPlatz;
                leseOffset += verfuegbarerPlatz;
                verbleibendeGroesse -= verfuegbarerPlatz;
                this.gesendeteDatensaetze++;

                if (verbleibendeGroesse == 0) {
                    this.schreibPosition += ganzzahlInPuffer(DATENSATZ_ENDE, this.sendePuffer, this.schreibPosition);
                    break;
                } else {
                    this.schreibPosition += ganzzahlInPuffer(SEGMENT_FORTSETZUNG, this.sendePuffer, this.schreibPosition);
                    paketBauenUndSenden();
                    if (this.protokollVersion >= 17) {
                        aufSegmentQuittungWarten();
                    }
                    ersterTypVorkommen = true;
                    continue;
                }
            }

            this.schreibPosition += ganzzahlInPuffer(PUFFER_VOLL_ZWISCHENSENDUNG, this.sendePuffer, this.schreibPosition);
            paketBauenUndSenden();
            if (this.protokollVersion >= 17) {
                aufSegmentQuittungWarten();
            }
            ersterTypVorkommen = true;
        }

        gespeichertenFehlerWerfen();
        this.aktuellerRecordTyp = datensatz.get();
    }

    // =================================================================
    //  add(IPalType[]) - Datensatz-Array
    // =================================================================
    public void add(IPalType[] datensaetze) throws IOException {
        empfangsphaseDrainieren();

        int erstTypSchluessel = datensaetze[0].get();
        if (!this.gepufferteTypSchluessel.add(erstTypSchluessel)) {
            throw new IllegalStateException(
                    "The last PAL transaction was not commited (Type " + erstTypSchluessel + " is still in queue)");
        }

        PalTrace.type(DATENSATZ_KLASSEN[erstTypSchluessel], false);

        for (int i = 0; i < datensaetze.length; i++) {
            datensaetze[i].setRecord(new ArrayList());
            datensaetze[i].serialize();

            int einzelTypSchluessel = datensaetze[i].get();
            ArrayList nutzdatenListe = datensaetze[i].getRecord();
            boolean ersterTypVorkommen = (i == 0 && einzelTypSchluessel != this.aktuellerRecordTyp);
            int verbleibendeGroesse = nutzdatenListe.size();
            int leseOffset = 0;
            byte[] nutzdatenBytes = byteListeZuArray(nutzdatenListe);

            while (true) {
                int aufschlag = ersterTypVorkommen ? AUFSCHLAG_ERSTER_DATENSATZ : AUFSCHLAG_FOLGE_DATENSATZ;

                if (this.schreibPosition + aufschlag + verbleibendeGroesse < this.sendePuffer.length) {
                    if (ersterTypVorkommen) {
                        typKopfSchreiben(einzelTypSchluessel);
                    }
                    this.schreibPosition += ganzzahlInPuffer(verbleibendeGroesse, this.sendePuffer, this.schreibPosition);
                    System.arraycopy(nutzdatenBytes, leseOffset, this.sendePuffer, this.schreibPosition, verbleibendeGroesse);
                    this.schreibPosition += verbleibendeGroesse;
                    this.schreibPosition += ganzzahlInPuffer(DATENSATZ_ENDE, this.sendePuffer, this.schreibPosition);
                    this.gesendeteDatensaetze++;
                    break;
                }

                int verfuegbarerPlatz = this.sendePuffer.length - (this.schreibPosition + aufschlag);

                if (verfuegbarerPlatz > 0) {
                    if (ersterTypVorkommen) {
                        typKopfSchreiben(einzelTypSchluessel);
                    }
                    this.schreibPosition += ganzzahlInPuffer(verfuegbarerPlatz, this.sendePuffer, this.schreibPosition);
                    System.arraycopy(nutzdatenBytes, leseOffset, this.sendePuffer, this.schreibPosition, verfuegbarerPlatz);
                    this.schreibPosition += verfuegbarerPlatz;
                    leseOffset += verfuegbarerPlatz;
                    verbleibendeGroesse -= verfuegbarerPlatz;
                    this.gesendeteDatensaetze++;

                    if (verbleibendeGroesse == 0) {
                        this.schreibPosition += ganzzahlInPuffer(DATENSATZ_ENDE, this.sendePuffer, this.schreibPosition);
                        break;
                    } else {
                        this.schreibPosition += ganzzahlInPuffer(SEGMENT_FORTSETZUNG, this.sendePuffer, this.schreibPosition);
                        paketBauenUndSenden();
                        if (this.protokollVersion >= 17) {
                            aufSegmentQuittungWarten();
                        }
                        ersterTypVorkommen = true;
                        continue;
                    }
                }

                this.schreibPosition += ganzzahlInPuffer(PUFFER_VOLL_ZWISCHENSENDUNG, this.sendePuffer, this.schreibPosition);
                paketBauenUndSenden();
                if (this.protokollVersion >= 17) {
                    aufSegmentQuittungWarten();
                }
                ersterTypVorkommen = true;
            }
        }

        this.aktuellerRecordTyp = erstTypSchluessel;
    }

    // =================================================================
    //  commit()
    // =================================================================
    public void commit() throws IOException {
        this.gepufferteTypSchluessel.clear();
        this.aktuellerRecordTyp = KEIN_AKTIVER_TYP;
        PalTrace.text("\r\n");

        int nutzdatenGroesse = this.schreibPosition - KOPFZEILEN_LAENGE;

        int kopfPosition = KOPFZEILEN_LAENGE;
        Arrays.fill(this.sendePuffer, kopfPosition, kopfPosition + TRANSAKTIONSGROESSE_LAENGE, (byte) 0);
        ganzzahlInPuffer(nutzdatenGroesse, this.sendePuffer, kopfPosition);

        this.schreibPosition += ganzzahlInPuffer(TRANSAKTION_ENDE, this.sendePuffer, this.schreibPosition);

        if (this.anzahlNachtragsPosition != 0) {
            datensatzAnzahlNachtragen();
        }

        kopfzeileAufbauen();

        byte[] paket = new byte[this.schreibPosition];
        System.arraycopy(this.sendePuffer, 0, paket, 0, this.schreibPosition);
        this.sendeDatenStrom.write(paket);
        PalTrace.buffer(paket, false, this.sitzungsKennung);

        this.schreibPosition = ERSTE_SCHREIBPOSITION;
        Arrays.fill(this.sendePuffer, (byte) 0);
    }

    // =================================================================
    //  retrieve(typSchluessel)
    // =================================================================
    public IPalType[] retrieve(int gesuchterTyp) throws IOException {
        this.empfangsphaseAktiv = true;

        if (gesuchterTyp < 0 || gesuchterTyp > 56) {
            throw new IllegalArgumentException(
                    "illegal PalType Id: " + gesuchterTyp + ", please refer to 'PalTypeId'class)");
        }

        if (this.aktuellerRecordTyp != TRANSAKTION_ENDE && this.empfangsZwischenspeicher[gesuchterTyp] == null) {
            if (this.naechsterBlockNoetig) {
                empfangsKopfVerarbeiten();
            }

            while (this.aktuellerRecordTyp != gesuchterTyp && this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
                datensaetzeEinlesen(gesuchterTyp);
                if (this.empfangsThreadFehler != null) break;
                if (this.aktuellerRecordTyp == TRANSAKTION_ENDE) break;
                empfangsKopfVerarbeiten();
                if (this.empfangsThreadFehler != null) break;
            }

            gespeichertenFehlerWerfen();

            if (this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
                datensaetzeEinlesen(gesuchterTyp);
                gespeichertenFehlerWerfen();
            }
        }

        return typArrayErzeugen(gesuchterTyp);
    }

    // =================================================================
    //  Getter und Setter
    // =================================================================
    public String getSessionId() {
        return this.sitzungsKennung;
    }

    public void setSessionId(String wert) {
        this.sitzungsKennung = wert;
    }

    public void setUserId(String wert) {
        this.benutzerKennung = wert;
    }

    public void setPalVersion(int wert) {
        this.protokollVersion = wert;
    }

    public void setNdvType(int wert) {
        this.serverTyp = wert;
    }

    public void setServerCodePage(String wert) {
        this.serverZeichensatz = wert;
    }

    public boolean isConnectionLost() {
        return this.verbindungVerloren;
    }

    public void setConnectionLost(boolean wert) {
        this.verbindungVerloren = wert;
    }

    public void setPalTimeoutHandler(IPalTimeoutHandler rueckruf) {
        this.wartezeitRueckruf = rueckruf;
    }

    public String toString() {
        return "Pal connection to " + this.serverAdresse + "-" + this.serverPort;
    }

    int getPalVersion() {
        return this.protokollVersion;
    }

    int getNdvType() {
        return this.serverTyp;
    }

    // =================================================================
    //  Private Hilfsmethoden
    // =================================================================

    /** Ganzzahl als nullterminierten Text in den Puffer schreiben. */
    private static int ganzzahlInPuffer(int wert, byte[] ziel, int position) {
        String s = Integer.toString(wert);
        byte[] b = s.getBytes();
        System.arraycopy(b, 0, ziel, position, b.length);
        ziel[position + b.length] = 0;
        return b.length + 1;
    }

    /** Nullterminierten Text als Ganzzahl aus dem Puffer lesen. */
    private static int ganzzahlAusPuffer(byte[] quelle, int position) {
        int i = position;
        while (quelle[i] != 0) {
            i++;
        }
        return Integer.valueOf(new String(quelle, position, i - position));
    }

    /** Ganzzahl aus Empfangspuffer lesen und Schreibposition vorrücken. */
    private int ganzzahlLesenUndWeiter() {
        int start = this.schreibPosition;
        while (this.empfangsPuffer[this.schreibPosition] != 0) {
            this.schreibPosition++;
        }
        int wert = Integer.valueOf(new String(this.empfangsPuffer, start, this.schreibPosition - start));
        this.schreibPosition++;
        return wert;
    }

    /** Byte-Liste in Byte-Array konvertieren. */
    private static byte[] byteListeZuArray(ArrayList liste) {
        byte[] ergebnis = new byte[liste.size()];
        for (int i = 0; i < liste.size(); i++) {
            ergebnis[i] = (Byte) liste.get(i);
        }
        return ergebnis;
    }

    /** Ersten Typ-Kopf schreiben (Typ-Schlüssel + Platzhalter für Datensatz-Anzahl). */
    private void typKopfSchreiben(int typSchluessel) {
        if (this.anzahlNachtragsPosition != 0) {
            datensatzAnzahlNachtragen();
        }
        this.schreibPosition += ganzzahlInPuffer(typSchluessel, this.sendePuffer, this.schreibPosition);
        this.anzahlNachtragsPosition = this.schreibPosition;
        Arrays.fill(this.sendePuffer, this.anzahlNachtragsPosition,
                this.anzahlNachtragsPosition + DATENSATZANZAHL_LAENGE, (byte) 0x20);
        this.schreibPosition += DATENSATZANZAHL_LAENGE;
        this.gesendeteDatensaetze = 0;
    }

    /** Datensatz-Anzahl an der reservierten Position nachtragen. */
    private void datensatzAnzahlNachtragen() {
        ganzzahlInPuffer(this.gesendeteDatensaetze, this.sendePuffer, this.anzahlNachtragsPosition);
    }

    /** Sendepuffer zu einem Paket zusammenbauen und senden. */
    private void paketBauenUndSenden() throws IOException {
        int nutzdatenGroesse = this.schreibPosition - KOPFZEILEN_LAENGE;

        int kopfPosition = KOPFZEILEN_LAENGE;
        Arrays.fill(this.sendePuffer, kopfPosition, kopfPosition + TRANSAKTIONSGROESSE_LAENGE, (byte) 0);
        ganzzahlInPuffer(nutzdatenGroesse, this.sendePuffer, kopfPosition);

        if (this.anzahlNachtragsPosition != 0) {
            datensatzAnzahlNachtragen();
        }

        kopfzeileAufbauen();

        byte[] paket = new byte[this.schreibPosition];
        System.arraycopy(this.sendePuffer, 0, paket, 0, this.schreibPosition);
        this.sendeDatenStrom.write(paket);
        PalTrace.buffer(paket, false, this.sitzungsKennung);

        this.schreibPosition = ERSTE_SCHREIBPOSITION;
        Arrays.fill(this.sendePuffer, (byte) 0);
        this.anzahlNachtragsPosition = 0;
        this.gesendeteDatensaetze = 0;
    }

    /** Protokoll-Kopfzeile (26 Bytes) am Anfang des Sendepuffers aufbauen. */
    private void kopfzeileAufbauen() {
        byte[] signaturBytes = PROTOKOLL_SIGNATUR.getBytes();
        int pos = 0;

        System.arraycopy(signaturBytes, 0, this.sendePuffer, 0, SIGNATUR_LAENGE);
        pos = SIGNATUR_LAENGE;

        Arrays.fill(this.sendePuffer, pos, pos + PAKETGROESSE_FELDBREITE, (byte) 0);
        ganzzahlInPuffer(KOPFZEILEN_LAENGE, this.sendePuffer, pos);
        pos += PAKETGROESSE_FELDBREITE;

        Arrays.fill(this.sendePuffer, pos, pos + EINTRAEGE_FELDBREITE, (byte) 0);
        ganzzahlInPuffer(FESTWERT_EINTRAEGE, this.sendePuffer, pos);
        pos += EINTRAEGE_FELDBREITE;

        Arrays.fill(this.sendePuffer, pos, pos + NUTZDATEN_FELDBREITE, (byte) 0);
        int kopfNutzdatenLaenge = this.schreibPosition - ENDMARKER_LAENGE;
        ganzzahlInPuffer(kopfNutzdatenLaenge, this.sendePuffer, pos);
        if (!this.neuesKopfFormat) {
            this.sendePuffer[pos + NUTZDATEN_FELDBREITE - 1] = ALTES_KOPFFORMAT_MARKIERUNG;
        }
    }

    /** Auf Segment-Quittung vom Server warten. */
    private void aufSegmentQuittungWarten() throws IOException {
        this.empfangsPuffer = this.uebergabeBereich.abholen();
        if (this.empfangsThreadFehler != null) return;
        PalTrace.buffer(this.empfangsPuffer, true, this.sitzungsKennung);
        if (!Arrays.equals(this.empfangsPuffer, QUITTUNG_NAECHSTES_SEGMENT)) {
            throw new IOException("Internal Error: server deliverd wrong data");
        }
    }

    /** Empfangsphase drainieren — verbleibende Server-Daten bis Transaktions-Ende lesen. */
    private void empfangsphaseDrainieren() throws IOException {
        if (this.empfangsphaseAktiv) {
            if (this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
                if (this.naechsterBlockNoetig) {
                    empfangsKopfVerarbeiten();
                }

                while (this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
                    datensaetzeEinlesen(TRANSAKTION_ENDE);
                    if (this.empfangsThreadFehler != null) break;
                    if (this.aktuellerRecordTyp == TRANSAKTION_ENDE) break;
                    empfangsKopfVerarbeiten();
                    if (this.empfangsThreadFehler != null) break;
                }

                if (this.empfangsThreadFehler != null) {
                    if (this.empfangsThreadFehler instanceof IOException)
                        throw (IOException) this.empfangsThreadFehler;
                    if (this.empfangsThreadFehler instanceof PalTimeoutException)
                        throw (PalTimeoutException) this.empfangsThreadFehler;
                }
            }

            Arrays.fill(this.empfangsZwischenspeicher, null);
            this.empfangsphaseAktiv = false;
            this.schreibPosition = ERSTE_SCHREIBPOSITION;
            this.anzahlNachtragsPosition = 0;
            this.gesendeteDatensaetze = 0;
            this.naechsterBlockNoetig = true;
            Arrays.fill(this.sendePuffer, (byte) 0);
            PalTrace.text("\r\n");
        }
    }

    /** Empfangene Kopfzeile inline verarbeiten — Typ-Schlüssel und Datensatz-Anzahl parsen. */
    private void empfangsKopfVerarbeiten() throws IOException {
        if (this.naechsterBlockNoetig) {
            this.datensaetzeImBlock = 0;
            this.empfangsPuffer = this.uebergabeBereich.abholen();
            if (this.empfangsThreadFehler != null) return;
            PalTrace.buffer(this.empfangsPuffer, true, this.getSessionId());
            ganzzahlAusPuffer(this.empfangsPuffer, 0);
            this.schreibPosition = TRANSAKTIONSGROESSE_LAENGE;
            this.naechsterBlockNoetig = false;
        }
        this.aktuellerRecordTyp = ganzzahlLesenUndWeiter();

        if (this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
            this.datensaetzeImBlock = ganzzahlAusPuffer(this.empfangsPuffer, this.schreibPosition);
            this.schreibPosition += DATENSATZANZAHL_LAENGE;
        }
    }

    /** Gespeicherten Empfangs-Thread-Fehler weiterwerfen. */
    private void gespeichertenFehlerWerfen() throws IOException {
        if (this.empfangsThreadFehler != null) {
            if (this.empfangsThreadFehler instanceof IOException)
                throw (IOException) this.empfangsThreadFehler;
            if (this.empfangsThreadFehler instanceof PalTimeoutException)
                throw (PalTimeoutException) this.empfangsThreadFehler;
        }
    }

    // =================================================================
    //  Datensätze einlesen und deserialisieren
    // =================================================================

    /** Datensätze des aktuellen Typs einlesen, deserialisieren und zwischenspeichern. */
    private void datensaetzeEinlesen(int zielTyp) throws IOException {
        if (this.empfangsZwischenspeicher[this.aktuellerRecordTyp] == null) {
            this.empfangsZwischenspeicher[this.aktuellerRecordTyp] = new ArrayList();
        }
        ArrayList ablage = this.empfangsZwischenspeicher[this.aktuellerRecordTyp];

        for (int i = 0; i < this.datensaetzeImBlock; i++) {
            ArrayList rohDaten = new ArrayList();
            einzelDatensatzLesen(rohDaten);
            if (this.empfangsThreadFehler != null) return;

            IPalType instanz = datensatzInstanzErzeugen(this.aktuellerRecordTyp);
            if (instanz != null) {
                instanz.setRecord(rohDaten);
                instanz.setPalVers(this.protokollVersion);
                instanz.setNdvType(this.serverTyp);
                instanz.setServerCodePage(this.serverZeichensatz);
                instanz.restore();
                ablage.add(instanz);
            } else {
                ablage.add(rohDaten);
            }
        }

        this.naechsterBlockNoetig = true;
    }

    /** Datensatz-Instanz für den gegebenen Typ-Schlüssel erzeugen. */
    private IPalType datensatzInstanzErzeugen(int typSchluessel) {
        switch (typSchluessel) {
            case 0:  return new PalTypeEnviron();
            case 1:  return new PalTypeConnect(null, null, null);
            case 3:  return new PalTypeSystemFile();
            case 4:  return new PalTypeLibraryStatistics();
            case 5:  return new PalTypeLibrary();
            case 6:
            case 30: return new PalTypeLibId();
            case 8:  return new PalTypeObject();
            case 10: return new PalTypeResult();
            case 11: return new PalTypeResultEx();
            case 12: return new PalTypeSourceCodePage();
            case 13: return new PalTypeStream();
            case 14: return new PalTypeUtility();
            case 15: return new PalTypeSrcDesc();
            case 19: return new PalTypeNotify();
            case 20: return new PalTypeGeneric();
            case 25: return new PalTypeNatParm();
            case 26: return new PalTypeSQLAuthentification();
            case 27: return new PalTypeCmdGuard();
            case 28: return new PalTypeSysVar();
            case 34: return new PalTypeDbgStackFrame();
            case 35: return new PalTypeDbgStatus();
            case 36: return new PalTypeDbgVarContainer();
            case 37: return new PalTypeDbgSyt();
            case 38: return new PalTypeDbgVarDesc();
            case 39: return new PalTypeDbgVarValue();
            case 40: return new PalTypeDbgSpy();
            case 42: return new PalTypeSourceUnicode();
            case 45: return new PalTypeCP();
            case 48: return new PalTypeSourceCP();
            case 49: return new PalTypeDbmsInfo();
            case 50: return new PalTypeClientConfig();
            case 52: return new PalTypeDevEnv();
            case 53: return new PalTypeDbgNatStack();
            case 54: return new PalTypeTimeStamp();
            case 55: return new PalTypeDbgaRecord();
            default: return null;
        }
    }

    /** Einzelnen Datensatz einlesen — ggf. über mehrere Segmente zusammensetzen. */
    private void einzelDatensatzLesen(ArrayList rohDaten) throws IOException {
        int datensatzLaenge = ganzzahlLesenUndWeiter();
        int datenStart = this.schreibPosition;
        int datenEnde = datenStart + datensatzLaenge;

        int endMarkierung = ganzzahlAusPuffer(this.empfangsPuffer, datenEnde);
        this.aktuellerRecordTyp = endMarkierung;

        while (this.aktuellerRecordTyp == SEGMENT_FORTSETZUNG) {
            for (int j = datenStart; j < datenStart + datensatzLaenge; j++) {
                rohDaten.add(this.empfangsPuffer[j]);
            }

            if (this.protokollVersion >= 17) {
                this.sendeDatenStrom.write(QUITTUNG_NAECHSTES_SEGMENT);
                PalTrace.buffer(QUITTUNG_NAECHSTES_SEGMENT, false, this.sitzungsKennung);
            }

            this.naechsterBlockNoetig = true;
            this.datensaetzeImBlock = 0;
            this.empfangsPuffer = this.uebergabeBereich.abholen();
            if (this.empfangsThreadFehler != null) return;
            PalTrace.buffer(this.empfangsPuffer, true, this.getSessionId());
            ganzzahlAusPuffer(this.empfangsPuffer, 0);
            this.schreibPosition = TRANSAKTIONSGROESSE_LAENGE;
            this.naechsterBlockNoetig = false;

            this.aktuellerRecordTyp = ganzzahlLesenUndWeiter();
            if (this.aktuellerRecordTyp != TRANSAKTION_ENDE) {
                this.datensaetzeImBlock = ganzzahlAusPuffer(this.empfangsPuffer, this.schreibPosition);
                this.schreibPosition += DATENSATZANZAHL_LAENGE;
            }

            if (this.empfangsThreadFehler != null) return;

            datensatzLaenge = ganzzahlLesenUndWeiter();
            datenStart = this.schreibPosition;
            datenEnde = datenStart + datensatzLaenge;

            endMarkierung = ganzzahlAusPuffer(this.empfangsPuffer, datenEnde);
            this.aktuellerRecordTyp = endMarkierung;
        }

        for (int j = datenStart; j < datenStart + datensatzLaenge; j++) {
            rohDaten.add(this.empfangsPuffer[j]);
        }
        this.schreibPosition = datenEnde + ENDMARKER_LAENGE;
    }

    // =================================================================
    //  Typ-spezifisches Array erzeugen
    // =================================================================
    @SuppressWarnings("unchecked")
    private IPalType[] typArrayErzeugen(int typSchluessel) {
        ArrayList ablage = this.empfangsZwischenspeicher[typSchluessel];
        if (ablage == null) {
            return null;
        }

        switch (typSchluessel) {
            case 0:  return (IPalType[]) ablage.toArray(new PalTypeEnviron[0]);
            case 1:  return (IPalType[]) ablage.toArray(new PalTypeConnect[0]);
            case 3:  return (IPalType[]) ablage.toArray(new PalTypeSystemFile[0]);
            case 4:  return (IPalType[]) ablage.toArray(new PalTypeLibraryStatistics[0]);
            case 5:  return (IPalType[]) ablage.toArray(new PalTypeLibrary[0]);
            case 6:
            case 30: return (IPalType[]) ablage.toArray(new PalTypeLibId[0]);
            case 8:  return (IPalType[]) ablage.toArray(new PalTypeObject[0]);
            case 10: return (IPalType[]) ablage.toArray(new PalTypeResult[0]);
            case 11: return (IPalType[]) ablage.toArray(new PalTypeResultEx[0]);
            case 12: return (IPalType[]) ablage.toArray(new PalTypeSourceCodePage[0]);
            case 13: return (IPalType[]) ablage.toArray(new PalTypeStream[0]);
            case 14: return (IPalType[]) ablage.toArray(new PalTypeUtility[0]);
            case 15: return (IPalType[]) ablage.toArray(new PalTypeSrcDesc[0]);
            case 19: return (IPalType[]) ablage.toArray(new PalTypeNotify[0]);
            case 20: return (IPalType[]) ablage.toArray(new PalTypeGeneric[0]);
            case 25: return (IPalType[]) ablage.toArray(new PalTypeNatParm[0]);
            case 26: return (IPalType[]) ablage.toArray(new PalTypeSQLAuthentification[0]);
            case 27: return (IPalType[]) ablage.toArray(new PalTypeCmdGuard[0]);
            case 28: return (IPalType[]) ablage.toArray(new PalTypeSysVar[0]);
            case 34: return (IPalType[]) ablage.toArray(new PalTypeDbgStackFrame[0]);
            case 35: return (IPalType[]) ablage.toArray(new PalTypeDbgStatus[0]);
            case 36: return (IPalType[]) ablage.toArray(new PalTypeDbgVarContainer[0]);
            case 37: return (IPalType[]) ablage.toArray(new PalTypeDbgSyt[0]);
            case 38: return (IPalType[]) ablage.toArray(new PalTypeDbgVarDesc[0]);
            case 39: return (IPalType[]) ablage.toArray(new PalTypeDbgVarValue[0]);
            case 40: return (IPalType[]) ablage.toArray(new PalTypeDbgSpy[0]);
            case 42: return (IPalType[]) ablage.toArray(new PalTypeSourceUnicode[0]);
            case 45: return (IPalType[]) ablage.toArray(new PalTypeCP[0]);
            case 48: return (IPalType[]) ablage.toArray(new PalTypeSourceCP[0]);
            case 49: return (IPalType[]) ablage.toArray(new PalTypeDbmsInfo[0]);
            case 50: return (IPalType[]) ablage.toArray(new PalTypeClientConfig[0]);
            case 52: return (IPalType[]) ablage.toArray(new PalTypeDevEnv[0]);
            case 53: return (IPalType[]) ablage.toArray(new PalTypeDbgNatStack[0]);
            case 54: return (IPalType[]) ablage.toArray(new PalTypeTimeStamp[0]);
            case 55: return (IPalType[]) ablage.toArray(new PalTypeDbgaRecord[0]);
            default: return null;
        }
    }

    // =================================================================
    //  Empfangs-Thread
    // =================================================================

    /** Empfangsschleife — liest dauerhaft Pakete vom Socket. */
    private void empfangsSchleife() {
        byte[] kopfzeile = new byte[KOPFZEILEN_LAENGE];

        while (true) {
            int geleseneBytes = kopfzeileVomServerLesen(kopfzeile);
            if (this.empfangsThreadFehler != null || geleseneBytes == -1) {
                if (geleseneBytes == -1) {
                    this.verbindungVerloren = true;
                }
                return;
            }

            if (Arrays.equals(kopfzeile, QUITTUNG_NAECHSTES_SEGMENT)) {
                byte[] kopie = new byte[KOPFZEILEN_LAENGE];
                System.arraycopy(kopfzeile, 0, kopie, 0, KOPFZEILEN_LAENGE);
                this.uebergabeBereich.einlegen(kopie);
                continue;
            }

            boolean signaturOk = true;
            byte[] signaturBytes = PROTOKOLL_SIGNATUR.getBytes();
            for (int i = 0; i < SIGNATUR_LAENGE; i++) {
                if (kopfzeile[i] != signaturBytes[i]) {
                    signaturOk = false;
                    break;
                }
            }
            if (!signaturOk) {
                empfangsThreadBeenden(new IOException("Invalid pal header"));
                return;
            }

            ganzzahlAusPuffer(kopfzeile, SIGNATUR_LAENGE);
            ganzzahlAusPuffer(kopfzeile, SIGNATUR_LAENGE + PAKETGROESSE_FELDBREITE);
            int rohNutzdatenLaenge = ganzzahlAusPuffer(kopfzeile, SIGNATUR_LAENGE + PAKETGROESSE_FELDBREITE + EINTRAEGE_FELDBREITE);
            int empfangsNutzdatenLaenge = rohNutzdatenLaenge + ENDMARKER_LAENGE - KOPFZEILEN_LAENGE;

            if (!this.neuesKopfFormat && kopfzeile[KOPFZEILEN_LAENGE - 1] == NEUES_KOPFFORMAT_ERKENNUNG) {
                this.neuesKopfFormat = true;
            }

            byte[] nutzdaten = new byte[empfangsNutzdatenLaenge];
            byte[] lesePuffer = new byte[MAX_PAKETGROESSE];
            int gesamtGelesen = 0;
            do {
                try {
                    int n = this.empfangsDatenStrom.read(lesePuffer, 0, Math.min(MAX_PAKETGROESSE, empfangsNutzdatenLaenge - gesamtGelesen));
                    if (n == -1) {
                        this.verbindungVerloren = true;
                        empfangsThreadBeenden(new IOException("Connection closed by server"));
                        return;
                    }
                    System.arraycopy(lesePuffer, 0, nutzdaten, gesamtGelesen, n);
                    gesamtGelesen += n;
                } catch (IOException e) {
                    empfangsThreadBeenden(e);
                    return;
                }
            } while (gesamtGelesen < empfangsNutzdatenLaenge);

            this.uebergabeBereich.einlegen(nutzdaten);
        }
    }

    /** Kopfzeile vom Server empfangen (26 Bytes) mit Zeitüberschreitungs-Behandlung. */
    private int kopfzeileVomServerLesen(byte[] puffer) {
        boolean weiter = true;
        int geleseneBytes = 0;

        while (weiter) {
            try {
                geleseneBytes = this.empfangsDatenStrom.read(puffer);
                if (geleseneBytes == -1 && this.ersterLeseversuch) {
                    empfangsThreadBeenden(new IOException());
                }
                weiter = false;
                this.ersterLeseversuch = false;
            } catch (SocketTimeoutException e) {
                if (this.wartezeitRueckruf == null) {
                    weiter = false;
                    empfangsThreadBeenden(
                            new PalTimeoutException("Timeout occured while waiting for Ndv server reply", e));
                } else {
                    weiter = this.wartezeitRueckruf.continueOperation();
                    if (!weiter) {
                        empfangsThreadBeenden(
                                new PalTimeoutException("Timeout occured while waiting for Ndv server reply", e));
                    }
                }
            } catch (IOException e) {
                empfangsThreadBeenden(e);
                weiter = false;
            } catch (Exception e) {
                weiter = false;
            }
        }
        return geleseneBytes;
    }

    /** Empfangs-Thread beenden — Fehler speichern und Haupt-Thread aufwecken. */
    private void empfangsThreadBeenden(Exception fehler) {
        this.verbindungVerloren = true;
        this.empfangsThreadFehler = fehler;
        this.uebergabeBereich.einlegen(new byte[1]);
    }

    // =================================================================
    //  Übergabebereich (innere Klasse)
    // =================================================================
    private static class Uebergabebereich {
        private byte[] inhalt;
        private boolean bereit = false;

        synchronized byte[] abholen() {
            while (!this.bereit) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            this.bereit = false;
            notifyAll();
            return this.inhalt;
        }

        synchronized void einlegen(byte[] daten) {
            while (this.bereit) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            this.inhalt = daten;
            this.bereit = true;
            notifyAll();
        }
    }
}
