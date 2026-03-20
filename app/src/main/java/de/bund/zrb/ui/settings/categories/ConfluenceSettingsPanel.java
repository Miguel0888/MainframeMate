package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for Confluence configuration.
 * Confluence instances are managed centrally via the "Passwörter" dialog
 * ({@code Einstellungen → Passwörter}).
 * This panel only contains prefetch settings.
 */
public class ConfluenceSettingsPanel extends AbstractSettingsPanel {

    private final JSpinner prefetchMaxItemsSpinner;
    private final JSpinner prefetchConcurrencySpinner;
    private final JSpinner prefetchCacheMaxMbSpinner;

    public ConfluenceSettingsPanel() {
        super("confluence", "Confluence");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Confluence-Instanzen");
        fb.addInfo("<html>Confluence-Instanzen werden über <b>Einstellungen → Passwörter</b> verwaltet.<br>"
                + "Dort können Sie Confluence-Einträge (Kategorie <i>Confluence</i>) hinzufügen, "
                + "bearbeiten und Zugangsdaten (mTLS-Zertifikat + Basic Auth) hinterlegen.</html>");

        // ── Prefetch settings ──
        fb.addSection("Vorabladen (Prefetch)");

        prefetchMaxItemsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.confluencePrefetchMaxItems, 1, 1000, 10));
        fb.addRow("Max. Seiten vorladen:", prefetchMaxItemsSpinner);

        prefetchConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.confluencePrefetchConcurrency, 1, 8, 1));
        fb.addRow("Parallele Requests:", prefetchConcurrencySpinner);

        prefetchCacheMaxMbSpinner = new JSpinner(new SpinnerNumberModel(
                settings.confluencePrefetchCacheMaxMb, 1, 500, 10));
        fb.addRow("Max. Cache-Größe (MB):", prefetchCacheMaxMbSpinner);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.confluencePrefetchMaxItems = (Integer) prefetchMaxItemsSpinner.getValue();
        s.confluencePrefetchConcurrency = (Integer) prefetchConcurrencySpinner.getValue();
        s.confluencePrefetchCacheMaxMb = (Integer) prefetchCacheMaxMbSpinner.getValue();
    }
}

