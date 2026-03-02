package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.type.PalTypeNatParm;
import com.softwareag.naturalone.natural.pal.external.*;
import com.softwareag.naturalone.natural.paltransactions.external.INatParm;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Implementierung der Natural-Parameter.
 * Aggregiert die einzelnen NatParm-Datensätze und bietet
 * Lazy-Zugriff auf die verschiedenen Parameter-Gruppen.
 */
public class NatParm implements INatParm, Serializable {

    private static final long serialVersionUID = 1L;

    private final IPalTypeNatParm[] parameterDatensaetze;
    private IReport bericht = null;
    private ICharAssign zeichenzuordnung = null;
    private ICompOpt kompilierungsOptionen = null;
    private ILimit grenzwerte = null;
    private IFldApp feldanwendung = null;
    private IRegional regionales = null;
    private IRpc fernaufruf = null;
    private IErr fehlerBehandlung = null;
    private IBuffSize pufferGroesse = null;

    public NatParm(IPalTypeNatParm[] datensaetze) {
        this.parameterDatensaetze = datensaetze;
    }

    @Override
    public IRpc getRpc() {
        if (this.fernaufruf == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getRpc() != null) {
                    this.fernaufruf = eintrag.getRpc();
                    break;
                }
            }
        }
        return this.fernaufruf;
    }

    @Override
    public IRegional getRegional() {
        if (this.regionales == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getRegional() != null) {
                    this.regionales = eintrag.getRegional();
                    break;
                }
            }
        }
        return this.regionales;
    }

    @Override
    public IFldApp getFldApp() {
        if (this.feldanwendung == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getFldApp() != null) {
                    this.feldanwendung = eintrag.getFldApp();
                    break;
                }
            }
        }
        return this.feldanwendung;
    }

    @Override
    public IReport getReport() {
        if (this.bericht == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getReport() != null) {
                    this.bericht = eintrag.getReport();
                    break;
                }
            }
        }
        return this.bericht;
    }

    @Override
    public ICharAssign getCharAssign() {
        if (this.zeichenzuordnung == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getCharAssign() != null) {
                    this.zeichenzuordnung = eintrag.getCharAssign();
                    break;
                }
            }
        }
        return this.zeichenzuordnung;
    }

    @Override
    public ICompOpt getCompOpt() {
        if (this.kompilierungsOptionen == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getCompOpt() != null) {
                    this.kompilierungsOptionen = eintrag.getCompOpt();
                    break;
                }
            }
        }
        return this.kompilierungsOptionen;
    }

    @Override
    public ILimit getLimit() {
        if (this.grenzwerte == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getLimit() != null) {
                    this.grenzwerte = eintrag.getLimit();
                    break;
                }
            }
        }
        return this.grenzwerte;
    }

    @Override
    public IBuffSize getBuffSize() {
        if (this.pufferGroesse == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getBuffSize() != null) {
                    this.pufferGroesse = eintrag.getBuffSize();
                    break;
                }
            }
        }
        return this.pufferGroesse;
    }

    @Override
    public IErr getErr() {
        if (this.fehlerBehandlung == null) {
            for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
                if (eintrag.getErr() != null) {
                    this.fehlerBehandlung = eintrag.getErr();
                    break;
                }
            }
        }
        return this.fehlerBehandlung;
    }

    @Override
    public IPalTypeNatParm[] get(int modus) {
        ArrayList<IPalTypeNatParm> ergebnis = new ArrayList<>();

        for (IPalTypeNatParm eintrag : this.parameterDatensaetze) {
            boolean einschliessen = true;

            if (eintrag.getRecordIndex() == 6 && modus == 1) {
                einschliessen = false;
            }
            if (eintrag.getRecordIndex() == 7 && modus == 1) {
                einschliessen = false;
            }

            if (einschliessen) {
                ergebnis.add(new PalTypeNatParm(eintrag));
            }
        }

        return ergebnis.toArray(new IPalTypeNatParm[0]);
    }
}

