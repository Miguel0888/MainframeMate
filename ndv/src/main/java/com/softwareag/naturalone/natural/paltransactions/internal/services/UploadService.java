package com.softwareag.naturalone.natural.paltransactions.internal.services;

import com.softwareag.naturalone.natural.auxiliary.renumber.internal.RenumberSource;
import com.softwareag.naturalone.natural.pal.*;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.*;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Upload-Operationen: Quellcode und Binaerdaten an den Server senden,
 * sowie Kompilierungskommandos (catalog, check, stow, save).
 *
 * read() ist ebenfalls hier, weil es intern dieselbe Logik wie
 * handleCommand braucht (readInternal/sourceToPal).
 */
public class UploadService {

    private final PalSessionContext ctx;

    public UploadService(PalSessionContext ctx) {
        this.ctx = ctx;
    }

    // ══════════════════════════════════════════════════════════════
    //  uploadSource
    // ══════════════════════════════════════════════════════════════

    public void uploadSource(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, Set<EUploadOption> options,
                             String[] sourceLines)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        if (sourceLines == null) throw new IllegalArgumentException("lines must not be null");
        if (sourceLines.length == 0) throw new IllegalArgumentException("lines array is empty");
        if (props.getKind() != 1) throw new IllegalArgumentException("the FileProperties kind must be ObjectKind.SOURCE");

        if (props.getType() == 32768) {
            uploadErrorMessage(sysFile, library, props, options, sourceLines);
        } else {
            uploadFile(sysFile, library, props, options, sourceLines);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  sendFiles
    // ══════════════════════════════════════════════════════════════

    public void sendFiles(IPalTypeSystemFile sysFile, String library,
                          ObjectProperties objProps, Set<EUploadOption> options, Object[] data)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (objProps == null) throw new IllegalArgumentException("properties must not be null");
        if (data == null) throw new IllegalArgumentException("files must not be null");
        if (data.length == 0) throw new IllegalArgumentException("files array is empty");

        PalTrace.header("sendFiles");
        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setNatKind(objProps.getKind());
        dateiId.setNatType(objProps.getType());

        if (objProps.getType() == 32768) {
            dateiId.setNatKind(64);
            if (objProps.getName().length() > 2) {
                dateiId.setNewObject(objProps.getName() + ".MSG");
                String numTeil = objProps.getName().substring(1, 3);
                if (numTeil.startsWith("0")) {
                    numTeil = numTeil.substring(1);
                }
                dateiId.setObject(numTeil);
            } else {
                dateiId.setNewObject(objProps.getName());
                dateiId.setObject(objProps.getName());
            }
        } else if (objProps.getType() == 8) {
            dateiId.setObject(objProps.getLongName());
            dateiId.setNewObject(objProps.getName());
        } else {
            dateiId.setObject(objProps.getName());
        }

        dateiId.setStructured(objProps.isStructured());
        dateiId.setUser(objProps.getUser());
        dateiId.setGpUser(objProps.getGpUser());
        dateiId.setSourceDate(objProps.getSourceDate());
        dateiId.setGpDate(objProps.getGpDate());
        dateiId.setSourceSize(objProps.getSourceSize());
        dateiId.setGpSize(objProps.getGpSize());
        dateiId.setDatabaseId(objProps.getDatbaseId());
        dateiId.setFileNumber(objProps.getFnr());

        fileOperationSendFiles(sysFile, ctx.getLibrary(sysFile, library),
                objProps.getBaseLibrary(), dateiId, data,
                objProps.getLineNumberIncrement(), objProps.getInternalLabelFirst(),
                options, objProps.getTimeStamp());
    }

    // ══════════════════════════════════════════════════════════════
    //  uploadBinary
    // ══════════════════════════════════════════════════════════════

    public void uploadBinary(IPalTypeSystemFile sysFile, String library,
                             IFileProperties props, ByteArrayOutputStream data)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        if (data == null) throw new IllegalArgumentException("contents must not be null");
        if (props.getKind() != 16 && props.getKind() != 2)
            throw new IllegalArgumentException("the FileProperties kind must be ObjectKind.RESOURCE");

