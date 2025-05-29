package de.bund.zrb.ui;

import de.bund.zrb.ui.commands.CommandRegistry;
import de.zrb.bund.api.Command;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ActionToolbar extends JToolBar {

    private final MainframeContext context;
    private final Set<String> activeCommandIds = new LinkedHashSet<>();
    private int buttonSizePx = 48;

    public ActionToolbar(MainframeContext context) {
        this.context = context;
        setFloatable(false);

        loadToolbarSettings();
        rebuildButtons();
    }

    private void rebuildButtons() {
        removeAll();

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (Command cmd : CommandRegistry.getAll()) {
            if (!activeCommandIds.contains(cmd.getId())) continue;

            JButton btn = new JButton(getIconFor(cmd));
            btn.setMargin(new Insets(0, 0, 0, 0)); // optional
            btn.setToolTipText(cmd.getLabel());
            btn.setPreferredSize(new Dimension(buttonSizePx, buttonSizePx));
            btn.addActionListener(e -> cmd.perform());
            leftPanel.add(btn);
        }

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton config = new JButton("⚙");
        config.setMargin(new Insets(0, 0, 0, 0)); // optional
        config.setToolTipText("Toolbar anpassen");
        config.setPreferredSize(new Dimension(buttonSizePx, buttonSizePx));
        config.addActionListener(e -> openConfigDialog());
        rightPanel.add(config);

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        revalidate();
        repaint();
    }

    private void openConfigDialog() {
        List<Command> all = new ArrayList<>(CommandRegistry.getAll());
        Map<Command, JCheckBox> checkboxes = new LinkedHashMap<>();

        JPanel commandPanel = new JPanel(new GridLayout(0, 1));
        for (Command cmd : all) {
            JCheckBox box = new JCheckBox(cmd.getLabel(), activeCommandIds.contains(cmd.getId()));
            checkboxes.put(cmd, box);
            commandPanel.add(box);
        }

        JPanel fullPanel = new JPanel(new BorderLayout(8, 8));
        fullPanel.add(new JScrollPane(commandPanel), BorderLayout.CENTER);

        // Buttongröße unten ergänzen
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sizePanel.add(new JLabel("Buttongröße:"));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(buttonSizePx, 24, 128, 4));
        sizePanel.add(sizeSpinner);
        fullPanel.add(sizePanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, fullPanel,
                "Toolbar konfigurieren", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            Set<String> selected = checkboxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(e -> e.getKey().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            activeCommandIds.clear();
            activeCommandIds.addAll(selected);

            buttonSizePx = (Integer) sizeSpinner.getValue();

            saveToolbarSettings();
            rebuildButtons();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadToolbarSettings() {
        Path file = Paths.get(System.getProperty("user.home"), ".mainframemate", "toolbar.json");
        if (!Files.exists(file)) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("#size=")) {
                    buttonSizePx = Integer.parseInt(line.substring(6).trim());
                } else {
                    activeCommandIds.add(line.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Laden der Toolbar-Konfiguration: " + e.getMessage());
        }
    }

    private void saveToolbarSettings() {
        Path file = Paths.get(System.getProperty("user.home"), ".mainframemate", "toolbar.json");
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("#size=" + buttonSizePx);
            lines.addAll(activeCommandIds);
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Speichern der Toolbar-Konfiguration: " + e.getMessage());
        }
    }

    private String getIconFor(Command cmd) {
        String id = cmd.getId();
        if (id.contains("connect")) return "🔌";
        if (id.contains("import")) return "📥";
        if (id.contains("save")) return "💾";
        if (id.contains("install")) return "➕";
        if (id.contains("settings")) return "⚙️";
        return "🔘";
    }
}
