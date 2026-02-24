package com.example.toolbarkit.toolbar;

import com.example.toolbarkit.command.ToolbarCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Configure the toolbar:
 * - group commands by prefix (before first '.')
 * - edit icon text (supports U+XXXX / 0xXXXX)
 * - set per-group color and per-button color
 * - set left/right alignment
 * - set positions with stable collision resolution
 * - hide/show commands via a visibility dialog
 */
public class ToolbarConfigDialog extends JDialog {

    private ToolbarConfig initialConfig;
    private final List<ToolbarCommand> allCommands;
    private final String[] iconSuggestions;

    private final JTabbedPane tabs = new JTabbedPane();

    private final Map<String, JTextField> groupColorFields = new LinkedHashMap<String, JTextField>();

    private final Map<ToolbarCommand, JCheckBox> cbEnabled = new LinkedHashMap<ToolbarCommand, JCheckBox>();
    private final Map<ToolbarCommand, JComboBox<String>> cbIcon = new LinkedHashMap<ToolbarCommand, JComboBox<String>>();
    private final Map<ToolbarCommand, JComboBox<String>> cbColor = new LinkedHashMap<ToolbarCommand, JComboBox<String>>();
    private final Map<ToolbarCommand, JCheckBox> cbRight = new LinkedHashMap<ToolbarCommand, JCheckBox>();
    private final Map<ToolbarCommand, JSpinner> spOrder = new LinkedHashMap<ToolbarCommand, JSpinner>();

    private JSpinner spButtonSize;
    private JSpinner spFontRatio;

    private ToolbarConfig result;