        PalTrace.header("uploadBinary");
        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setNatKind(props.getKind());
        dateiId.setNatType(props.getType());
        dateiId.setObject(props.getName());
        dateiId.setNewObject(props.getLongName());
        dateiId.setUser(props.getUser());
        dateiId.setDatabaseId(props.getDatbaseId());
        dateiId.setFileNumber(props.getFnr());

        if (props.getKind() == 2) {
            dateiId.setStructured(props.isStructured());
            dateiId.setGpDate(props.getDate());
            dateiId.setGpSize(props.getSize() != 0 ? props.getSize() : data.size());
        } else {
            dateiId.setSourceDate(props.getDate());
            dateiId.setSourceSize(props.getSize() != 0 ? props.getSize() : data.size());
        }

        fileOperationUploadBinary(sysFile, library, dateiId, data,
                props.getTimeStamp(), props.getBaseLibrary());
    }

    // ══════════════════════════════════════════════════════════════
    //  read
    // ══════════════════════════════════════════════════════════════

    public String[] read(IPalTypeSystemFile sysFile, String library,
                         String name, Set<EReadOption> options)
            throws IOException, PalResultException {
        ctx.requirePal();
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (name == null) throw new IllegalArgumentException("sourceName must not be null");
        return readInternal(sysFile, library, name, options, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  catalog / check / stow / save  (alle delegieren an handleCommand)
    // ══════════════════════════════════════════════════════════════

    public void catalog(IPalTypeSystemFile sysFile, String library,
                        IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        ctx.requirePal();
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        PalTrace.header("catalog");
        boolean altesFormat = props.getOptions() != null
                && props.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT);
        handleCommand(sysFile, ctx.getLibrary(sysFile, library), props.getBaseLibrary(),
                props.getType() == 8 ? props.getLongName() : props.getName(),
                props.getType(), "CATALOG", props.isStructured(), true,
                sourceLines, props.getLineNumberIncrement(), props.getCodePage(),
                props.getDatbaseId(), props.getFnr(), altesFormat,
                props.getTimeStamp(), props.isLinkedDdm());
    }

    public void check(IPalTypeSystemFile sysFile, String library,
                      IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        ctx.requirePal();
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        PalTrace.header("check");
        boolean altesFormat = props.getOptions() != null
                && props.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT);
        handleCommand(sysFile, ctx.getLibrary(sysFile, library), props.getBaseLibrary(),
                props.getType() == 8 ? props.getLongName() : props.getName(),
                props.getType(), "CHECK", props.isStructured(), true,
                sourceLines, props.getLineNumberIncrement(), props.getCodePage(),
                props.getDatbaseId(), props.getFnr(), altesFormat,
                props.getTimeStamp(), props.isLinkedDdm());
    }

    public void stow(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        ctx.requirePal();
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        PalTrace.header("stow");
        boolean altesFormat = props.getOptions() != null
                && props.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT);
        handleCommand(sysFile, ctx.getLibrary(sysFile, library), props.getBaseLibrary(),
                props.getType() == 8 ? props.getLongName() : props.getName(),
                props.getType(), "STOW", props.isStructured(), true,
                sourceLines, props.getLineNumberIncrement(), props.getCodePage(),
                props.getDatbaseId(), props.getFnr(), altesFormat,
                props.getTimeStamp(), props.isLinkedDdm());
    }

    public void save(IPalTypeSystemFile sysFile, String library,
                     IFileProperties props, String[] sourceLines)
            throws IOException, PalResultException, PalCompileResultException {
        ctx.requirePal();
        if (props == null) throw new IllegalArgumentException("properties must not be null");
        PalTrace.header("save");
        boolean altesFormat = props.getOptions() != null
                && props.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT);
        handleCommand(sysFile, ctx.getLibrary(sysFile, library), props.getBaseLibrary(),
                props.getType() == 8 ? props.getLongName() : props.getName(),
                props.getType(), "SAVE", props.isStructured(), true,
                sourceLines, props.getLineNumberIncrement(), props.getCodePage(),
                props.getDatbaseId(), props.getFnr(), altesFormat,
                props.getTimeStamp(), props.isLinkedDdm());
    }

    // ══════════════════════════════════════════════════════════════
    //  handleCommand — zentrale Kommandoverarbeitung
    //  (catalog/check/stow/save benutzen alle diesen Ablauf)
    // ══════════════════════════════════════════════════════════════

    private void handleCommand(IPalTypeSystemFile sysFile, String library,
                               String basisBibliothek, String objektName, int typ,
                               String kommando, boolean strukturiert, boolean mitQuellcode,
                               String[] quellZeilen, int zeilenInkrement,
                               String zeichensatz, int dbId, int fnr,
                               boolean altesFormat, PalTimeStamp zeitstempel,
                               boolean verknuepftesDdm)
            throws IOException, PalResultException, PalCompileResultException {

        if (sysFile == null) throw new IllegalArgumentException("systemFileKey must not be null");
        if (library == null) throw new IllegalArgumentException("library must not be null");
        if (objektName == null) throw new IllegalArgumentException("sourceName must not be null");
        if (!ObjectType.getInstanceIdExtension().containsKey(typ))
            throw new IllegalArgumentException("type must be one of the ids defined inside utility class 'sag.pal.ObjectType'");

        Pal pal = ctx.getPal();
        byte operationsUnterTyp = 28;

        // Auto-Logon
        if (ctx.isAutomaticLogon() && library.length() > 0) {
            ctx.logon(library);
        }

        // Quellcode senden
        if (quellZeilen != null) {
            String effZeichensatz = zeichensatz;
            if (zeichensatz != null && !zeichensatz.trim().isEmpty()) {
                IPalTypeCP[] seiten = ctx.getCodePages();
                if (seiten != null && !Arrays.asList(seiten).contains(new PalTypeCP(zeichensatz))) {
                    effZeichensatz = ctx.getPalProperties().getDefaultCodePage();
                }
            }

            try {
                sourceToPal(quellZeilen, zeilenInkrement, ctx.getInternalLabelPrefix(),
                        effZeichensatz, true, objectHasLineNumberReferences(typ));
            } catch (PalUnmappableCodePointException e) {
                pal.init();
                throw new PalCompileResultException(3422, 3, e.getMessage(),
                        e.getRow(), e.getColumn(), typ, objektName, library,
                        sysFile.getDatabaseId(), sysFile.getFileNumber());
            }

            if (effZeichensatz != null) {
                PalTypeCP cp = new PalTypeCP();
                cp.setCodePage(effZeichensatz);
                pal.add((IPalType) cp);
            }
            operationsUnterTyp = 2;
        } else if (typ == 8) {
            // DDM ohne Quellcode → intern lesen
            String[] ddmZeilen;
            if (verknuepftesDdm) {
                ddmZeilen = readInternal(sysFile, "", objektName,
                        EnumSet.of(EReadOption.READDDM), false);
            } else {
                ddmZeilen = readInternal(sysFile, library, objektName,
                        EnumSet.of(EReadOption.READDDM), false);
            }
            if (ctx.getPalProperties().getNdvType() == 1) {
                try {
                    sourceToPal(ddmZeilen, zeilenInkrement, ctx.getInternalLabelPrefix(),
                            null, false, objectHasLineNumberReferences(typ));
                } catch (PalUnmappableCodePointException e) {
                    pal.init();
                    throw new PalCompileResultException(3422, 3, e.getMessage(),
                            e.getRow(), e.getColumn(), typ, objektName, library,
                            sysFile.getDatabaseId(), sysFile.getFileNumber());
                }
            }
        }

        if (kommando.equals("SAVE")) {
            operationsUnterTyp = 4;
        }

        pal.add((IPalType) new PalTypeOperation(2, operationsUnterTyp));
        pal.add((IPalType) new PalTypeStack(kommando));
        pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                sysFile.getFileNumber(), library,
                sysFile.getPassword(), sysFile.getCipher(), 6));
        if (basisBibliothek != null) {
            pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), basisBibliothek,
                    sysFile.getPassword(), sysFile.getCipher(), 30));
        }

        PalTypeSrcDesc beschreibung = (typ == 8)
                ? new PalTypeSrcDesc(typ, objektName, strukturiert, dbId, fnr)
                : new PalTypeSrcDesc(typ, objektName, strukturiert, altesFormat ? 1 : 0);
        pal.add((IPalType) beschreibung);

        if (zeitstempel != null) {
            zeitstempelAnServerSenden(zeitstempel);
        }

        pal.commit();
        PalCompileResultException kompilierFehler = getCompileResultException();
        if (zeitstempel != null) {
            zeitstempel.copy(zeitstempelVomServerLesen());
        }
        if (kompilierFehler != null) {
            throw kompilierFehler;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  readInternal — Quellcode lesen (fuer read() und DDM-Lesen)
    // ══════════════════════════════════════════════════════════════

    String[] readInternal(IPalTypeSystemFile sysFile, String library,
                          String name, Set<EReadOption> options, boolean mitLogon)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();

        if (mitLogon && ctx.isAutomaticLogon() && library.length() > 0) {
            ctx.logon(library);
        }

        // Sub-Operation bestimmen (wie im Original)
        int unterTyp = 0;
        if (options.contains(EReadOption.READ)) {
            unterTyp = 10;
        }
        if (options.contains(EReadOption.READDDM)) {
            unterTyp = 11;
        }
        if (options.contains(EReadOption.LIST)) {
            unterTyp = 8;
        }
        if (options.contains(EReadOption.LISTDDM)) {
            unterTyp = 24;
        }
        if (options.contains(EReadOption.EDITDDM)) {
            unterTyp = 23;
        }
        if (options.contains(EReadOption.EDIT)) {
            unterTyp = 21;
        }

        pal.add((IPalType) new PalTypeOperation(2, unterTyp));
        pal.add((IPalType) new PalTypeStack("READ " + name + " " + library));
        pal.commit();

        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        // Quellcode empfangen (sourceFromPal-Logik)
        IPalTypeSource[] quellen = (IPalTypeSource[]) pal.retrieve(42); // PalTypeSourceUnicode
        if (quellen == null) {
            quellen = (IPalTypeSource[]) pal.retrieve(12); // PalTypeSourceCodePage
        }
        if (quellen == null) {
            quellen = (IPalTypeSource[]) pal.retrieve(48); // PalTypeSourceCP
            if (quellen != null) {
                // PalTypeSourceCP benötigt Charset-Konvertierung
                String charsetName = ctx.getPalProperties().getDefaultCodePage();
                IPalTypeCP[] cp = (IPalTypeCP[]) pal.retrieve(45);
                if (cp != null) {
                    String cpName = cp[0].getCodePage();
                    if (cpName != null && cpName.trim().length() > 0) {
                        charsetName = cpName.trim();
                    }
                }
                for (int i = 0; i < quellen.length; i++) {
                    quellen[i].convert(charsetName);
                }
            }
        }
        if (quellen == null) {
            return new String[0];
        }

        String[] zeilen = new String[quellen.length];
        for (int i = 0; i < quellen.length; i++) {
            zeilen[i] = quellen[i].getSourceRecord();
        }
        return zeilen;
    }

    // ══════════════════════════════════════════════════════════════
    //  sourceToPal — Quellcode in PAL-Datensaetze serialisieren
    // ══════════════════════════════════════════════════════════════

    private void sourceToPal(String[] zeilen, int inkrement,
                             String labelPraefix, String zeichensatz,
                             boolean mitZeilenNummern, boolean hatVerweise)
            throws IOException {
        Pal pal = ctx.getPal();
        PalTypeSource[] datensaetze = new PalTypeSource[zeilen.length];

        try {
            Class quellKlasse = getSourceClass();

            // Effektiven Zeichensatz bestimmen (wie im Original)
            String effZeichensatz = zeichensatz;
            if (zeichensatz == null || zeichensatz.length() == 0) {
                effZeichensatz = ctx.getPalProperties().getDefaultCodePage();
            }

            if (!mitZeilenNummern) {
                // Ohne Zeilennummern: Quellzeilen direkt verwenden
                for (int i = 0; i < zeilen.length; i++) {
                    datensaetze[i] = (PalTypeSource) quellKlasse.newInstance();
                    datensaetze[i].setPalVers(ctx.getPalProperties().getPalVersion());
                    if (ctx.isOpenSystemsServer()) {
                        datensaetze[i].setSourceRecord(zeilen[i] + " ");
                    } else {
                        datensaetze[i].setSourceRecord(zeilen[i]);
                    }
                    datensaetze[i].setCharSetName(effZeichensatz);
                }
            } else {
                // Mit Zeilennummern: RenumberSource verwenden (wie im Original)
                String effLabelPraefix = ctx.getInternalLabelPrefix();
                if (labelPraefix != null) {
                    for (int j = 0; j < labelPraefix.length(); j++) {
                        if (labelPraefix.charAt(j) != ' ') {
                            effLabelPraefix = labelPraefix;
                            break;
                        }
                    }
                }

                StringBuffer[] nummerierteZeilen = RenumberSource.addLineNumbers(
                        zeilen, inkrement, effLabelPraefix, hatVerweise,
                        ctx.isOpenSystemsServer(), false);

                for (int i = 0; i < zeilen.length; i++) {
                    datensaetze[i] = (PalTypeSource) quellKlasse.newInstance();
                    datensaetze[i].setPalVers(ctx.getPalProperties().getPalVersion());
                    datensaetze[i].setNdvType(ctx.getPalProperties().getNdvType());
                    datensaetze[i].setSourceRecord(nummerierteZeilen[i].toString());
                    datensaetze[i].setCharSetName(effZeichensatz);
                }
            }

            pal.add((IPalType[]) datensaetze);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (PalUnmappableCodePointException e) {
            pal.init();
            // Zeilennummer der fehlerhaften Zeile ermitteln
            for (int i = 0; i < datensaetze.length; i++) {
                if (datensaetze[i] == e.getPalTypeSource()) {
                    e.setRow(i + 1);
                    throw e;
                }
            }
        } catch (Throwable e) {
            pal.init();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Error serializing source", e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  uploadFile — Quellcode-Datei hochladen
    // ══════════════════════════════════════════════════════════════

    private void uploadFile(IPalTypeSystemFile sysFile, String library,
                            IFileProperties props, Set<EUploadOption> options,
                            String[] sourceLines)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();
        PalTrace.header("uploadSource");

        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setNatKind(props.getKind());
        dateiId.setNatType(props.getType());
        dateiId.setObject(props.getName());
        dateiId.setNewObject(props.getLongName());
        dateiId.setStructured(props.isStructured());
        dateiId.setDatabaseId(props.getDatbaseId());
        dateiId.setFileNumber(props.getFnr());

        fileOperationUploadSource(sysFile, ctx.getLibrary(sysFile, library),
                dateiId, sourceLines, props, options);
    }

    // ══════════════════════════════════════════════════════════════
    //  uploadErrorMessage — Fehlermeldungsdatei hochladen
    // ══════════════════════════════════════════════════════════════

    private void uploadErrorMessage(IPalTypeSystemFile sysFile, String library,
                                    IFileProperties props, Set<EUploadOption> options,
                                    String[] sourceLines)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();
        PalTrace.header("uploadSource (error message)");

        PalTypeFileId dateiId = new PalTypeFileId();
        dateiId.setNatKind(64);
        if (props.getName().length() > 2) {
            dateiId.setNewObject(props.getName() + ".MSG");
            String numTeil = props.getName().substring(1, 3);
            if (numTeil.startsWith("0")) numTeil = numTeil.substring(1);
            dateiId.setObject(numTeil);
        } else {
            dateiId.setNewObject(props.getName());
            dateiId.setObject(props.getName());
        }
        dateiId.setNatType(32768);

        fileOperationUploadErrorMsg(sysFile, ctx.getLibrary(sysFile, library),
                props.getBaseLibrary(), dateiId, sourceLines, props.getTimeStamp());
    }

    // ══════════════════════════════════════════════════════════════
    //  Protokoll-Operationen
    // ══════════════════════════════════════════════════════════════

    private void fileOperationUploadSource(IPalTypeSystemFile sysFile,
                                           String library, PalTypeFileId dateiId,
                                           String[] zeilen, IFileProperties props,
                                           Set<EUploadOption> options)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();
        PalResultException gesamtFehler = null;

        try {
            // Datei-Operation einleiten (Operation 12, wie im Original)
            ctx.fileOperationInitiate(12, sysFile, library, props.getBaseLibrary());

            // Datei-Beschreibung senden
            dateiId.setSourceSize(calculateSourceSize(zeilen));
            if (props.getOptions() != null && props.getOptions().contains(EFileOptions.OLD_DATAAREA_FORMAT)) {
                dateiId.setOptions(1);
            }
            IPalTypeNotify antwort = ctx.fileOperationSendDescription(dateiId);

            if (antwort.getNotification() == 13) {
                // Quellcode senden
                try {
                    sourceToPal(zeilen, props.getLineNumberIncrement(),
                            props.getInternalLabelFirst(), props.getCodePage(),
                            !options.contains(EUploadOption.SOURCE_UNCHANGED),
                            objectHasLineNumberReferences(props.getType()));
                } catch (PalUnmappableCodePointException e) {
                    pal.init();
                    throw new PalCompileResultException(3422, 3, e.getMessage(),
                            e.getRow(), e.getColumn(), dateiId.getNatType(), dateiId.getObject(),
                            library, sysFile.getDatabaseId(), sysFile.getFileNumber());
                }

                if (props.getCodePage() != null) {
                    PalTypeCP cp = new PalTypeCP();
                    cp.setCodePage(props.getCodePage());
                    pal.add((IPalType) cp);
                }

                if (props.getTimeStamp() != null) {
                    zeitstempelAnServerSenden(props.getTimeStamp());
                }

                pal.commit();
                IPalTypeNotify[] endBenachrichtigungen = (IPalTypeNotify[]) pal.retrieve(19);
                if (props.getTimeStamp() != null) {
                    props.getTimeStamp().copy(zeitstempelVomServerLesen());
                }

                gesamtFehler = ctx.getResultException();
                ctx.fileOperationNotifyNull(endBenachrichtigungen, gesamtFehler);
                if (endBenachrichtigungen[0].getNotification() == 9
                        || endBenachrichtigungen[0].getNotification() == 6) {
                    ctx.fileOperationAbort(EnumSet.of(EDownLoadOption.NONE));
                }
            }
        } catch (NullPointerException npe) {
            throw new NullPointerException();
        }

        if (gesamtFehler != null) {
            throw gesamtFehler;
        }
    }

    private void fileOperationSendFiles(IPalTypeSystemFile sysFile,
                                        String library, String basisBibliothek,
                                        PalTypeFileId dateiId, Object[] daten,
                                        int zeilenInkrement, String ersterLabel,
                                        Set<EUploadOption> options,
                                        PalTimeStamp zeitstempel)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();

        // Datei-Operation einleiten
        int opTyp = options.contains(EUploadOption.DELETE_ON_TARGET) ? 43 : 11;
        pal.add((IPalType) new PalTypeOperation(opTyp));
        pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                sysFile.getFileNumber(), library,
                sysFile.getPassword(), sysFile.getCipher(), 6));
        if (basisBibliothek != null) {
            pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), basisBibliothek,
                    sysFile.getPassword(), sysFile.getCipher(), 30));
        }
        pal.commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        // Dateien einzeln senden
        for (Object datei : daten) {
            if (datei instanceof String[]) {
                // Quellcode
                dateiId.setSourceSize(calculateSourceSize((String[]) datei));
                pal.add((IPalType) dateiId);
                pal.add((IPalType) new PalTypeNotify(6));
                pal.commit();
                ex = ctx.getResultException();
                IPalTypeNotify[] notify = (IPalTypeNotify[]) pal.retrieve(19);
                if (notify == null) {
                    if (ex != null) { ex.setErrorKind(4); throw ex; }
                    throw new IllegalArgumentException();
                }
                if (notify[0].getNotification() == 6) {
                    sourceToPal((String[]) datei, zeilenInkrement,
                            ersterLabel, null, true,
                            objectHasLineNumberReferences(dateiId.getNatType()));

                    if (zeitstempel != null) zeitstempelAnServerSenden(zeitstempel);
                    pal.add((IPalType) new PalTypeNotify(5));
                    pal.commit();
                    ex = ctx.getResultException();
                    if (zeitstempel != null) zeitstempel.copy(zeitstempelVomServerLesen());
                }
            } else if (datei instanceof ByteArrayOutputStream) {
                // Binaerdaten
                binaryToPal((ByteArrayOutputStream) datei);
                if (zeitstempel != null) zeitstempelAnServerSenden(zeitstempel);
                pal.add((IPalType) new PalTypeNotify(5));
                pal.commit();
                ex = ctx.getResultException();
                if (zeitstempel != null) zeitstempel.copy(zeitstempelVomServerLesen());
            }
        }

        // Abschluss
        pal.add((IPalType) new PalTypeNotify(
                options.contains(EUploadOption.DELETE_ON_TARGET) ? 12 : 5));
        pal.commit();
        ex = ctx.getResultException();
        if (ex != null) throw ex;
    }

    private void fileOperationUploadErrorMsg(IPalTypeSystemFile sysFile,
                                             String library, String basisBibliothek,
                                             PalTypeFileId dateiId, String[] zeilen,
                                             PalTimeStamp zeitstempel)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();

        pal.add((IPalType) new PalTypeOperation(11));
        pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                sysFile.getFileNumber(), library,
                sysFile.getPassword(), sysFile.getCipher(), 6));
        if (basisBibliothek != null) {
            pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), basisBibliothek,
                    sysFile.getPassword(), sysFile.getCipher(), 30));
        }
        pal.commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        pal.add((IPalType) dateiId);
        pal.add((IPalType) new PalTypeNotify(6));
        pal.commit();
        ex = ctx.getResultException();
        IPalTypeNotify[] notify = (IPalTypeNotify[]) pal.retrieve(19);
        if (notify == null) {
            if (ex != null) { ex.setErrorKind(4); throw ex; }
            throw new IllegalArgumentException();
        }

        if (notify[0].getNotification() == 6) {
            // Fehlermeldungs-Zeilen als Quellcode-Datensaetze senden
            for (String zeile : zeilen) {
                pal.add((IPalType) new PalTypeSourceCodePage(zeile));
            }
            if (zeitstempel != null) zeitstempelAnServerSenden(zeitstempel);
            pal.add((IPalType) new PalTypeNotify(5));
            pal.commit();
            ex = ctx.getResultException();
            if (zeitstempel != null) zeitstempel.copy(zeitstempelVomServerLesen());
        }

        pal.add((IPalType) new PalTypeNotify(5));
        pal.commit();
        ex = ctx.getResultException();
        if (ex != null) throw ex;
    }

    private void fileOperationUploadBinary(IPalTypeSystemFile sysFile,
                                           String library, PalTypeFileId dateiId,
                                           ByteArrayOutputStream data,
                                           PalTimeStamp zeitstempel,
                                           String basisBibliothek)
            throws IOException, PalResultException {
        Pal pal = ctx.getPal();

        pal.add((IPalType) new PalTypeOperation(11));
        pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                sysFile.getFileNumber(), library,
                sysFile.getPassword(), sysFile.getCipher(), 6));
        if (basisBibliothek != null) {
            pal.add((IPalType) new PalTypeLibId(sysFile.getDatabaseId(),
                    sysFile.getFileNumber(), basisBibliothek,
                    sysFile.getPassword(), sysFile.getCipher(), 30));
        }
        pal.commit();
        PalResultException ex = ctx.getResultException();
        if (ex != null) throw ex;

        pal.add((IPalType) dateiId);
        pal.add((IPalType) new PalTypeNotify(6));
        pal.commit();
        ex = ctx.getResultException();
        IPalTypeNotify[] notify = (IPalTypeNotify[]) pal.retrieve(19);
        if (notify == null) {
            if (ex != null) { ex.setErrorKind(4); throw ex; }
            throw new IllegalArgumentException();
        }

        if (notify[0].getNotification() == 6) {
            binaryToPal(data);
            if (zeitstempel != null) zeitstempelAnServerSenden(zeitstempel);
            pal.add((IPalType) new PalTypeNotify(5));
            pal.commit();
            ex = ctx.getResultException();
            if (zeitstempel != null) zeitstempel.copy(zeitstempelVomServerLesen());
        }

        pal.add((IPalType) new PalTypeNotify(5));
        pal.commit();
        ex = ctx.getResultException();
        if (ex != null) throw ex;
    }

    // ══════════════════════════════════════════════════════════════
    //  Hilfsmethoden
    // ══════════════════════════════════════════════════════════════

    private void binaryToPal(ByteArrayOutputStream data) throws IOException {
        Pal pal = ctx.getPal();
        byte[] bytes = data.toByteArray();
        int offset = 0;
        while (offset < bytes.length) {
            int chunkSize = Math.min(253, bytes.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(bytes, offset, chunk, 0, chunkSize);
            pal.add((IPalType) new PalTypeStream(chunk, ctx.getPalProperties().getNdvType()));
            offset += chunkSize;
        }
    }

    private int calculateSourceSize(String[] zeilen) {
        int groesse = 0;
        for (String zeile : zeilen) {
            groesse += (zeile != null ? zeile.length() : 0) + 4;
        }
        return groesse;
    }

    private boolean objectHasLineNumberReferences(int natTyp) {
        return natTyp != 4 && natTyp != 1 && natTyp != 2 && natTyp != 4096 && natTyp != 8;
    }

    private Class getSourceClass() {
        int ndvTyp = ctx.getPalProperties().getNdvType();
        int ndvVersion = ctx.getPalProperties().getNdvVersion();
        int ndvMajorVersion;
        try {
            ndvMajorVersion = Integer.valueOf(Integer.valueOf(ndvVersion).toString().substring(0, 3));
        } catch (Exception e) {
            ndvMajorVersion = 0;
        }

        if (ndvTyp == 1) {
            // Mainframe
            if (ctx.getPalProperties().isMfUnicodeSrcPossible()) {
                return PalTypeSourceUnicode.class;
            } else if (ndvMajorVersion >= 224) {
                return PalTypeSourceCP.class;
            } else {
                return PalTypeSourceCodePage.class;
            }
        } else {
            // Open Systems
            if (ndvMajorVersion >= 220) {
                return PalTypeSourceUnicode.class;
            } else {
                return PalTypeSourceCodePage.class;
            }
        }
    }

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

    private PalCompileResultException getCompileResultException() throws IOException {
        Pal pal = ctx.getPal();
        PalResultException ex = ctx.getResultException();
        if (ex != null) {
            PalTypeSrcDesc[] beschr = (PalTypeSrcDesc[]) pal.retrieve(15);
            if (beschr != null) {
                return new PalCompileResultException(
                        ex.getErrorNumber(), ex.getErrorKind(), ex.getMessage(),
                        beschr[0].getErrorLine(), beschr[0].getErrorColumn(),
                        beschr[0].getType(), beschr[0].getObject(),
                        "", 0, 0);
            }
            return new PalCompileResultException(
                    ex.getErrorNumber(), ex.getErrorKind(), ex.getMessage());
        }
        return null;
    }
}

