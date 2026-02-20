package de.bund.zrb;

import de.bund.zrb.history.LocalHistoryService;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.bund.zrb.ui.util.UnicodeFontFix;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
//        UnicodeFontFix.apply(); // for windows 11 required to display emojis correctly
        MainframeSyntaxSupport.register(); // Register Natural/JCL/COBOL syntax highlighting

        // Start local history auto-pruning in background
        LocalHistoryService.getInstance().autoPruneAsync();

        SwingUtilities.invokeLater(() -> {
            MainFrame gui = new MainFrame();
            gui.setVisible(true);
        });
    }
}