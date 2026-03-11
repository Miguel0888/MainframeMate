package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.wiki.domain.WikiSiteDescriptor;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.infrastructure.JwbfWikiContentService;
import de.bund.zrb.wiki.port.WikiContentService;
import de.bund.zrb.wiki.ui.WikiConnectionTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a Wiki connection tab for browsing MediaWiki sites.
 */
public class OpenWebMenuCommand extends ShortcutMenuCommand {

    private final TabbedPaneManager tabManager;

    public OpenWebMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.wiki";
    }

    @Override
    public String getLabel() {
        return "Wiki\u2026";
    }

    @Override
    public void perform() {
        List<WikiSiteDescriptor> sites = parseWikiSites();
        if (sites.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Keine Wiki-Sites konfiguriert.\n"
                            + "Bitte unter Einstellungen → Wiki mindestens eine Site hinzufügen.",
                    "Keine Wiki-Sites", JOptionPane.WARNING_MESSAGE);
            return;
        }

        WikiContentService service = new JwbfWikiContentService(sites);
        WikiConnectionTab tab = new WikiConnectionTab(service);
        tabManager.addTab(tab);
    }

    private List<WikiSiteDescriptor> parseWikiSites() {
        Settings settings = SettingsHelper.load();
        List<WikiSiteDescriptor> result = new ArrayList<WikiSiteDescriptor>();

        if (settings.wikiSites == null) return result;

        for (String entry : settings.wikiSites) {
            // Format: "id|displayName|apiUrl|requiresLogin"
            String[] parts = entry.split("\\|", 4);
            if (parts.length >= 3) {
                String id = parts[0].trim();
                String name = parts[1].trim();
                String url = parts[2].trim();
                boolean login = parts.length >= 4 && "true".equalsIgnoreCase(parts[3].trim());
                result.add(new WikiSiteDescriptor(new WikiSiteId(id), name, url, login));
            }
        }
        return result;
    }
}
