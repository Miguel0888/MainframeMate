package de.bund.zrb.ui.commands;

public interface Command {
    String getId();               // z. B. "file.save"
    String getLabel();            // z. B. "Speichern"
    void perform();
}
