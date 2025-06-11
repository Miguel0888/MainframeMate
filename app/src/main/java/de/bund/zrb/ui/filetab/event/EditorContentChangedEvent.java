package de.bund.zrb.ui.filetab.event;

public class EditorContentChangedEvent {
    public final boolean changed;

    public EditorContentChangedEvent(boolean changed) {
        this.changed = changed;
    }
}
