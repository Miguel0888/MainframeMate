package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

/**
 * Settings panel for Wiki configuration.
 * Wiki sites are managed centrally via the "Passwörter" dialog
 * ({@code Einstellungen → Passwörter}).
 * This panel only contains prefetch settings.
 */
public class WikiSettingsPanel extends AbstractSettingsPanel {

    private final JSpinner prefetchMaxItemsSpinner;
    private final JSpinner prefetchConcurrencySpinner;
    private final JSpinner prefetchCacheMaxMbSpinner;

    public WikiSettingsPanel() {
        super("wiki", "Wiki");
        FormBuilder fb = new FormBuilder();

        fb.addSection("Wiki-Sites");
        fb.addInfo("<html>Wiki-Sites werden über <b>Einstellungen → Passwörter</b> verwaltet.<br>"
                + "Dort können Sie Wiki-Einträge (Kategorie <i>Wiki</i>) hinzufügen, "
                + "bearbeiten und Zugangsdaten hinterlegen.</html>");

        // ── Prefetch settings ──
        fb.addSection("Vorabladen (Prefetch)");

        prefetchMaxItemsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchMaxItems, 1, 1000, 10));
        fb.addRow("Max. Seiten vorladen:", prefetchMaxItemsSpinner);

        prefetchConcurrencySpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchConcurrency, 1, 8, 1));
        fb.addRow("Parallele Requests:", prefetchConcurrencySpinner);

        prefetchCacheMaxMbSpinner = new JSpinner(new SpinnerNumberModel(
                settings.wikiPrefetchCacheMaxMb, 1, 500, 10));
        fb.addRow("Max. Cache-Größe (MB):", prefetchCacheMaxMbSpinner);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.wikiPrefetchMaxItems = (Integer) prefetchMaxItemsSpinner.getValue();
        s.wikiPrefetchConcurrency = (Integer) prefetchConcurrencySpinner.getValue();
        s.wikiPrefetchCacheMaxMb = (Integer) prefetchCacheMaxMbSpinner.getValue();

        // Sync IndexSource entries for auto-indexed wiki sites
        syncWikiIndexSources(s);
    }

    /**
     * Create/remove IndexSource entries for each wiki site based on autoIndex flag.
     */
    private void syncWikiIndexSources(Settings s) {
        try {
            de.bund.zrb.indexing.service.IndexingService indexingService =
                    de.bund.zrb.indexing.service.IndexingService.getInstance();
            java.util.Set<String> existingWikiSourceIds = new java.util.HashSet<String>();
            for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI) {
                    existingWikiSourceIds.add(src.getConnectionHost());
                }
            }

            for (Settings.PasswordEntryMeta meta : s.passwordEntries) {
                if (!"Wiki".equals(meta.category)) continue;

                if (meta.autoIndex && !existingWikiSourceIds.contains(meta.id)) {
                    // Create new IndexSource for this wiki
                    de.bund.zrb.indexing.model.IndexSource source = new de.bund.zrb.indexing.model.IndexSource();
                    source.setName("Wiki: " + (meta.displayName != null ? meta.displayName : meta.id));
                    source.setSourceType(de.bund.zrb.indexing.model.SourceType.WIKI);
                    source.setEnabled(true);
                    source.setConnectionHost(meta.id);
                    source.setMaxCrawlDepth(0);
                    source.setMaxUrlsPerSession(100);
                    source.setScheduleMode(de.bund.zrb.indexing.model.ScheduleMode.MANUAL);
                    source.setFulltextEnabled(true);
                    source.setEmbeddingEnabled(false);
                    indexingService.saveSource(source);
                } else if (!meta.autoIndex && existingWikiSourceIds.contains(meta.id)) {
                    // Remove IndexSource for this wiki
                    for (de.bund.zrb.indexing.model.IndexSource src : indexingService.getAllSources()) {
                        if (src.getSourceType() == de.bund.zrb.indexing.model.SourceType.WIKI
                                && meta.id.equals(src.getConnectionHost())) {
                            indexingService.removeSource(src.getSourceId());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Best-effort: don't fail settings save if indexing sync fails
        }
    }
}
