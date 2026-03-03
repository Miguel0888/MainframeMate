package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeCmdGuard;
import de.bund.zrb.ndv.core.api.IPalTypeLibId;
import de.bund.zrb.ndv.core.api.IPalTypeLibrary;

public class LibraryInfo implements ILibraryInfo {
    private String name;
    private EPrivatePrefixType privatPraefixTyp;
    private String privatPraefix;
    private IPalTypeLibId[] suchPfadBibliotheken;
    private IPalTypeCmdGuard befehlsSchutz;

    private LibraryInfo() {
    }

    public EPrivatePrefixType getPrivatePrefixType() {
        return privatPraefixTyp;
    }

    public String getPrivatePrefix() {
        return privatPraefix;
    }

    public IPalTypeLibId[] getStepLibs() {
        return suchPfadBibliotheken;
    }

    public IPalTypeCmdGuard getCmdGuard() {
        return befehlsSchutz;
    }

    public static class Builder {
        private String name;
        private IPalTypeCmdGuard befehlsSchutz;
        private IPalTypeLibrary privatBibliothek;

        public Builder(String name) {
            this.name = name;
        }

        public Builder cmdGuard(IPalTypeCmdGuard[] guards) {
            if (guards != null && guards.length > 0) {
                this.befehlsSchutz = guards[0];
            }
            return this;
        }

        public Builder prefix(IPalTypeLibrary[] libs) {
            if (libs != null && libs.length > 0) {
                this.privatBibliothek = libs[0];
            }
            return this;
        }

        public LibraryInfo build() {
            LibraryInfo info = new LibraryInfo();
            info.name = this.name;
            info.befehlsSchutz = this.befehlsSchutz;
            if (this.privatBibliothek != null) {
                info.privatPraefix = this.privatBibliothek.getLibrary();
                int flags = this.privatBibliothek.getFlags();
                switch (flags) {
                    case 1: info.privatPraefixTyp = EPrivatePrefixType.UNDEFINED; break;
                    case 2: info.privatPraefixTyp = EPrivatePrefixType.PROJECT; break;
                    case 3: info.privatPraefixTyp = EPrivatePrefixType.LIBRARY; break;
                    case 4: info.privatPraefixTyp = EPrivatePrefixType.USER; break;
                    case 5: info.privatPraefixTyp = EPrivatePrefixType.CUSTOM; break;
                    default: break;
                }
            }
            return info;
        }
    }
}
