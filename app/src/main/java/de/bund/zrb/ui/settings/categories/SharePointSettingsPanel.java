package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for SharePoint connection configuration.
 * Allows configuring the parent page URL from which SharePoint links are extracted.
 */
public class SharePointSettingsPanel extends AbstractSettingsPanel {

    private final JTextField parentPageUrlField;
    private final JSpinner cacheConcurrencySpinner;

    public SharePointSettingsPanel() {
        super("sharepoint", "SharePoint");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Parent-Seite");

        parentPageUrlField = new JTextField(
                settings.sharepointParentPageUrl != null ? settings.sharepointParentPageUrl : "", 40);
        parentPageUrlField.setToolTipText(
                "URL der Seite, auf der Ihre SharePoint-Links aufgelistet sind.\n"
                + "Alle Links mit 'sharepoint' in der URL werden automatisch extrahiert.");
        fb.addRow("Parent-URL:", parentPageUrlField);

        fb.addInfo("<html><i>Geben Sie die URL einer Seite an, die Links zu Ihren SharePoint-Sites "
                + "enthält.<br>Beim Öffnen der SharePoint-Verbindung werden alle dort gelisteten "
                + "SharePoint-Links automatisch erkannt<br>und als navigierbare Sites angezeigt.</i></html>");

        fb.addSection("Caching");

        cacheConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.sharepointCacheConcurrency, 1, 8, 1));
        cacheConcurrencySpinner.setToolTipText("Anzahl paralleler Downloads beim Caching");
        fb.addRow("Parallele Downloads:", cacheConcurrencySpinner);

        fb.addInfo("<html><i>Beim ersten Besuch einer SharePoint-Seite wird der Inhalt automatisch "
                + "im lokalen Cache (H2 + Lucene) gespeichert.<br>"
                + "Die gecachten Inhalte sind über <b>Überall suchen</b> mit dem Kürzel "
                + "<b>SP</b> auffindbar.</i></html>");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.sharepointParentPageUrl = parentPageUrlField.getText().trim();
        s.sharepointCacheConcurrency = ((Number) cacheConcurrencySpinner.getValue()).intValue();
    }
}

