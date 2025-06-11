package de.bund.zrb.ui.filetab.event;

public class CaretMovedEvent {
    public final int editorLine;

    public CaretMovedEvent(int editorLine) {
        this.editorLine = editorLine;
    }
}
