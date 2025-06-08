package de.zrb.bund.api;

public interface MenuCommand {
    String getId();               // z. B. "file.save"
    String getLabel();            // z. B. "Speichern"
    void perform();
}
