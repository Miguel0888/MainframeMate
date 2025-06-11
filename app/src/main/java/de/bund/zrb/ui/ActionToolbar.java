package de.bund.zrb.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.ui.commands.CommandRegistry;
import de.bund.zrb.ui.dto.ToolbarButtonConfig;
import de.bund.zrb.ui.dto.ToolbarConfig;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ActionToolbar extends JToolBar {

    private final MainframeContext context;
    private ToolbarConfig config;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ActionToolbar(MainframeContext context) {
        this.context = context;
        setFloatable(false);

        loadToolbarSettings();
        rebuildButtons();
    }

    private void rebuildButtons() {
        removeAll();

        JPanel leftPanel = new ReorderablePanel(config);
        for (ToolbarButtonConfig btnCfg : config.buttons) {
            CommandRegistry.getById(btnCfg.id).ifPresent(cmd -> {
                JButton btn = new JButton(btnCfg.icon);
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setToolTipText(cmd.getLabel());
                btn.setPreferredSize(new Dimension(config.buttonSizePx, config.buttonSizePx));

                int fontSize = (int) (config.buttonSizePx * config.fontSizeRatio);
                btn.setFont(btn.getFont().deriveFont((float) fontSize));
                btn.setFocusPainted(false);

                btn.addActionListener(e -> cmd.perform());
                leftPanel.add(btn);
            });
        }

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton configBtn = new JButton("⚙");
        configBtn.setMargin(new Insets(0, 0, 0, 0));
        configBtn.setToolTipText("Toolbar anpassen");
        configBtn.setPreferredSize(new Dimension(config.buttonSizePx, config.buttonSizePx));
        configBtn.addActionListener(e -> openConfigDialog());

        int fontSize = (int) (config.buttonSizePx * config.fontSizeRatio);
        configBtn.setFont(configBtn.getFont().deriveFont((float) fontSize));
        configBtn.setFocusPainted(false);

        rightPanel.add(configBtn);

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        revalidate();
        repaint();
    }

    private void openConfigDialog() {
        List<MenuCommand> all = new ArrayList<>(CommandRegistry.getAll());
        Map<MenuCommand, JCheckBox> checkboxes = new LinkedHashMap<>();
        Map<MenuCommand, JComboBox<String>> iconSelectors = new LinkedHashMap<>();

        JPanel commandPanel = new JPanel(new GridLayout(0, 1));
        for (MenuCommand cmd : all) {
            JPanel line = new JPanel(new BorderLayout(4, 0));
            JCheckBox box = new JCheckBox(cmd.getLabel(), isCommandActive(cmd.getId()));

            JComboBox<String> iconCombo = new JComboBox<>(getSimpleIconSuggestions());
            iconCombo.setEditable(true);
            iconCombo.setSelectedItem(getIconFor(cmd.getId()));
            iconCombo.setPreferredSize(new Dimension(48, 24));
            iconSelectors.put(cmd, iconCombo);

            checkboxes.put(cmd, box);
            line.add(box, BorderLayout.CENTER);
            line.add(iconCombo, BorderLayout.EAST);
            commandPanel.add(line);
        }

        JPanel fullPanel = new JPanel(new BorderLayout(8, 8));
        fullPanel.add(new JScrollPane(commandPanel), BorderLayout.CENTER);

        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sizePanel.add(new JLabel("Buttongröße:"));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(config.buttonSizePx, 24, 128, 4));
        sizePanel.add(sizeSpinner);
        sizePanel.add(new JLabel("Schrift %:"));
        JSpinner ratioSpinner = new JSpinner(new SpinnerNumberModel(config.fontSizeRatio, 0.3, 1.0, 0.05));
        sizePanel.add(ratioSpinner);

        fullPanel.add(sizePanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, fullPanel,
                "Toolbar konfigurieren", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            config.buttons.clear();
            for (Map.Entry<MenuCommand, JCheckBox> entry : checkboxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    String icon = Objects.toString(iconSelectors.get(entry.getKey()).getSelectedItem(), "").trim();
                    config.buttons.add(new ToolbarButtonConfig(entry.getKey().getId(), icon));
                }
            }
            config.buttonSizePx = (Integer) sizeSpinner.getValue();
            config.fontSizeRatio = ((Double) ratioSpinner.getValue()).floatValue();

            saveToolbarSettings();
            rebuildButtons();
        }
    }

    private boolean isCommandActive(String id) {
        return config.buttons.stream().anyMatch(b -> b.id.equals(id));
    }

    private String getIconFor(String id) {
        return config.buttons.stream()
                .filter(b -> b.id.equals(id))
                .map(b -> b.icon)
                .findFirst()
                .orElse("🔘");
    }

    private void loadToolbarSettings() {
        Path file = Paths.get(System.getProperty("user.home"), ".mainframemate", "toolbar.json");
        if (!Files.exists(file)) {
            config = new ToolbarConfig();
            config.buttonSizePx = 48;
            config.fontSizeRatio = 0.75f;
            config.buttons = new ArrayList<>();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            config = gson.fromJson(reader, ToolbarConfig.class);
        } catch (IOException e) {
            System.err.println("⚠️ Fehler beim Laden der Toolbar-Konfiguration: " + e.getMessage());
            config = new ToolbarConfig();
        }
    }

    private void saveToolbarSettings() {
        Path file = Paths.get(System.getProperty("user.home"), ".mainframemate", "toolbar.json");
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            System.err.println("⚠️ Fehler beim Speichern der Toolbar-Konfiguration: " + e.getMessage());
        }
    }

    private String[] getSimpleIconSuggestions() {
            return new String[] {
                    // System & Dateien
                    "💾", "📁", "📂", "📄", "📃", "📜", "🗃", "🗄", "📇", "📑", "📋", "🗂", "📦", "📬", "📮", "📬", "📪", "📭",

                    // Aktionen
                    "✔", "❌", "✖", "✅", "✳", "✴", "➕", "➖", "➗", "✂", "🔀", "🔁", "🔂", "🔄", "🔃", "🔽", "🔼", "⬅", "➡", "⬆", "⬇",

                    // Navigation
                    "🔙", "🔚", "🔛", "🔜", "🔝", "⬅", "➡", "⏮", "⏭", "⏫", "⏬", "⏪", "⏩",

                    // Status / Anzeigen
                    "🆗", "🆕", "🆙", "🆒", "🆓", "🆖", "🈚", "🈶", "🈸", "🈺", "🈹", "🈯",

                    // Zeit
                    "⏰", "⏱", "⏲", "🕛", "🕧", "🕐", "🕜", "🕑", "🕝", "🕒", "🕞", "🕓", "🕟", "🕔", "🕠", "🕕", "🕡", "🕖", "🕢", "🕗", "🕣", "🕘", "🕤", "🕙", "🕥", "🕚", "🕦", "🕮",

                    // Kommunikation
                    "📩", "📨", "📧", "📫", "📪", "📬", "📭", "📮", "✉", "🔔", "🔕", "📢", "📣", "📡",

                    // Werkzeuge
                    "🔧", "🔨", "🪛", "🪚", "🛠", "🧰", "🔩", "⚙", "🧲", "🔗", "📎", "🖇",

                    // Texteingabe / Bearbeitung
                    "📝", "✏", "✒", "🖊", "🖋", "🖌", "🔤", "🔡", "🔠", "🔣", "🔠",

                    // Sonstiges Nützliches
                    "🔍", "🔎", "🔒", "🔓", "🔑", "🗝", "📌", "📍", "📏", "📐", "📊", "📈", "📉", "📅", "📆", "🗓", "📇", "🧾", "📖", "📚",

                    // Personen-/Datenkontext
                    "🧑", "👤", "👥", "🧠", "🦷", "🫀", "🫁",

                    // Code / IT
                    "💻", "🖥", "🖨", "⌨", "🖱", "🖲", "💽", "💾", "💿", "📀", "🧮", "📡",

                    // Hilfe / Info / System
                    "ℹ", "❓", "❗", "‼", "⚠", "🚫", "🔞", "♻", "⚡", "🔥", "💡", "🔋", "🔌", "🧯",

                    // Symbole / Stil
                    "🔘", "🔴", "🟢", "🟡", "🟠", "🔵", "🟣", "⚫", "⚪", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "⬛", "⬜",

                    // Buchstaben-/Zahlenrahmen
                    "🅰", "🅱", "🆎", "🅾", "🔠", "🔢", "🔣", "🔤"
            };
    }

    /**
     * Panel, das die Buttons enthält und Drag-and-Drop-Reihenfolgeänderung erlaubt.
     */
    private class ReorderablePanel extends JPanel {
        public ReorderablePanel(ToolbarConfig config) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 2));
            setTransferHandler(new ButtonReorderHandler(config));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    Component c = getComponentAt(e.getPoint());
                    if (c instanceof JButton) {
                        getTransferHandler().exportAsDrag(ReorderablePanel.this, e, TransferHandler.MOVE);
                    }
                }
            });
        }
    }

    /**
     * Handler zur Umsetzung von Drag & Drop für Buttons in der Toolbar
     */
    private class ButtonReorderHandler extends TransferHandler {
        private final ToolbarConfig config;

        public ButtonReorderHandler(ToolbarConfig config) {
            this.config = config;
        }

        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        protected Transferable createTransferable(JComponent c) {
            return new StringSelection(""); // Dummy für Swing DnD
        }

        public boolean canImport(TransferSupport support) {
            return support.getComponent() instanceof JPanel;
        }

        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;

            Point dropPoint = support.getDropLocation().getDropPoint();
            JPanel panel = (JPanel) support.getComponent();
            Component dragged = null;
            for (Component c : panel.getComponents()) {
                if (c.getBounds().contains(dropPoint)) {
                    dragged = c;
                    break;
                }
            }

            if (dragged == null) return false;

            int index = panel.getComponentZOrder(dragged);
            if (index < 0) return false;

            JButton dummy = new JButton();
            dummy.setVisible(false);
            panel.add(dummy, index);
            panel.remove(dragged);
            panel.add(dragged, index);

            // Reihenfolge in config.buttons aktualisieren
            List<ToolbarButtonConfig> reordered = new ArrayList<>();
            for (Component comp : panel.getComponents()) {
                if (comp instanceof JButton) {
                    JButton btn = (JButton) comp;
                    String icon = btn.getText();
                    for (ToolbarButtonConfig b : config.buttons) {
                        if (b.icon.equals(icon)) {
                            reordered.add(b);
                            break;
                        }
                    }
                }
            }

            config.buttons = reordered;
            saveToolbarSettings();
            return true;
        }
    }

}