    public ToolbarConfigDialog(Window owner,
                               ToolbarConfig currentConfig,
                               List<ToolbarCommand> commands,
                               String[] iconSuggestions) {
        super(owner, "Toolbar konfigurieren", ModalityType.APPLICATION_MODAL);
        this.initialConfig = deepCopyOrInit(currentConfig, commands);
        this.allCommands = new ArrayList<ToolbarCommand>(commands);
        this.iconSuggestions = iconSuggestions != null ? iconSuggestions : new String[0];

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(Math.max(860, getWidth()), Math.max(580, getHeight())));
    }

    public ToolbarConfig showDialog() {
        setVisible(true);
        return result;
    }

    // ---------------- UI ----------------

    private JComponent buildUI() {
        cbEnabled.clear();
        cbIcon.clear();
        cbColor.clear();
        cbRight.clear();
        spOrder.clear();
        groupColorFields.clear();

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel top = new JPanel(new BorderLayout());
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btVisibility = new JButton("Sichtbarkeit…");
        JButton btDefaults = new JButton("Standard laden");
        JButton btLoadAll = new JButton("Alle laden");
        leftTop.add(btVisibility);
        leftTop.add(btDefaults);
        leftTop.add(btLoadAll);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btOk = new JButton("OK");
        JButton btCancel = new JButton("Abbrechen");
        rightTop.add(btOk);
        rightTop.add(btCancel);

        top.add(leftTop, BorderLayout.WEST);
        top.add(rightTop, BorderLayout.EAST);

        tabs.removeAll();

        Map<String, List<ToolbarCommand>> byGroup = groupCommands(filteredCommands());
        for (Map.Entry<String, List<ToolbarCommand>> e : byGroup.entrySet()) {
            String group = e.getKey();
            JPanel panel = buildGroupPanel(group, e.getValue());
            JScrollPane scroll = new JScrollPane(panel);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            tabs.addTab(group, scroll);
        }

        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sizePanel.add(new JLabel("Buttongröße:"));
        spButtonSize = new JSpinner(new SpinnerNumberModel(
                Math.max(24, initialConfig.buttonSizePx), 24, 128, 4));
        sizePanel.add(spButtonSize);

        sizePanel.add(new JLabel("Schrift %:"));
        spFontRatio = new JSpinner(new SpinnerNumberModel(
                (double) Math.max(0.3f, Math.min(1.0f, initialConfig.fontSizeRatio)), 0.3, 1.0, 0.05));
        sizePanel.add(spFontRatio);

        btVisibility.addActionListener(e -> openVisibilityDialog());
        btDefaults.addActionListener(e -> {
            initialConfig = ToolbarDefaults.createInitialConfig(allCommands);
            setContentPane(buildUI());
            revalidate();
            repaint();
            pack();
        });
        btLoadAll.addActionListener(e -> {
            initialConfig = createAllConfig();
            setContentPane(buildUI());
            revalidate();
            repaint();
            pack();
        });
        btOk.addActionListener(e -> onOk());
        btCancel.addActionListener(e -> onCancel());

        root.add(top, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(sizePanel, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildGroupPanel(String group, List<ToolbarCommand> cmds) {
        cmds.sort(new Comparator<ToolbarCommand>() {
            @Override
            public int compare(ToolbarCommand a, ToolbarCommand b) {
                int oa = getOrderFor(a.getId());
                int ob = getOrderFor(b.getId());
                if (oa != ob) return Integer.compare(normalizeOrder(oa), normalizeOrder(ob));
                return Objects.toString(a.getLabel(), Objects.toString(a.getId(), ""))
                        .compareToIgnoreCase(Objects.toString(b.getLabel(), Objects.toString(b.getId(), "")));
            }
        });

        JPanel groupRoot = new JPanel(new BorderLayout(8, 8));

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        head.add(new JLabel("Gruppenfarbe (HEX, optional):"));
        String preset = initialConfig.groupColors.getOrDefault(group, "");
        JTextField tfGroupColor = new JTextField(preset, 10);
        head.add(tfGroupColor);

        JLabel lbPreview = makeColorPreviewLabel(tfGroupColor.getText());
        head.add(lbPreview);

        JButton btPick = makeColorPickerButton(
                () -> tfGroupColor.getText(),
                hex -> tfGroupColor.setText(hex == null ? "" : hex)
        );
        head.add(btPick);
        head.add(new JLabel(" z.B. #FFD700"));

        groupColorFields.put(group, tfGroupColor);
        wireColorFieldPreview(tfGroupColor, lbPreview);

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
        for (ToolbarCommand cmd : cmds) {
            list.add(buildCommandLine(cmd));
        }

        groupRoot.add(head, BorderLayout.NORTH);
        groupRoot.add(list, BorderLayout.CENTER);
        return groupRoot;
    }

    private JPanel buildCommandLine(ToolbarCommand cmd) {
        JPanel line = new JPanel(new BorderLayout(6, 0));

        JCheckBox enabled = new JCheckBox(cmd.getLabel(), isCommandActive(cmd.getId()));
        cbEnabled.put(cmd, enabled);

        JComboBox<String> iconCombo = new JComboBox<String>(iconSuggestions);
        iconCombo.setEditable(true);
        iconCombo.setSelectedItem(getIconFor(cmd.getId()));
        iconCombo.setPreferredSize(new Dimension(120, 38));
        iconCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setFont(c.getFont().deriveFont(20f));
                if (c instanceof JLabel) ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });
        Component ed = iconCombo.getEditor().getEditorComponent();
        ed.setFont(ed.getFont().deriveFont(20f));
        cbIcon.put(cmd, iconCombo);

        JComboBox<String> colorCombo = new JComboBox<String>(new String[] {
                "", "#FF0000", "#00AA00", "#008000", "#FFA500", "#0000FF", "#FFFF00"
        });
        colorCombo.setEditable(true);
        String preHex = getBackgroundHexFor(cmd.getId());
        colorCombo.setSelectedItem(preHex == null ? "" : preHex);
        colorCombo.setPreferredSize(new Dimension(110, 28));
        colorCombo.setRenderer(new ColorCellRenderer());
        setEditorColorPreview(colorCombo);
        cbColor.put(cmd, colorCombo);

        JButton pickBtn = makeColorPickerButton(
                () -> Objects.toString(colorCombo.getSelectedItem(), "").trim(),
                hex -> colorCombo.setSelectedItem(hex == null ? "" : hex)
        );

        boolean preRight = initialConfig.rightSideIds.contains(cmd.getId());
        JCheckBox rightChk = new JCheckBox("rechts", preRight);
        cbRight.put(cmd, rightChk);

        int order = getOrderFor(cmd.getId());
        int initial = order > 0 ? order : suggestNextOrder();
        JSpinner sp = new JSpinner(new SpinnerNumberModel(initial, 1, 9999, 1));
        sp.setPreferredSize(new Dimension(72, 28));
        spOrder.put(cmd, sp);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(new JLabel("Pos:"));
        right.add(sp);
        right.add(iconCombo);
        right.add(colorCombo);
        right.add(pickBtn);
        right.add(rightChk);

        line.add(enabled, BorderLayout.CENTER);
        line.add(right, BorderLayout.EAST);
        return line;
    }

    // ---------------- Actions ----------------

    private void onOk() {
        ToolbarConfig cfg = deepCopyOrInit(initialConfig, allCommands);
        cfg.buttons.clear();
        LinkedHashSet<String> newRight = new LinkedHashSet<String>();

        for (Map.Entry<String, JTextField> e : groupColorFields.entrySet()) {
            String grp = e.getKey();
            String hex = normalizeHex(e.getValue().getText());
            if (hex == null) cfg.groupColors.remove(grp);
            else cfg.groupColors.put(grp, hex);
        }

        class Row {
            String id; String icon; String hex; int requestedOrder; boolean right;
            Row(String id, String icon, String hex, int ord, boolean r) {
                this.id = id; this.icon = icon; this.hex = hex; this.requestedOrder = ord; this.right = r;
            }
        }

        List<Row> rows = new ArrayList<Row>();
        for (ToolbarCommand cmd : cbEnabled.keySet()) {
            if (!cbEnabled.get(cmd).isSelected()) continue;

            String rawIcon = Objects.toString(cbIcon.get(cmd).getSelectedItem(), "").trim();
            String icon = normalizeIcon(rawIcon);
            String hex = normalizeHex(Objects.toString(cbColor.get(cmd).getSelectedItem(), "").trim());
            int pos = ((Number) spOrder.get(cmd).getValue()).intValue();
            boolean right = cbRight.get(cmd).isSelected();

            rows.add(new Row(cmd.getId(), icon, hex, pos, right));
        }

        rows.sort(new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int c = Integer.compare(normalizeOrder(a.requestedOrder), normalizeOrder(b.requestedOrder));
                if (c != 0) return c;
                String la = labelOf(a.id);
                String lb = labelOf(b.id);
                return la.compareToIgnoreCase(lb);
            }
        });

        TreeSet<Integer> used = new TreeSet<Integer>();
        AtomicInteger maxAssigned = new AtomicInteger(0);

        for (Row r : rows) {
            int want = Math.max(1, r.requestedOrder);
            int assign = want;
            while (used.contains(assign)) assign++;
            used.add(assign);
            maxAssigned.set(Math.max(maxAssigned.get(), assign));

            ToolbarButtonConfig tbc = new ToolbarButtonConfig(r.id, r.icon);
            tbc.order = Integer.valueOf(assign);
            tbc.backgroundHex = r.hex;
            cfg.buttons.add(tbc);

            if (r.right) newRight.add(r.id);
        }

        cfg.buttonSizePx = ((Number) spButtonSize.getValue()).intValue();
        cfg.fontSizeRatio = ((Number) spFontRatio.getValue()).floatValue();
        cfg.rightSideIds = newRight;

        cfg.hiddenCommandIds = new LinkedHashSet<String>(initialConfig.hiddenCommandIds);

        this.result = cfg;
        dispose();
    }

    private void onCancel() {
        this.result = null;
        dispose();
    }

    // ---------------- Visibility dialog ----------------

    private void openVisibilityDialog() {
        LinkedHashSet<String> hidden = new LinkedHashSet<String>();
        if (initialConfig.hiddenCommandIds != null) hidden.addAll(initialConfig.hiddenCommandIds);

        JDialog dlg = new JDialog(this, "Buttons ein-/ausblenden", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        List<ToolbarCommand> sorted = new ArrayList<ToolbarCommand>(allCommands);
        sorted.sort(new Comparator<ToolbarCommand>() {
            @Override
            public int compare(ToolbarCommand a, ToolbarCommand b) {
                String la = Objects.toString(a.getLabel(), Objects.toString(a.getId(), ""));
                String lb = Objects.toString(b.getLabel(), Objects.toString(b.getId(), ""));
                return la.compareToIgnoreCase(lb);
            }
        });

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        Map<ToolbarCommand, JCheckBox> checks = new LinkedHashMap<ToolbarCommand, JCheckBox>();

        for (ToolbarCommand cmd : sorted) {
            String id = Objects.toString(cmd.getId(), "");
            boolean visible = !hidden.contains(id);
            JCheckBox cb = new JCheckBox(cmd.getLabel() + "  (" + id + ")", visible);
            checks.put(cmd, cb);
            list.add(cb);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btAllOn = new JButton("Alle anzeigen");
        JButton btAllOff = new JButton("Alle ausblenden");
        top.add(btAllOn);
        top.add(btAllOff);

        btAllOn.addActionListener(e -> {
            for (JCheckBox cb : checks.values()) cb.setSelected(true);
        });
        btAllOff.addActionListener(e -> {
            for (JCheckBox cb : checks.values()) cb.setSelected(false);
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btOk = new JButton("OK");
        JButton btCancel = new JButton("Abbrechen");
        bottom.add(btOk);
        bottom.add(btCancel);

        btCancel.addActionListener(e -> dlg.dispose());
        btOk.addActionListener(e -> {
            LinkedHashSet<String> newHidden = new LinkedHashSet<String>();
            for (Map.Entry<ToolbarCommand, JCheckBox> en : checks.entrySet()) {
                String id = Objects.toString(en.getKey().getId(), "");
                if (!en.getValue().isSelected()) newHidden.add(id);
            }

            if (initialConfig.hiddenCommandIds == null) {
                initialConfig.hiddenCommandIds = new LinkedHashSet<String>();
            }
            initialConfig.hiddenCommandIds.clear();
            initialConfig.hiddenCommandIds.addAll(newHidden);

            dlg.dispose();

            setContentPane(buildUI());
            revalidate();
            repaint();
            pack();
        });

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(Math.max(560, dlg.getWidth()), Math.max(420, dlg.getHeight())));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ---------------- Model helpers ----------------

    private ToolbarConfig deepCopyOrInit(ToolbarConfig in, List<ToolbarCommand> commands) {
        if (in == null) {
            return ToolbarDefaults.createInitialConfig(commands);
        }
        ToolbarConfig cfg = new ToolbarConfig();
        cfg.buttonSizePx = (in.buttonSizePx > 0) ? in.buttonSizePx : 48;
        cfg.fontSizeRatio = (in.fontSizeRatio > 0f) ? in.fontSizeRatio : 0.75f;

        cfg.buttons = new ArrayList<ToolbarButtonConfig>();
        if (in.buttons != null) {
            for (ToolbarButtonConfig b : in.buttons) {
                ToolbarButtonConfig nb = new ToolbarButtonConfig(b.id, b.iconText);
                nb.order = b.order;
                nb.backgroundHex = b.backgroundHex;
                cfg.buttons.add(nb);
            }
        }

        cfg.rightSideIds = new LinkedHashSet<String>();
        if (in.rightSideIds != null) cfg.rightSideIds.addAll(in.rightSideIds);

        cfg.groupColors = new LinkedHashMap<String, String>();
        if (in.groupColors != null) cfg.groupColors.putAll(in.groupColors);

        cfg.hiddenCommandIds = new LinkedHashSet<String>();
        if (in.hiddenCommandIds != null) cfg.hiddenCommandIds.addAll(in.hiddenCommandIds);

        return cfg;
    }

    private List<ToolbarCommand> filteredCommands() {
        List<ToolbarCommand> list = new ArrayList<ToolbarCommand>();
        Set<String> hidden = initialConfig.hiddenCommandIds != null
                ? initialConfig.hiddenCommandIds
                : new LinkedHashSet<String>();

        for (ToolbarCommand mc : allCommands) {
            String id = Objects.toString(mc.getId(), "");
            if (!hidden.contains(id)) {
                list.add(mc);
            }
        }
        return list;
    }

    private Map<String, List<ToolbarCommand>> groupCommands(List<ToolbarCommand> cmds) {
        Map<String, List<ToolbarCommand>> m = new LinkedHashMap<String, List<ToolbarCommand>>();
        for (ToolbarCommand c : cmds) {
            String id = Objects.toString(c.getId(), "");
            int dot = id.indexOf('.');
            String group = (dot > 0) ? id.substring(0, dot) : "(ohne)";
            List<ToolbarCommand> l = m.get(group);
            if (l == null) {
                l = new ArrayList<ToolbarCommand>();
                m.put(group, l);
            }
            l.add(c);
        }
        return m;
    }

    private boolean isCommandActive(String id) {
        if (initialConfig.buttons == null) return false;
        for (ToolbarButtonConfig b : initialConfig.buttons) {
            if (Objects.equals(b.id, id)) return true;
        }
        return false;
    }

    private String getIconFor(String id) {
        if (initialConfig.buttons != null) {
            for (ToolbarButtonConfig b : initialConfig.buttons) {
                if (Objects.equals(id, b.id)) return b.iconText;
            }
        }
        return ToolbarDefaults.defaultIconFor(id);
    }

    private String getBackgroundHexFor(String id) {
        if (initialConfig.buttons != null) {
            for (ToolbarButtonConfig b : initialConfig.buttons) {
                if (Objects.equals(id, b.id)) return b.backgroundHex;
            }
        }
        return null;
    }

    private int getOrderFor(String id) {
        if (initialConfig.buttons == null) return 0;
        for (ToolbarButtonConfig b : initialConfig.buttons) {
            if (Objects.equals(id, b.id)) {
                return (b.order == null) ? 0 : b.order.intValue();
            }
        }
        return 0;
    }

    private int suggestNextOrder() {
        int max = 0;
        if (initialConfig.buttons != null) {
            for (ToolbarButtonConfig b : initialConfig.buttons) {
                if (b.order != null) max = Math.max(max, b.order.intValue());
            }
        }
        return max + 1;
    }

    private int normalizeOrder(int ord) {
        return (ord <= 0) ? Integer.MAX_VALUE : ord;
    }

    private String labelOf(String id) {
        for (ToolbarCommand mc : allCommands) {
            if (Objects.equals(mc.getId(), id)) return Objects.toString(mc.getLabel(), id);
        }
        return id;
    }

    private String normalizeIcon(String raw) {
        if (raw == null) return "●";
        String s = raw.trim();
        if (s.isEmpty()) return "●";
        String up = s.toUpperCase(Locale.ROOT);
        if (up.matches("^([U]\\+|0X)?[0-9A-F]{4,6}$")) {
            String hex = up.replace("U+", "").replace("0X", "");
            try {
                int cp = Integer.parseInt(hex, 16);
                return new String(Character.toChars(cp));
            } catch (Exception ignore) {
                return s;
            }
        }
        return s;
    }

    private String normalizeHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        if (!s.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    // ---------------- Color UI helpers ----------------

    private static final class ColorCellRenderer extends DefaultListCellRenderer {
        private static final Icon EMPTY_ICON = new ColorIcon(null, 14, 14);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String s = Objects.toString(value, "").trim();
            String hex = s.isEmpty() ? null : normalizeHexStatic(s);
            Color c = toColor(hex);
            lbl.setText(hex == null ? "(auto)" : hex);
            lbl.setIcon(c == null ? EMPTY_ICON : new ColorIcon(c, 14, 14));
            lbl.setHorizontalTextPosition(SwingConstants.RIGHT);
            lbl.setIconTextGap(8);
            lbl.setToolTipText(hex == null
                    ? "leer = Gruppenfarbe (falls gesetzt), sonst Standard"
                    : hex);
            return lbl;
        }
    }

    private JButton makeColorPickerButton(Supplier<String> currentHexSupplier, Consumer<String> hexConsumer) {
        JButton btn = new JButton("■");
        btn.setMargin(new Insets(0, 0, 0, 0));
        Dimension d = new Dimension(24, 24);
        btn.setPreferredSize(d);
        btn.setMinimumSize(d);
        btn.setMaximumSize(d);
        btn.setFocusable(false);
        btn.setToolTipText("Farbe wählen…");
        btn.addActionListener(e -> {
            String hex = normalizeHex(currentHexSupplier.get());
            Color base = toColor(hex);
            Color chosen = JColorChooser.showDialog(this, "Farbe wählen", base == null ? Color.WHITE : base);
            if (chosen != null) {
                String out = toHex(chosen);
                hexConsumer.accept(out);
            }
        });
        return btn;
    }

    private JLabel makeColorPreviewLabel(String hex) {
        JLabel l = new JLabel("  ");
        l.setOpaque(true);
        l.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 80)));
        l.setPreferredSize(new Dimension(28, 18));
        applyPreviewColor(l, hex);
        return l;
    }

    private void wireColorFieldPreview(JTextField tf, JLabel preview) {
        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { applyPreviewColor(preview, tf.getText()); }
        });
    }

    private void setEditorColorPreview(JComboBox<String> combo) {
        Component editor = combo.getEditor().getEditorComponent();
        if (!(editor instanceof JTextField)) return;
        JTextField tf = (JTextField) editor;
        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
            private void update() {
                String hex = normalizeHex(tf.getText());
                Color c = toColor(hex);
                if (hex == null || c == null) {
                    tf.setBackground(UIManager.getColor("TextField.background"));
                    tf.setForeground(UIManager.getColor("TextField.foreground"));
                    return;
                }
                tf.setBackground(c);
                tf.setForeground(contrastColor(c));
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                String hex = normalizeHex(tf.getText());
                Color c = toColor(hex);
                if (hex != null && c != null) {
                    tf.setBackground(c);
                    tf.setForeground(contrastColor(c));
                }
            }
        });
    }

    private static void applyPreviewColor(JLabel l, String rawHex) {
        String hex = normalizeHexStatic(rawHex);
        Color c = toColor(hex);
        if (c == null) {
            l.setBackground(UIManager.getColor("Panel.background"));
            l.setToolTipText("leer = Gruppenfarbe deaktiviert");
        } else {
            l.setBackground(c);
            l.setToolTipText(hex);
        }
    }

    // ---------------- Color utils ----------------

    private static String normalizeHexStatic(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        if (!s.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private static Color toColor(String hex) {
        if (hex == null) return null;
        try {
            String h = hex.substring(1);
            if (h.length() == 3) {
                int r = Integer.parseInt(h.substring(0, 1) + h.substring(0, 1), 16);
                int g = Integer.parseInt(h.substring(1, 2) + h.substring(1, 2), 16);
                int b = Integer.parseInt(h.substring(2, 3) + h.substring(2, 3), 16);
                return new Color(r, g, b);
            } else if (h.length() == 6) {
                int rgb = Integer.parseInt(h, 16);
                return new Color(rgb);
            } else if (h.length() == 8) {
                long v = Long.parseLong(h, 16);
                int a = (int) ((v >> 24) & 0xFF);
                int r = (int) ((v >> 16) & 0xFF);
                int g = (int) ((v >> 8) & 0xFF);
                int b = (int) (v & 0xFF);
                return new Color(r, g, b, a);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static String toHex(Color c) {
        if (c == null) return null;
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color contrastColor(Color bg) {
        double luma = 0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue();
        return (luma < 140) ? Color.WHITE : Color.BLACK;
    }

    private static final class ColorIcon implements Icon {
        private final Color color;
        private final int w, h;

        ColorIcon(Color c, int w, int h) {
            this.color = c;
            this.w = w;
            this.h = h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color == null ? new Color(0, 0, 0, 0) : color);
                g2.fillRoundRect(x, y, w, h, 3, 3);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.drawRoundRect(x, y, w, h, 3, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() { return w; }

        @Override
        public int getIconHeight() { return h; }
    }

    private ToolbarConfig createAllConfig() {
        ToolbarConfig cfg = new ToolbarConfig();
        cfg.buttonSizePx = (initialConfig != null && initialConfig.buttonSizePx > 0) ? initialConfig.buttonSizePx : 48;
        cfg.fontSizeRatio = (initialConfig != null && initialConfig.fontSizeRatio > 0f) ? initialConfig.fontSizeRatio : 0.75f;

        cfg.groupColors = new LinkedHashMap<String, String>(initialConfig != null && initialConfig.groupColors != null
                ? initialConfig.groupColors
                : new LinkedHashMap<String, String>());

        cfg.rightSideIds = new LinkedHashSet<String>();

        List<ToolbarCommand> cmds = new ArrayList<ToolbarCommand>(allCommands);
        cmds.sort(new Comparator<ToolbarCommand>() {
            @Override
            public int compare(ToolbarCommand a, ToolbarCommand b) {
                String la = Objects.toString(a.getLabel(), Objects.toString(a.getId(), ""));
                String lb = Objects.toString(b.getLabel(), Objects.toString(b.getId(), ""));
                return la.compareToIgnoreCase(lb);
            }
        });

        cfg.buttons = new ArrayList<ToolbarButtonConfig>();
        int pos = 1;
        for (ToolbarCommand mc : cmds) {
            String id = Objects.toString(mc.getId(), "");
            ToolbarButtonConfig b = new ToolbarButtonConfig(id, ToolbarDefaults.defaultIconFor(id));
            b.order = Integer.valueOf(pos++);
            b.backgroundHex = ToolbarDefaults.defaultBackgroundHexFor(id);
            cfg.buttons.add(b);
        }
        cfg.hiddenCommandIds = new LinkedHashSet<String>();
        return cfg;
    }
}
