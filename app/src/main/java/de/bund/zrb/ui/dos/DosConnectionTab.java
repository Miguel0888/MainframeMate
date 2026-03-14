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
import java.io.File;

/**
 * DOSBox connection tab – embeds a DOS terminal emulator as a MainframeMate tab.
 * Uses the built-in Java CPU emulator for executing DOS programs.
 * No native DOSBox dependency required!
 */
public class DosConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final SwingDisplay display;
    private final DOSBox dosbox;
    private volatile boolean closed;

    /** The doom2 directory (auto-detected relative to project root). */
    private File doom2Dir;

    public DosConnectionTab() {
        dosbox = new DOSBox();

        // Auto-detect doom2 directory
        doom2Dir = findDoom2Dir();

        // Mount doom2 directory as D: if found
        if (doom2Dir != null) {
            dosbox.getShell().mountDrive(3, doom2Dir.getAbsolutePath()); // D:
        }

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

    /**
     * Find the doom2 directory relative to the project or working directory.
     */
    private File findDoom2Dir() {
        // Try relative to working directory
        String[] candidates = {
                "doom2",
                "DOOM2",
                "Doom2",
                "../doom2",
                "games/doom2",
        };
        for (String path : candidates) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                File exe = new File(dir, "DOOM2.EXE");
                if (!exe.exists()) exe = new File(dir, "doom2.exe");
                if (exe.exists()) return dir.getAbsoluteFile();
            }
        }
        return null;
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

            // Show Doom 2 availability info
            if (doom2Dir != null) {
                shell.printLine("Doom 2 gefunden: " + doom2Dir.getAbsolutePath());
                shell.printLine("Gemountet als D: - Tippe D: dann DOOM2 zum Starten.");
                shell.printLine("Oder nutze den \uD83C\uDFAE Doom 2 Button in der Toolbar.");
                shell.printLine("");
            }

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

        // ── Doom 2 quick-launch button ──────────────────────
        JButton doom2Btn = new JButton("\uD83C\uDFAE Doom 2");
        doom2Btn.setToolTipText("Doom 2 im Java-Emulator starten");
        doom2Btn.addActionListener(e -> launchDoom2());
        toolbar.add(doom2Btn);

        // ── Emulator status indicator ───────────────────────
        toolbar.add(Box.createHorizontalStrut(16));
        JLabel statusLabel = new JLabel();
        statusLabel.setText("\u2705 Java-Emulator");
        statusLabel.setToolTipText("x86 Real Mode Emulation — kein natives DOSBox noetig");
        statusLabel.setForeground(new Color(0, 128, 0));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        toolbar.add(statusLabel);

        return toolbar;
    }

    /**
     * Launch Doom 2 via the built-in Java CPU emulator.
     */
    private void launchDoom2() {
        if (doom2Dir == null) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Doom 2 Verzeichnis nicht gefunden!\n\n"
                            + "Erwartet: doom2/DOOM2.EXE relativ zum Projektverzeichnis.",
                    "Doom 2 nicht gefunden",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Execute via the DOS shell (which uses the Java emulator)
        DosShell shell = dosbox.getShell();
        shell.printLine("");
        shell.printLine("=== DOOM 2: Hell on Earth ===");
        shell.printLine("Ausfuehrung im Java-Emulator...");
        shell.printLine("");

        // Switch to D: drive and run DOOM2.EXE through the shell
        Thread launchThread = new Thread(() -> {
            shell.executeCommand("D:");
            shell.executeCommand("DOOM2");
        }, "Doom2-Launch");
        launchThread.setDaemon(true);
        launchThread.start();
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));

        String statusText = "JDOSBox \u2013 Java CPU Emulator (x86 Real Mode)";
        if (doom2Dir != null) {
            statusText += "  |  Doom 2: " + doom2Dir.getAbsolutePath();
        }
        statusBar.add(new JLabel(statusText));

        return statusBar;
    }

    // ── ConnectionTab / Bookmarkable implementation ──────────

    @Override
    public String getTitle() {
        return "\uD83D\uDCBB DOS";
    }

    @Override
    public String getTooltip() {
        return "DOS-Terminal (JDOSBox) – Java CPU Emulator";
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

        JMenuItem doom2Item = new JMenuItem("\uD83C\uDFAE Doom 2 starten");
        doom2Item.addActionListener(e -> launchDoom2());

        JMenuItem resetItem = new JMenuItem("\u21BB DOS zur\u00FCcksetzen");
        resetItem.addActionListener(e -> {
            dosbox.getMemory().reset();
            for (int i = 0; i < Int10Handler.ROWS * Int10Handler.COLS; i++) {
                dosbox.getMemory().writeByte(Memory.TEXT_VIDEO_START + i * 2, 0x20);
                dosbox.getMemory().writeByte(Memory.TEXT_VIDEO_START + i * 2 + 1, 0x07);
            }
            startShell();
        });

        JMenuItem closeItem = new JMenuItem("\u274C Tab schlie\u00DFen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(doom2Item);
        menu.addSeparator();
        menu.add(resetItem);
        menu.addSeparator();
        menu.add(closeItem);
        return menu;
    }
}
