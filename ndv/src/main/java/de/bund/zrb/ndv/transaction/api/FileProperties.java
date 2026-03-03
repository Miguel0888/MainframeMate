package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IFileProperties;
import de.bund.zrb.ndv.core.api.PalDate;
import de.bund.zrb.ndv.transaction.impl.NdvTimeStamp;

import java.util.EnumSet;
import java.util.Set;

public class FileProperties implements IFileProperties {
    private final String name;
    private final String langName;
    private final String benutzer;
    private final String kompiliertVonBenutzer;
    private final String zeichensatz;
    private final int quellGroesse;
    private final int kompilierteGroesse;
    private final int objektArt;
    private final int objektTyp;
    private final int datenbankNummer;
    private final int dateiNummer;
    private final boolean strukturiert;
    private final boolean verknuepftesDdm;
    private final PalDate quellDatum;
    private final PalDate kompiliertDatum;
    private final PalDate zugriffsDatum;
    private final String internesLabelErstes;
    private final int zeilennummernSchrittweite;
    private final Set<EFileOptions> optionen;
    private final NdvTimeStamp zeitstempel;
    private final String basisBibliothek;

    private FileProperties(Builder builder) {
        this.name = builder.name;
        this.langName = builder.langName;
        this.benutzer = builder.benutzer;
        this.kompiliertVonBenutzer = builder.kompiliertVonBenutzer;
        this.zeichensatz = builder.zeichensatz;
        this.quellGroesse = builder.quellGroesse;
        this.kompilierteGroesse = builder.kompilierteGroesse;
        this.objektArt = builder.objektArt;
        this.objektTyp = builder.objektTyp;
        this.datenbankNummer = builder.datenbankNummer;
        this.dateiNummer = builder.dateiNummer;
        this.strukturiert = builder.strukturiert;
        this.verknuepftesDdm = builder.verknuepftesDdm;
        this.quellDatum = builder.quellDatum;
        this.kompiliertDatum = builder.kompiliertDatum;
        this.zugriffsDatum = builder.zugriffsDatum;
        this.internesLabelErstes = builder.internesLabelErstes;
        this.zeilennummernSchrittweite = builder.zeilennummernSchrittweite;
        this.optionen = builder.optionen;
        this.zeitstempel = builder.zeitstempel;
        this.basisBibliothek = builder.basisBibliothek;
    }

    public String getName() {
        return name;
    }

    public String getLongName() {
        return langName;
    }

    public String getUser() {
        return benutzer;
    }

    public String getGpUser() {
        return kompiliertVonBenutzer;
    }

    public String getCodePage() {
        return zeichensatz;
    }

    public int getSourceSize() {
        return quellGroesse;
    }

    public int getGpSize() {
        return kompilierteGroesse;
    }

    public int getKind() {
        return objektArt;
    }

    public int getType() {
        return objektTyp;
    }

    public int getDatbaseId() {
        return datenbankNummer;
    }

    public int getFnr() {
        return dateiNummer;
    }

    public boolean isStructured() {
        return strukturiert;
    }

    public boolean isLinkedDdm() {
        return verknuepftesDdm;
    }

    public PalDate getSourceDate() {
        return quellDatum;
    }

    public PalDate getGpDate() {
        return kompiliertDatum;
    }

    public PalDate getAccessDate() {
        return zugriffsDatum;
    }

    public PalDate getDate() {
        if (objektArt == 1 || objektArt == 16 || objektArt == 64) {
            return quellDatum;
        }
        return kompiliertDatum;
    }

    public int getSize() {
        if (objektArt == 1 || objektArt == 16 || objektArt == 64) {
            return quellGroesse;
        }
        return kompilierteGroesse;
    }

    public String getInternalLabelFirst() {
        return internesLabelErstes;
    }

    public int getLineNumberIncrement() {
        return zeilennummernSchrittweite;
    }

    public Set<EFileOptions> getOptions() {
        return optionen;
    }

    public NdvTimeStamp getTimeStamp() {
        return zeitstempel;
    }

    public String getBaseLibrary() {
        return basisBibliothek;
    }

    public static class Builder {
        private final String name;
        private final int objektTyp;
        private String langName = "";
        private String benutzer = "";
        private String kompiliertVonBenutzer = "";
        private String zeichensatz = "";
        private int quellGroesse;
        private int kompilierteGroesse;
        private int objektArt = 1;
        private int datenbankNummer;
        private int dateiNummer;
        private boolean strukturiert = true;
        private boolean verknuepftesDdm = false;
        private PalDate quellDatum = new PalDate();
        private PalDate kompiliertDatum = new PalDate();
        private PalDate zugriffsDatum = new PalDate();
        private String internesLabelErstes = "";
        private int zeilennummernSchrittweite;
        private Set<EFileOptions> optionen = EnumSet.of(EFileOptions.INIT);
        private NdvTimeStamp zeitstempel;
        private String basisBibliothek;

        public Builder(String name, int type) {
            this.name = name;
            this.objektTyp = type;
        }

        public Builder longName(String longName) { this.langName = longName; return this; }
        public Builder user(String user) { this.benutzer = user; return this; }
        public Builder gpUser(String gpUser) { this.kompiliertVonBenutzer = gpUser; return this; }
        public Builder codePage(String codePage) { this.zeichensatz = codePage; return this; }
        public Builder sourceSize(int sourceSize) { this.quellGroesse = sourceSize; return this; }
        public Builder gpSize(int gpSize) { this.kompilierteGroesse = gpSize; return this; }
        public Builder natKind(int natKind) { this.objektArt = natKind; return this; }
        public Builder databaseId(int databaseId) { this.datenbankNummer = databaseId; return this; }
        public Builder fileNumber(int fileNumber) { this.dateiNummer = fileNumber; return this; }
        public Builder isStructured(boolean structured) { this.strukturiert = structured; return this; }
        public Builder isLinkedDDm(boolean linkedDdm) { this.verknuepftesDdm = linkedDdm; return this; }
        public Builder sourceDate(PalDate sourceDate) { this.quellDatum = sourceDate; return this; }
        public Builder gpDate(PalDate gpDate) { this.kompiliertDatum = gpDate; return this; }
        public Builder accessDate(PalDate accessDate) { this.zugriffsDatum = accessDate; return this; }
        public Builder internalLabelFirst(String label) { this.internesLabelErstes = label; return this; }
        public Builder lineNumberIncrement(int inc) { this.zeilennummernSchrittweite = inc; return this; }
        public Builder options(Set<EFileOptions> options) { this.optionen = options; return this; }
        public Builder timeStamp(NdvTimeStamp timeStamp) { this.zeitstempel = timeStamp; return this; }
        public Builder baseLibrary(String baseLibrary) { this.basisBibliothek = baseLibrary; return this; }

        public FileProperties build() {
            return new FileProperties(this);
        }
    }
}
