package de.zrb.bund.api;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MainframeContext {

    Map<String, String> loadPluginSettings(String pluginKey);

    void savePluginSettings(String pluginKey, Map<String, String> settings);

    Optional<TabAdapter> getSelectedTab();
    void openFileTab(String content);

    JFrame getMainFrame();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // For Extensions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    BookmarkManager getBookmarkManager();
    List<TabAdapter> getAllFileTabs();
    void focusFileTab(TabAdapter tab);

    void refresh();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // ToDo

}
