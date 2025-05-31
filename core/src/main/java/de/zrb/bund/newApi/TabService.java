package de.zrb.bund.newApi;

import de.zrb.bund.newApi.listener.TabListener;

import java.awt.*;

public interface TabService {
    int openTab(String title, Component component);
    void closeTab(int index);
    void updateTab(int index, Component newContent);
    void addTabListener(TabListener listener);
}
