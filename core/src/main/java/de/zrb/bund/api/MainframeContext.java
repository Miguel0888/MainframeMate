package de.zrb.bund.api;

import javax.swing.*;
import java.util.Map;
import java.util.Optional;

public interface MainframeContext {

    Map<String, String> loadPluginSettings(String pluginKey);

    void savePluginSettings(String pluginKey, Map<String, String> settings);

    Optional<FileTabAdapter> getSelectedFileTab();
    void openFileTab(String content);

    JFrame getMainFrame();

    // ToDo

}
