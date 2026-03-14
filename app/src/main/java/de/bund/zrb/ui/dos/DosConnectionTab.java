package de.bund.zrb.ui.dos;

import de.bund.zrb.dosbox.core.DOSBox;
import de.bund.zrb.dosbox.gui.SwingDisplay;
import de.bund.zrb.dosbox.hardware.memory.Memory;
import de.bund.zrb.dosbox.ints.Int10Handler;
import de.bund.zrb.dosbox.shell.DosShell;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;

/**
 * DOSBox connection tab – embeds a DOS terminal emulator as a MainframeMate tab.
 * Allows testing the Java DOSBox port directly inside the application.
 */
public class DosConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final SwingDisplay display;
    private final DOSBox dosbox;
    private volatile boolean closed;

    public DosConnectionTab() {
        dosbox = new DOSBox();

        // Create the Swing display panel
        display = new SwingDisplay(dosbox.getMemory(), dosbox.getInt16());
        display.startRendering();

        // Main panel with border
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createToolbar(), BorderLayout.NORTH);
        mainPanel.add(display, BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        // Start the DOS shell on a background thread
        startShell();
    }

    private void startShell() {
        // Continuously sync cursor position from BIOS video handler to display (30 Hz)
        javax.swing.Timer cursorSync = new javax.swing.Timer(33, e -> {
            display.setCursorPosition(
                    dosbox.getInt10().getCursorRow(),
                    dosbox.getInt10().getCursorCol());
        });
        cursorSync.start();

        Thread shellThread = new Thread(() -> {
            DosShell shell = dosbox.getShell();
            shell.showBanner();
            while (shell.isRunning() && !closed) {
                shell.showPrompt();
                String line = shell.readLine();
                if (line != null) {
                    shell.executeCommand(line);
                }
            }
            cursorSync.stop();
        }, "DOSBox-Shell-Tab");
        shellThread.setDaemon(true);
        shellThread.start();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));

        JLabel label = new JLabel("  \uD83D\uDCBB DOS 5.0  ");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        toolbar.add(label);

        JButton resetBtn = new JButton("\u21BB Reset");
        resetBtn.setToolTipText("DOS-Emulator zur\u00FCcksetzen");
        resetBtn.addActionListener(e -> {
            dosbox.getMemory().reset();
            // Re-init video
            for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
                dosbox.getMemory().writeByte(Memory.TEXT_VIDEO_START + i * 2, 0x20);
                dosbox.getMemory().writeByte(Memory.TEXT_VIDEO_START + i * 2 + 1, 0x07);
            }
            startShell();
        });
        toolbar.add(resetBtn);

        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
        statusBar.add(new JLabel("JDOSBox \u2013 Java DOSBox Port (em-dosbox-svn-sdl2)"));
        return statusBar;
    }

    // ── ConnectionTab / FtpTab / Bookmarkable implementation ─

    @Override
    public String getTitle() {
        return "\uD83D\uDCBB DOS";
    }

    @Override
    public String getTooltip() {
        return "DOS-Terminal (JDOSBox Java-Port)";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void saveIfApplicable() {
        // Nothing to save
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // Not applicable
    }

    @Override
    public String getPath() {
        return "dos://C:\\";
    }

    @Override
    public Bookmarkable.Type getType() {
        return Bookmarkable.Type.CONNECTION;
    }

    @Override
    public void onClose() {
        closed = true;
        dosbox.getShell().setRunning(false);
    }

    @Override
    public void focusSearchField() {
        // Give focus to the DOS display panel so keyboard input works
        SwingUtilities.invokeLater(() -> display.requestFocusInWindow());
    }

    @Override
    public void searchFor(String searchPattern) {
        // Not applicable for DOS terminal
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem resetItem = new JMenuItem("\u21BB DOS zur\u00FCcksetzen");
        resetItem.addActionListener(e -> {
            // Reset would go here
        });

        JMenuItem closeItem = new JMenuItem("\u274C Tab schlie\u00DFen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(resetItem);
        menu.addSeparator();
        menu.add(closeItem);
        return menu;
    }
}
