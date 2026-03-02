package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;
import com.softwareag.naturalone.natural.auxiliary.renumber.internal.IInsertLabels;
import com.softwareag.naturalone.natural.auxiliary.renumber.internal.RenumberSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

/**
 * Download- und Upload-Operationen fuer Quellcode und Binaerdaten.
 * Kapselt die komplexe Protokolllogik (fileOperationInitiate,
 * fileOperationSendDescription, sourceFromPal, binaryFromPal etc.)
 * und stellt sie als einfache Methoden zur Verfuegung.
 */
public class DownloadService {

    private static final String[] NULL_STRING_ARRAY = new String[0];

    private final PalSessionContext ctx;

    public DownloadService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ══════════════════════════════════════════════════════════════
    //  Oeffentliche Methoden
    // ══════════════════════════════════════════════════════════════

    /**
     * Quellcode vom Server herunterladen.
     */
    public IDownloadResult downloadSource(ITransactionContext txCtx,
                                          IPalTypeSystemFile sysFile, String library,
                                          IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props == null) throw new IllegalArgumentException("properties must not be null");

        ITransactionContextDownload dlCtx = null;
        if (txCtx instanceof ITransactionContextDownload) {
            dlCtx = (ITransactionContextDownload) txCtx;
        }

        IDownloadResult result = null;
        try {
            PalTrace.header("downloadSource");
            result = dateiOperationQuellcodeLaden(dlCtx, sysFile,
                    ctx.getLibrary(sysFile, library), props, options);
            return result;
        } catch (InvalidSourceException e) {
            throw e;
        }
    }

    /**
     * Binaerdaten vom Server herunterladen (deprecated 3-Arg-Variante).
     */
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile,
                                                 String library, String name, int type)
            throws IOException, PalResultException {
        return downloadBinary(sysFile, library,
                new ObjectProperties.Builder(name, type).longName(name).build());
    }

    /**
     * Binaerdaten vom Server herunterladen (deprecated 4-Arg-Variante).
     */
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile,
                                                 String library, String name,
                                                 String longName, int type)
            throws IOException, PalResultException {
        return downloadBinary(sysFile, library,
                new ObjectProperties.Builder(name, type).longName(longName).build());
    }

    /**
     * Binaerdaten vom Server herunterladen (ohne Transaktionskontext).
     */
    public ByteArrayOutputStream downloadBinary(IPalTypeSystemFile sysFile,
                                                 String library, IFileProperties props)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props.getName() == null) throw new IllegalArgumentException("sourceName must not be null");

        PalTrace.header("downloadBinary");
        return dateiOperationBinaerLaden(null, sysFile, library, props);
    }

    /**
     * Binaerdaten vom Server herunterladen (mit Transaktionskontext).
     */
    public ByteArrayOutputStream downloadBinary(ITransactionContext txCtx,
                                                 IPalTypeSystemFile sysFile,
                                                 String library, IFileProperties props)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props.getName() == null) throw new IllegalArgumentException("sourceName must not be null");

        if (txCtx instanceof ITransactionContextDownload) {
            PalTrace.header("downloadBinary");
            return dateiOperationBinaerLaden(
                    (ITransactionContextDownload) txCtx, sysFile, library, props);
        } else {
            throw new IllegalStateException("the download context is illegal");
        }
    }

    /**
     * Dateien vom Server empfangen (Multi-File-Download).
     * Hinweis: fileOperationReceiveFiles konnte im Original nicht dekompiliert werden.
     * Stub, bis der Bytecode rekonstruiert wird.
     */
    public Object[] receiveFiles(IPalTypeSystemFile sysFile, String library,
                                 IFileProperties props, Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props == null) throw new IllegalArgumentException("properties must not be null");

        PalTrace.header("receiveFiles");
        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setObject(props.getName());
        dateiId.setNewObject(props.getLongName());
        if (props.getType() == 32768) {
            dateiId.setNatKind(0);
        } else {
            dateiId.setNatKind(props.getKind());
        }
        dateiId.setNatType(props.getType());

        if (sysFile.getKind() == 6) {
            dateiId.setNatKind(props.getKind());
            dateiId.setNatType(8);
        }

        // fileOperationReceiveFiles konnte nicht dekompiliert werden —
        // Minimale Implementierung: einzelne Datei als Array zurueckgeben
        IDownloadResult result = dateiOperationQuellcodeLaden(
                null, sysFile, ctx.getLibrary(sysFile, library), props, options);
        if (result != null && result.getSource() != null) {
            return new Object[]{ result };
        }
        return new Object[0];
    }

    // ══════════════════════════════════════════════════════════════
    //  Kern-Protokolllogik: Quellcode-Download
    // ══════════════════════════════════════════════════════════════

    private IDownloadResult dateiOperationQuellcodeLaden(
            ITransactionContextDownload dlCtx,
            IPalTypeSystemFile sysFile, String library,
            IFileProperties props, Set<EDownLoadOption> options)
            throws UnsupportedEncodingException, IOException, PalResultException {

        IDownloadResult ergebnis = null;
        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setObject(props.getName());
        dateiId.setNewObject(props.getLongName());
        if (props.getType() == 32768) {
            dateiId.setNatKind(0);
        } else {
            dateiId.setNatKind(1);
        }
        dateiId.setNatType(props.getType());
        dateiId.setStructured(props.isStructured());

        if (sysFile.getKind() == 6) {
            dateiId.setNatKind(1);
            dateiId.setNatType(8);
        }

        // Automatisches Logon bei DDM-Typ
        if (ctx.isAutomaticLogon() && dlCtx == null
                && props.getType() == 8 && library.length() > 0) {
            logonIntern(library);
        }

        boolean fehlerhaft = false;
        try {
            // Datei-Operation initialisieren (wenn kein laufender Kontext)
            if (dlCtx == null || !istKontextGestartet(dlCtx)) {
                if (dlCtx != null) {
                    kontextSetOptionen(dlCtx, options);
                    kontextSetGestartet(dlCtx, true);
                }
                int operationsTyp = options.contains(EDownLoadOption.DELETE_ON_TARGET) ? 43 : 11;
                dateiOperationInitiieren(operationsTyp, sysFile, library, props.getBaseLibrary());
            }

            // Zeitstempel an den Server senden
            if (props.getTimeStamp() != null) {
                zeitstempelAnServerSenden(props.getTimeStamp());
            }

            try {
                IPalTypeNotify benachrichtigung = dateiBeschreibungSenden(dateiId, dlCtx);

                if (dateiId.getNatType() == 32768) {
                    // Fehlermeldungs-Objekt
                    ergebnis = fehlermeldungLaden(sysFile, library, dateiId);
                } else if (benachrichtigung.getNotification() == 6) {
                    // Normaler Quellcode
                    boolean ibm420 = false;
                    if (istIBM420Zeichensatz(props.getCodePage())
                            || (props.getCodePage().isEmpty()
                            && ctx.getNaturalParameters() != null
                            && ctx.getNaturalParameters().getRegional() != null
                            && istIBM420Zeichensatz(
                            ctx.getNaturalParameters().getRegional().getCodePage()))) {
                        ibm420 = true;
                    }
                    ergebnis = quellcodeVomServer(sysFile, library,
                            dateiId.getObject(),
                            !options.contains(EDownLoadOption.KEEP_LINE_NUMBERS),
                            objektHatZeilennummernVerweise(dateiId.getNatType()),
                            ibm420);
                    if (props.getTimeStamp() != null) {
                        props.getTimeStamp().copy(zeitstempelVomServerLesen());
                    }
                }
            } finally {
                if (props.getTimeStamp() != null) {
                    props.getTimeStamp().copy(zeitstempelVomServerLesen());
                }
            }

            // Wenn kein Transaktionskontext, Datei-Operation abbrechen
            if (dlCtx == null) {
                dateiOperationAbbrechen(options);
            }

        } catch (NullPointerException e) {
            fehlerhaft = true;
            throw new NullPointerException();
        } catch (PalUnmappableCodePointException e) {
            if (dlCtx == null) {
                ctx.getPal().init();
                dateiOperationAbbrechen(options);
            }
            throw e;
        } catch (UnsupportedEncodingException e) {
            if (dlCtx == null) {
                dateiOperationAbbrechen(options);
            }
            throw e;
        } catch (PalResultException e) {
            if (e.getErrorKind() == 4) {
                fehlerhaft = true;
            }
            throw e;
        } catch (InvalidSourceException e) {
            if (dlCtx == null) {
                dateiOperationAbbrechen(options);
            }
            throw e;
        } finally {
            if (dlCtx != null) {
                kontextSetBeendet(dlCtx, fehlerhaft);
            }
        }

        return ergebnis;
    }

    // ══════════════════════════════════════════════════════════════
    //  Kern-Protokolllogik: Binaer-Download
    // ══════════════════════════════════════════════════════════════

    private ByteArrayOutputStream dateiOperationBinaerLaden(
            ITransactionContextDownload dlCtx,
            IPalTypeSystemFile sysFile, String library,
            IFileProperties props)
            throws IOException, PalResultException {

        ByteArrayOutputStream ergebnis = null;
        boolean fehlerhaft = false;

        try {
            PalTimeStamp zeitstempel = props.getTimeStamp();
            PalTypeFileId dateiId = new PalTypeFileId();
            dateiId.setObject(props.getName());
            dateiId.setNewObject(props.getLongName());
            dateiId.setNatType(props.getType());
            if (props.getType() == 65536) {
                dateiId.setNatKind(16);
            } else {
                dateiId.setNatKind(2);
            }

            if (dlCtx == null || !istKontextGestartet(dlCtx)) {
                if (dlCtx != null) {
                    kontextSetOptionen(dlCtx, EnumSet.of(EDownLoadOption.NONE));
                    kontextSetGestartet(dlCtx, true);
                }
                dateiOperationInitiieren(11, sysFile, library, props.getBaseLibrary());
            }

            if (zeitstempel != null) {
                zeitstempelAnServerSenden(zeitstempel);
            }

            IPalTypeNotify benachrichtigung;
            try {
                benachrichtigung = dateiBeschreibungSenden(dateiId, dlCtx);
            } finally {
                if (zeitstempel != null) {
                    zeitstempel.copy(zeitstempelVomServerLesen());
                }
            }

            if (benachrichtigung.getNotification() == 6) {
                ergebnis = binaerVomServer(sysFile, library, dateiId.getObject());
                if (dlCtx == null) {
                    dateiOperationAbbrechen(EnumSet.of(EDownLoadOption.NONE));
                }
            } else if (benachrichtigung.getNotification() == 18) {
                ergebnis = new ByteArrayOutputStream();
            }
        } catch (NullPointerException e) {
            fehlerhaft = true;
            throw new NullPointerException();
        } finally {
            if (dlCtx != null) {
                kontextSetBeendet(dlCtx, fehlerhaft);
            }
        }

        return ergebnis;
    }

    // ══════════════════════════════════════════════════════════════
    //  Datei-Operationen (Protokoll-Schritte)
    // ══════════════════════════════════════════════════════════════

    /**
     * Datei-Operation am Server einleiten (LOGON in Bibliothek).
     */
    private void dateiOperationInitiieren(int operationsTyp,
                                          IPalTypeSystemFile sysFile, String library,
                                          String basisBibliothek)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();
        pal.add((IPalType) new PalTypeOperation(operationsTyp));

        if (operationsTyp == 1 && ctx.isMainframe()) {
            PalTypeLibId[] libs = new PalTypeLibId[]{
                    new PalTypeLibId(sysFile.getDatabaseId(), sysFile.getFileNumber(),
                            library, sysFile.getPassword(), sysFile.getCipher(), 6),
                    new PalTypeLibId(sysFile.getDatabaseId(), sysFile.getFileNumber(),
                            library, sysFile.getPassword(), sysFile.getCipher(), 6)
            };
            pal.add((IPalType[]) libs);
        } else {
            PalTypeLibId lib = new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), library,
                    sysFile.getPassword(), sysFile.getCipher(), 6);
            pal.add((IPalType) lib);
        }

        if (basisBibliothek != null) {
            pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), basisBibliothek,
                    sysFile.getPassword(), sysFile.getCipher(), 30));
        }

        pal.commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) {
            throw ex;
        }
    }

    /**
     * Dateibeschreibung an den Server senden und Benachrichtigung empfangen.
     *   1. PalTypeNotify(4) senden (Beschreibungs-Anforderung)
     *   2. PalTypeFileId senden
     *   3. commit
     *   4. Notify-Antwort (Typ 19) empfangen
     *   5. Fehler pruefen und Notify zurueckgeben
     *
     * Rekonstruiert aus Bytecode: Bei Notification==9 wird der Fehler
     * als 'terminated' markiert. Bei Fehler UND keinem Transaktionskontext
     * wird automatisch fileOperationAbort aufgerufen.
     */
    private IPalTypeNotify dateiBeschreibungSenden(PalTypeFileId dateiId,
                                                     ITransactionContext txCtx)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();

        ITransactionContextDownload dlCtx = null;
        if (txCtx instanceof ITransactionContextDownload) {
            dlCtx = (ITransactionContextDownload) txCtx;
        }

        IPalTypeNotify[] benachrichtigungen = null;
        boolean fehlerAufgetreten = false;

        try {
            // Notify(4) = "Beschreibung folgt" an Server senden
            pal.add((IPalType) new PalTypeNotify(4));
            // Datei-Beschreibung senden
            pal.add((IPalType) dateiId);
            pal.commit();

            // Antwort empfangen
            benachrichtigungen = (IPalTypeNotify[]) pal.retrieve(19);

            PalResultException ex = ctx.getResultException();
            dateiOperationBenachrichtigungPruefen(benachrichtigungen, ex);

            // Notification 9 = Server signalisiert Fehler/Abbruch
            if (benachrichtigungen[0].getNotification() == 9) {
                fehlerAufgetreten = true;
                if (ex != null) {
                    throw ex;
                }
            }

            // Normaler Fehler
            if (ex != null) {
                fehlerAufgetreten = true;
                throw ex;
            }
        } finally {
            if (fehlerAufgetreten) {
                // Bei Fehler: wenn kein Transaktionskontext, Abort senden
                if (dlCtx != null) {
                    // Kontext vorhanden - Abort ueber Kontext-Logik
                } else {
                    try {
                        dateiOperationAbbrechen(java.util.EnumSet.of(EDownLoadOption.NONE));
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return benachrichtigungen[0];
    }

    /**
     * Datei-Operation abbrechen (Abort-Nachricht senden).
     */
    public void dateiOperationAbbrechen(Set<EDownLoadOption> options)
            throws IOException, PalResultException {
        PalTypeNotify abbruch = new PalTypeNotify(
                options.contains(EDownLoadOption.DELETE_ON_TARGET) ? 12 : 5);
        ctx.getPal().add((IPalType) abbruch);
        ctx.getPal().commit();
        PalResultException ex = ctx.getResultException();
        IPalTypeNotify[] antwort = (IPalTypeNotify[]) ctx.getPal().retrieve(19);
        dateiOperationBenachrichtigungPruefen(antwort, ex);
        antwort[0].getNotification();
    }

    /**
     * Benachrichtigungs-Pruefung (wirft Fehler bei fehlender Notify-Antwort).
     */
    private void dateiOperationBenachrichtigungPruefen(IPalTypeNotify[] benachrichtigungen,
                                                        PalResultException ex)
            throws IOException, PalResultException {
        if (benachrichtigungen == null) {
            if (ex != null) {
                ex.setErrorKind(4);
                throw ex;
            }
            throw new IllegalArgumentException();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Quellcode-Parsing
    // ══════════════════════════════════════════════════════════════

    /**
     * Quellcode-Datensaetze vom Server empfangen und in String-Array wandeln.
     */
    private IDownloadResult quellcodeVomServer(IPalTypeSystemFile sysFile,
                                               String library, String objektName,
                                               boolean zeilennummernEntfernen,
                                               boolean hatZeilennummernVerweise,
                                               boolean ibm420)
            throws UnsupportedEncodingException, IOException {
        Pal pal = ctx.getPal();
        int zeilenNr = 0;

        try {
            // Quellcode-Datensaetze in verschiedenen Formaten empfangen
            Object quellDaten = (PalTypeSourceUnicode[]) pal.retrieve(42);
            if (quellDaten == null) {
                quellDaten = (PalTypeSourceCodePage[]) pal.retrieve(12);
                if (quellDaten == null) {
                    quellDaten = (PalTypeSourceCP[]) pal.retrieve(48);
                    if (quellDaten != null) {
                        String zeichensatz = zeichensatzNameErmitteln();
                        for (int i = 0; i < ((Object[]) quellDaten).length; i++) {
                            ((IPalTypeSource) ((Object[]) quellDaten)[i]).convert(zeichensatz);
                            zeilenNr++;
                        }
                    }
                }
            }

            if (quellDaten != null) {
                if (zeilennummernEntfernen) {
                    // Zeilennummern entfernen und Labels einsetzen
                    boolean labelEinfuegen = ctx.getPalPreferences() != null
                            && ctx.getPalPreferences().replaceLineNoRefsWithLabels();
                    return zeilennummernExtrahieren(
                            (IPalTypeSource[]) quellDaten,
                            hatZeilennummernVerweise,
                            labelEinfuegen,
                            labelEinfuegen ? ctx.getPalPreferences().createLabelsInNewLine() : false,
                            labelEinfuegen ? labelFormatKonvertieren(ctx.getPalPreferences().getLabelFormat()) : "",
                            ibm420);
                } else {
                    // Quellcode mit Zeilennummern zurueckgeben
                    int ersteZnr = 0;
                    int inkrement = 0;
                    String[] zeilen = new String[((Object[]) quellDaten).length];
                    for (int i = 0; i < ((Object[]) quellDaten).length; i++) {
                        zeilen[i] = ((IPalTypeSource) ((Object[]) quellDaten)[i]).getSourceRecord();
                        if (ersteZnr == 0) {
                            ersteZnr = Integer.valueOf(zeilen[i].substring(0, 4));
                        } else if (inkrement == 0) {
                            inkrement = Integer.valueOf(zeilen[i].substring(0, 4)) - ersteZnr;
                        }
                    }
                    inkrement = inkrement == 0 ? 1 : inkrement;

                    if (ibm420 && ctx.getShapingContext() != null) {
                        for (String zeile : zeilen) {
                            ctx.getShapingContext().unshape(zeile);
                        }
                    }

                    return new DownloadResult(zeilen, inkrement);
                }
            } else {
                return new DownloadResult(NULL_STRING_ARRAY, 0);
            }

        } catch (Exception e) {
            if (e instanceof PalUnmappableCodePointException) {
                ctx.getPal().init();
                String meldung = String.format("Conversion error in line %d: %s",
                        zeilenNr, e.getMessage());
                throw new PalUnmappableCodePointException(meldung,
                        ((PalUnmappableCodePointException) e).getPalTypeSource(),
                        ((PalUnmappableCodePointException) e).getColumn());
            }
            throw new InvalidSourceException("illegal Natural source", e);
        }
    }

    /**
     * Zeilennummern extrahieren und optional durch Labels ersetzen.
     */
    private IDownloadResult zeilennummernExtrahieren(IPalTypeSource[] datensaetze,
                                                     boolean hatVerweise,
                                                     boolean labelEinfuegen,
                                                     boolean neueZeileFuerLabel,
                                                     String labelFormat,
                                                     boolean ibm420) {
        java.util.ArrayList<StringBuffer> zeilen = new java.util.ArrayList<>();
        int ersteZnr = 0;
        int inkrement = 0;

        for (int i = 0; i < datensaetze.length; i++) {
            String zeile = datensaetze[i].getSourceRecord();
            if (ibm420 && ctx.getShapingContext() != null && zeile.length() > 0) {
                zeile = ctx.getShapingContext().unshape(zeile);
            }
            zeilen.add(new StringBuffer(zeile));

            if (ctx.isOpenSystemsServer() && zeilen.get(i).length() > 1) {
                zeilen.get(i).deleteCharAt(zeilen.get(i).length() - 1);
            }

            try {
                if (ersteZnr == 0) {
                    ersteZnr = Integer.valueOf(zeilen.get(i).substring(0, 4));
                } else if (inkrement == 0) {
                    inkrement = Integer.valueOf(zeilen.get(i).substring(0, 4)) - ersteZnr;
                }
            } catch (NumberFormatException e) {
                inkrement = 1;
            }
        }

        inkrement = inkrement == 0 ? 1 : inkrement;
        final boolean flgLabel = labelEinfuegen;
        final boolean flgNeueZeile = neueZeileFuerLabel;
        final String flgFormat = labelFormat;

        return new DownloadResult(
                RenumberSource.removeLineNumbers(zeilen, hatVerweise,
                        istRenConst(), 5, inkrement,
                        new IInsertLabels() {
                            public boolean isInsertLabels() { return flgLabel; }
                            public boolean isCreateNewLine() { return flgNeueZeile; }
                            public String getLabelFormat() { return flgFormat; }
                        }),
                inkrement);
    }

    /**
     * Binaerdaten vom Server empfangen.
     * Hinweis: binaryFromPal im Original konnte nicht dekompiliert werden.
     * Rekonstruktion: IPalTypeStream[] empfangen und zusammensetzen.
     */
    private ByteArrayOutputStream binaerVomServer(IPalTypeSystemFile sysFile,
                                                   String library, String name)
            throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        IPalTypeStream[] streams = (IPalTypeStream[]) ctx.getPal().retrieve(13);
        if (streams != null) {
            for (IPalTypeStream stream : streams) {
                byte[] chunk = stream.getStreamRecord();
                if (chunk != null) {
                    result.write(chunk);
                }
            }
        }
        return result;
    }

    /**
     * Fehlermeldungs-Objekt laden (Typ 32768 = Error Message).
     * Hinweis: fileOperationDownloadErrorMsg im Original konnte nicht dekompiliert werden.
     * Rekonstruktion: PalTypeSourceCodePage[] empfangen und als String[] zurueckgeben.
     */
    private IDownloadResult fehlermeldungLaden(IPalTypeSystemFile sysFile,
                                               String library,
                                               IPalTypeFileId dateiId)
            throws IOException, PalResultException {
        PalTypeSourceCodePage[] quellZeilen =
                (PalTypeSourceCodePage[]) ctx.getPal().retrieve(12);
        if (quellZeilen != null) {
            String[] zeilen = new String[quellZeilen.length];
            for (int i = 0; i < quellZeilen.length; i++) {
                zeilen[i] = quellZeilen[i].getSourceRecord();
            }
            return new DownloadResult(zeilen, 0);
        }
        return new DownloadResult(NULL_STRING_ARRAY, 0);
    }

    // ══════════════════════════════════════════════════════════════
    //  Hilfsmethoden
    // ══════════════════════════════════════════════════════════════

    private void zeitstempelAnServerSenden(PalTimeStamp ts) throws IOException {
        ctx.getPal().add((IPalType) new PalTypeTimeStamp(
                ts.getFlags(), ts.getCompactString(), ts.getUser()));
    }

    private PalTimeStamp zeitstempelVomServerLesen() throws IOException {
        PalTimeStamp result = null;
        IPalTypeTimeStamp[] ts = (IPalTypeTimeStamp[]) ctx.getPal().retrieve(54);
        if (ts != null) {
            result = PalTimeStamp.get(ts[0].getTimeStamp(), ts[0].getUserId().trim());
        }
        return result == null ? PalTimeStamp.get() : result;
    }

    private String zeichensatzNameErmitteln() throws IOException {
        String name = ctx.getPalProperties().getDefaultCodePage();
        IPalTypeCP[] cps = (IPalTypeCP[]) ctx.getPal().retrieve(45);
        if (cps != null) {
            String cp = cps[0].getCodePage();
            if (cp != null && cp.length() != 0) {
                name = cp.trim();
            }
        }
        return name;
    }

    private boolean objektHatZeilennummernVerweise(int natTyp) {
        return natTyp != 4 && natTyp != 1 && natTyp != 2 && natTyp != 4096 && natTyp != 8;
    }

    private boolean istIBM420Zeichensatz(String codePage) {
        if (codePage == null) return false;
        return codePage.trim().equals("IBM420");
    }

    private boolean istRenConst() {
        boolean result = false;
        try {
            IServerConfiguration cfg = ctx.getServerConfiguration();
            if (cfg != null) {
                result = cfg.getRenConst();
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private String labelFormatKonvertieren(String format) {
        StringBuffer sb = new StringBuffer(format);
        int idx = sb.indexOf("{count}");
        if (idx != -1) {
            sb.replace(idx, idx + 7, "%d");
            sb.append(".");
        } else {
            sb.append("%d.");
        }
        return sb.toString();
    }

    private void logonIntern(String library) throws IOException, PalResultException {
        ctx.requirePal();
        IPalTypeLibId[] stepLibs = new IPalTypeLibId[1];
        stepLibs[0] = PalTypeLibIdFactory.newInstance();
        PalTrace.header("logon");
        ctx.getPal().add((IPalType) new PalTypeOperation(2, 12));
        ctx.getPal().add((IPalType) new PalTypeStack("LOGON " + library));
        ctx.getPal().add((IPalType[]) stepLibs);
        ctx.getPal().commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) {
            throw ex;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ContextDownload - Adapter-Methoden
    //  (im Original ueber synthetic access$-Methoden geloest)
    // ══════════════════════════════════════════════════════════════

    private boolean istKontextGestartet(ITransactionContextDownload dlCtx) {
        if (dlCtx instanceof ContextDownload) {
            return ((ContextDownload) dlCtx).isStarted();
        }
        return false;
    }

    private void kontextSetGestartet(ITransactionContextDownload dlCtx, boolean started) {
        if (dlCtx instanceof ContextDownload) {
            ((ContextDownload) dlCtx).setStarted(started);
        }
    }

    private void kontextSetOptionen(ITransactionContextDownload dlCtx, Set<EDownLoadOption> options) {
        if (dlCtx instanceof ContextDownload) {
            ((ContextDownload) dlCtx).setInitOptions(options);
        }
    }

    private void kontextSetBeendet(ITransactionContextDownload dlCtx, boolean terminated) {
        if (dlCtx instanceof ContextDownload) {
            ((ContextDownload) dlCtx).setTerminated(terminated);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Innere Klassen
    // ══════════════════════════════════════════════════════════════

    /**
     * Download-Ergebnis: Quellcode-Zeilen und Zeilennummern-Inkrement.
     */
    static class DownloadResult implements IDownloadResult {
        private final String[] source;
        private final int lineIncrement;

        DownloadResult(String[] source, int lineIncrement) {
            this.source = source;
            this.lineIncrement = lineIncrement;
        }

        @Override
        public String[] getSource() {
            return this.source;
        }

        @Override
        public int getLineIncrement() {
            return this.lineIncrement;
        }
    }

    /**
     * Download-Transaktionskontext.
     */
    public static class ContextDownload implements ITransactionContextDownload {
        private Set<EDownLoadOption> options;
        private boolean terminated = false;
        private boolean started = false;

        public ContextDownload() {
        }

        public boolean isStarted() {
            return started;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }

        public boolean isTerminated() {
            return terminated;
        }

        public void setTerminated(boolean terminated) {
            this.terminated = terminated;
        }

        public Set<EDownLoadOption> getInitOptions() {
            return options;
        }

        public void setInitOptions(Set<EDownLoadOption> options) {
            this.options = options;
        }
    }
}
