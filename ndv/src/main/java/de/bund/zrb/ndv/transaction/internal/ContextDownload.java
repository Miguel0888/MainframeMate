package de.bund.zrb.ndv.transaction.internal;

import de.bund.zrb.ndv.transaction.api.ITransactionContextDownload;

import java.util.Set;

/**
 * Transaktionskontext für Download-Operationen.
 * Verwaltet den Zustand einer laufenden Download-Transaktion.
 */
public class ContextDownload implements ITransactionContextDownload {

    private Set options;
    private boolean beendet;
    private boolean gestartet;

    public ContextDownload() {
        this.beendet = false;
        this.gestartet = false;
    }

    public void setTerminated(boolean wert) {
        this.beendet = wert;
    }

    public void setStarted(boolean wert) {
        this.gestartet = wert;
    }

    public boolean isStarted() {
        return this.gestartet;
    }

    public boolean isTerminated() {
        return this.beendet;
    }

    public void setInitOptions(Set optionen) {
        this.options = optionen;
    }

    public Set getInitOptions() {
        return this.options;
    }
}

