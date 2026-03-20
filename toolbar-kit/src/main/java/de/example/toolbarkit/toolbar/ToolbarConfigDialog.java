package de.example.toolbarkit.toolbar;

import de.example.toolbarkit.command.ToolbarCommand;

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
 * Configure the toolbar in a modern settings-style dialog:
 * <ul>
 *   <li>Category sidebar on the left (instead of tabs)</li>
 *   <li>Card-style entries per command with large icon preview</li>
 *   <li>Editable icon text field + icon suggestions dropdown + full Unicode Char Picker</li>
 *   <li>Per-group and per-button color, left/right alignment, position</li>
 * </ul>
 */
public class ToolbarConfigDialog extends JDialog {

    private ToolbarConfig initialConfig;
    private final List<ToolbarCommand> allCommands;
    private final String[] iconSuggestions;

    // ── State maps ──
    private final Map<ToolbarCommand, JCheckBox> cbEnabled = new LinkedHashMap<ToolbarCommand, JCheckBox>();
    private final Map<ToolbarCommand, JTextField> iconFields = new LinkedHashMap<ToolbarCommand, JTextField>();
    private final Map<ToolbarCommand, JLabel> iconPreviews = new LinkedHashMap<ToolbarCommand, JLabel>();
    private final Map<ToolbarCommand, JComboBox<String>> cbColor = new LinkedHashMap<ToolbarCommand, JComboBox<String>>();
    private final Map<ToolbarCommand, JCheckBox> cbRight = new LinkedHashMap<ToolbarCommand, JCheckBox>();
    private final Map<ToolbarCommand, JSpinner> spOrder = new LinkedHashMap<ToolbarCommand, JSpinner>();
    private final Map<String, JTextField> groupColorFields = new LinkedHashMap<String, JTextField>();

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
        setMinimumSize(new Dimension(Math.max(980, getWidth()), Math.max(640, getHeight())));
    }

    public ToolbarConfig showDialog() {
        setVisible(true);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════════════════════════

    private JComponent buildUI() {
        cbEnabled.clear();
        iconFields.clear();
        iconPreviews.clear();
        cbColor.clear();
        cbRight.clear();
        spOrder.clear();
        groupColorFields.clear();

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        // ── Top bar ─────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout());
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btVisibility = new JButton("Filter\u2026");
        JButton btDefaults = new JButton("Standard laden");
        JButton btLoadAll = new JButton("Alle laden");
        leftTop.add(btVisibility);
        leftTop.add(btDefaults);
        leftTop.add(btLoadAll);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton btOk = new JButton("OK");
        JButton btCancel = new JButton("Abbrechen");
        rightTop.add(btOk);
        rightTop.add(btCancel);
        top.add(leftTop, BorderLayout.WEST);
        top.add(rightTop, BorderLayout.EAST);

        // ── Categories + command cards ──────────────────────────
        Map<String, List<ToolbarCommand>> byGroup = groupCommands(filteredCommands());

        DefaultListModel<String> catModel = new DefaultListModel<String>();
        for (String g : byGroup.keySet()) {
            catModel.addElement(g);
        }

        JList<String> catList = new JList<String>(catModel);
        catList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (catModel.size() > 0) catList.setSelectedIndex(0);
        catList.setFixedCellHeight(36);
        catList.setCellRenderer(new CategoryListRenderer());

        JScrollPane catScroll = new JScrollPane(catList);
        catScroll.setPreferredSize(new Dimension(155, 0));
        catScroll.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 0, new Color(0xCCCCCC)));

        JPanel cardPanel = new JPanel(new CardLayout());
        cardPanel.setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC)));

        for (Map.Entry<String, List<ToolbarCommand>> e : byGroup.entrySet()) {
            JPanel gp = buildGroupPanel(e.getKey(), e.getValue());
            JScrollPane scroll = new JScrollPane(gp);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            cardPanel.add(scroll, e.getKey());
        }

        catList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = catList.getSelectedValue();
                if (sel != null) {
                    ((CardLayout) cardPanel.getLayout()).show(cardPanel, sel);
                }
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.add(catScroll, BorderLayout.WEST);
        centerPanel.add(cardPanel, BorderLayout.CENTER);

        // ── Bottom: size settings ───────────────────────────────
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.add(new JLabel("Buttongr\u00f6\u00dfe:"));
        spButtonSize = new JSpinner(new SpinnerNumberModel(
                Math.max(24, initialConfig.buttonSizePx), 24, 128, 4));
        bottom.add(spButtonSize);
        bottom.add(new JLabel("Schrift %:"));
        spFontRatio = new JSpinner(new SpinnerNumberModel(
                (double) Math.max(0.3f, Math.min(1.0f, initialConfig.fontSizeRatio)),
                0.3, 1.0, 0.05));
        bottom.add(spFontRatio);

        // ── Listeners ───────────────────────────────────────────
        btVisibility.addActionListener(e -> openVisibilityDialog());
        btDefaults.addActionListener(e -> {
            initialConfig = ToolbarDefaults.createInitialConfig(allCommands);
            rebuildDialog();
        });
        btLoadAll.addActionListener(e -> {
            initialConfig = createAllConfig();
            rebuildDialog();
        });
        btOk.addActionListener(e -> onOk());
        btCancel.addActionListener(e -> onCancel());

        root.add(top, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void rebuildDialog() {
        setContentPane(buildUI());
        revalidate();
        repaint();
        pack();
    }

    // ── Category sidebar renderer ───────────────────────────────

    private static class CategoryListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
            l.setBorder(new EmptyBorder(6, 12, 6, 12));
            if (isSelected) {
                l.setBackground(new Color(0x2979FF));
                l.setForeground(Color.WHITE);
            }
            return l;
        }
    }

    // ── Group panel (header + command cards) ────────────────────

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

        JPanel groupRoot = new JPanel(new BorderLayout(0, 0));
        groupRoot.setBackground(new Color(0xF5F5F5));

        // ── Group header with color ──
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        head.setBackground(new Color(0xEEEEEE));
        head.setBorder(new EmptyBorder(4, 10, 4, 10));
        head.add(new JLabel("Gruppenfarbe:"));
        String preset = initialConfig.groupColors != null
                ? initialConfig.groupColors.getOrDefault(group, "")
                : "";
        JTextField tfGroupColor = new JTextField(preset, 8);
        head.add(tfGroupColor);

        JLabel lbPreview = makeColorPreviewLabel(tfGroupColor.getText());
        head.add(lbPreview);

        JButton btPick = makeColorPickerButton(
                () -> tfGroupColor.getText(),
                hex -> tfGroupColor.setText(hex == null ? "" : hex));
        head.add(btPick);

        groupColorFields.put(group, tfGroupColor);
        wireColorFieldPreview(tfGroupColor, lbPreview);

        // ── Command cards ──
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(new Color(0xF5F5F5));

        for (ToolbarCommand cmd : cmds) {
            list.add(buildCommandCard(cmd));
        }
        // Filler to push cards to top
        list.add(Box.createVerticalGlue());

        groupRoot.add(head, BorderLayout.NORTH);
        groupRoot.add(list, BorderLayout.CENTER);
        return groupRoot;
    }

    // ── Single command card ─────────────────────────────────────

    private JPanel buildCommandCard(final ToolbarCommand cmd) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE0E0E0)),
                new EmptyBorder(8, 10, 8, 10)));
        card.setBackground(Color.WHITE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        // ── Left: checkbox + large icon preview ──
        JPanel leftPanel = new JPanel(new BorderLayout(0, 2));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(76, 0));

        JCheckBox enabled = new JCheckBox("", isCommandActive(cmd.getId()));
        enabled.setOpaque(false);
        cbEnabled.put(cmd, enabled);

        String currentIcon = getIconFor(cmd.getId(), cmd.getLabel());

        JLabel iconPreview = new JLabel(currentIcon, SwingConstants.CENTER);
        iconPreview.setFont(new Font(Font.DIALOG, Font.PLAIN, 36));
        iconPreview.setPreferredSize(new Dimension(60, 56));
        iconPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xCCCCCC), 1, true),
                new EmptyBorder(2, 2, 2, 2)));
        iconPreview.setOpaque(true);
        iconPreview.setBackground(new Color(0xFAFAFA));
        iconPreviews.put(cmd, iconPreview);

        leftPanel.add(enabled, BorderLayout.NORTH);
        leftPanel.add(iconPreview, BorderLayout.CENTER);

        // ── Center: name, ID, controls ──
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

        // Row 1: command label (bold)
        JLabel nameLabel = new JLabel(cmd.getLabel());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Row 2: command ID (gray, italic)
        JLabel idLabel = new JLabel(cmd.getId());
        idLabel.setFont(idLabel.getFont().deriveFont(Font.ITALIC, 11f));
        idLabel.setForeground(new Color(0x999999));
        idLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Row 3: controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ── Icon text field ──
        controls.add(makeSmallLabel("Icon:"));
        JTextField iconField = new JTextField(currentIcon, 3);
        iconField.setFont(new Font(Font.DIALOG, Font.PLAIN, 22));
        iconField.setHorizontalAlignment(JTextField.CENTER);
        Dimension iconDim = new Dimension(64, 32);
        iconField.setPreferredSize(iconDim);
        iconField.setMinimumSize(iconDim);
        iconField.setMaximumSize(iconDim);
        iconFields.put(cmd, iconField);

        // Wire field → preview
        iconField.getDocument().addDocumentListener(new SimpleDocumentListener(new Runnable() {
            @Override
            public void run() {
                JTextField tf = iconFields.get(cmd);
                JLabel pv = iconPreviews.get(cmd);
                if (tf != null && pv != null) {
                    pv.setText(normalizeIcon(tf.getText().trim()));
                }
            }
        }));
        controls.add(iconField);

        // ── Icon suggestion dropdown ──
        JButton suggestBtn = new JButton("\u25BC");
        suggestBtn.setMargin(new Insets(1, 3, 1, 3));
        suggestBtn.setToolTipText("Icon-Vorschl\u00e4ge");
        suggestBtn.setFocusable(false);
        suggestBtn.addActionListener(e -> showIconSuggestions(suggestBtn, iconField, cmd));
        controls.add(suggestBtn);

        // ── Unicode char picker ──
        JButton charPickerBtn = new JButton("Aa");
        charPickerBtn.setMargin(new Insets(1, 4, 1, 4));
        charPickerBtn.setToolTipText("Unicode Zeichentabelle\u2026");
        charPickerBtn.setFocusable(false);
        charPickerBtn.addActionListener(e -> {
            UnicodeCharPickerDialog picker = new UnicodeCharPickerDialog(
                    SwingUtilities.getWindowAncestor(card));
            String picked = picker.showDialog();
            if (picked != null) {
                iconField.setText(picked);
            }
        });
        controls.add(charPickerBtn);

        // Spacer
        controls.add(Box.createHorizontalStrut(6));

        // ── Color ──
        controls.add(makeSmallLabel("Farbe:"));
        JComboBox<String> colorCombo = new JComboBox<String>(new String[]{
                "", "#FF0000", "#00AA00", "#008000", "#FFA500", "#0000FF", "#FFFF00"
        });
        colorCombo.setEditable(true);
        String preHex = getBackgroundHexFor(cmd.getId());
        colorCombo.setSelectedItem(preHex == null ? "" : preHex);
        colorCombo.setPreferredSize(new Dimension(100, 26));
        colorCombo.setRenderer(new ColorCellRenderer());
        setEditorColorPreview(colorCombo);
        cbColor.put(cmd, colorCombo);
        controls.add(colorCombo);

        JButton pickBtn = makeColorPickerButton(
                () -> Objects.toString(colorCombo.getSelectedItem(), "").trim(),
                hex -> colorCombo.setSelectedItem(hex == null ? "" : hex));
        controls.add(pickBtn);

        // Spacer
        controls.add(Box.createHorizontalStrut(6));

        // ── Position ──
        controls.add(makeSmallLabel("Pos:"));
        int order = getOrderFor(cmd.getId());
        int initial = order > 0 ? order : suggestNextOrder();
        JSpinner sp = new JSpinner(new SpinnerNumberModel(initial, 1, 9999, 1));
        sp.setPreferredSize(new Dimension(60, 26));
        spOrder.put(cmd, sp);
        controls.add(sp);

        // ── Right alignment ──
        boolean preRight = initialConfig.rightSideIds != null
                && initialConfig.rightSideIds.contains(cmd.getId());
        JCheckBox rightChk = new JCheckBox("rechts", preRight);
        rightChk.setOpaque(false);
        rightChk.setFont(rightChk.getFont().deriveFont(11f));
        cbRight.put(cmd, rightChk);
        controls.add(rightChk);

        centerPanel.add(nameLabel);
        centerPanel.add(Box.createVerticalStrut(1));
        centerPanel.add(idLabel);
        centerPanel.add(Box.createVerticalStrut(4));
        centerPanel.add(controls);

        card.add(leftPanel, BorderLayout.WEST);
        card.add(centerPanel, BorderLayout.CENTER);
        return card;
    }

    /** Show a popup grid of icon suggestions (including the label-derived default). */
    private void showIconSuggestions(JButton anchor, JTextField iconField, ToolbarCommand cmd) {
        JPopupMenu popup = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 10, 1, 1));
        grid.setBorder(new EmptyBorder(4, 4, 4, 4));
        grid.setBackground(Color.WHITE);

        // Collect unique suggestions: label icon first, then default, then general
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<String>();
        String labelIcon = ToolbarDefaults.extractIconFromLabel(cmd.getLabel());
        if (labelIcon != null) ordered.add(labelIcon);
        String defaultIcon = ToolbarDefaults.defaultIconFor(cmd.getId());
        ordered.add(defaultIcon);
        for (String s : iconSuggestions) {
            ordered.add(s);
        }

        for (final String icon : ordered) {
            JButton btn = new JButton(icon);
            btn.setFont(new Font(Font.DIALOG, Font.PLAIN, 18));
            Dimension d = new Dimension(34, 34);
            btn.setPreferredSize(d);
            btn.setMinimumSize(d);
            btn.setMaximumSize(d);
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusable(false);
            btn.addActionListener(e -> {
                iconField.setText(icon);
                popup.setVisible(false);
            });
            grid.add(btn);
        }

        popup.add(grid);
        popup.show(anchor, 0, anchor.getHeight());
    }

    private static JLabel makeSmallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(11f));
        return l;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════

    private void onOk() {
        ToolbarConfig cfg = deepCopyOrInit(initialConfig, allCommands);
        cfg.buttons.clear();
        LinkedHashSet<String> newRight = new LinkedHashSet<String>();

        // Group colors
        for (Map.Entry<String, JTextField> e : groupColorFields.entrySet()) {
            String grp = e.getKey();
            String hex = normalizeHex(e.getValue().getText());
            if (hex == null) cfg.groupColors.remove(grp);
            else cfg.groupColors.put(grp, hex);
        }

        // Collect enabled rows
        class Row {
            String id;
            String icon;
            String hex;
            int requestedOrder;
            boolean right;

            Row(String id, String icon, String hex, int ord, boolean r) {
                this.id = id;
                this.icon = icon;
                this.hex = hex;
                this.requestedOrder = ord;
                this.right = r;
            }
        }

        List<Row> rows = new ArrayList<Row>();
        for (ToolbarCommand cmd : cbEnabled.keySet()) {
            if (!cbEnabled.get(cmd).isSelected()) continue;

            String rawIcon = iconFields.get(cmd).getText().trim();
            String icon = normalizeIcon(rawIcon);
            String hex = normalizeHex(Objects.toString(cbColor.get(cmd).getSelectedItem(), "").trim());
            int pos = ((Number) spOrder.get(cmd).getValue()).intValue();
            boolean right = cbRight.get(cmd).isSelected();

            rows.add(new Row(cmd.getId(), icon, hex, pos, right));
        }

        // Sort by requested order (stable)
        rows.sort(new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int c = Integer.compare(normalizeOrder(a.requestedOrder), normalizeOrder(b.requestedOrder));
                if (c != 0) return c;
                return labelOf(a.id).compareToIgnoreCase(labelOf(b.id));
            }
        });

        // Resolve order collisions
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

    // ═══════════════════════════════════════════════════════════════
    //  Visibility dialog
    // ═══════════════════════════════════════════════════════════════

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
        final Map<ToolbarCommand, JCheckBox> checks = new LinkedHashMap<ToolbarCommand, JCheckBox>();

        for (ToolbarCommand cmd : sorted) {
            String id = Objects.toString(cmd.getId(), "");
            boolean visible = !hidden.contains(id);
            JCheckBox cb = new JCheckBox(cmd.getLabel() + "  (" + id + ")", visible);
            checks.put(cmd, cb);
            list.add(cb);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btAllOn = new JButton("Alle anzeigen");
        JButton btAllOff = new JButton("Alle ausblenden");
        topBar.add(btAllOn);
        topBar.add(btAllOff);
        btAllOn.addActionListener(e -> {
            for (JCheckBox cb : checks.values()) cb.setSelected(true);
        });
        btAllOff.addActionListener(e -> {
            for (JCheckBox cb : checks.values()) cb.setSelected(false);
        });

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btOk = new JButton("OK");
        JButton btCancelDlg = new JButton("Abbrechen");
        bottomBar.add(btOk);
        bottomBar.add(btCancelDlg);
        btCancelDlg.addActionListener(e -> dlg.dispose());
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
            rebuildDialog();
        });

        root.add(topBar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottomBar, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(Math.max(560, dlg.getWidth()), Math.max(420, dlg.getHeight())));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Model helpers
    // ═══════════════════════════════════════════════════════════════

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

    /** Resolve the current icon for a command: config → label extraction → static map. */
    private String getIconFor(String id, String label) {
        if (initialConfig.buttons != null) {
            for (ToolbarButtonConfig b : initialConfig.buttons) {
                if (Objects.equals(id, b.id) && b.iconText != null && !b.iconText.trim().isEmpty()) {
                    return b.iconText;
                }
            }
        }
        return ToolbarDefaults.defaultIconFor(id, label);
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
        if (raw == null) return "\u25CF";
        String s = raw.trim();
        if (s.isEmpty()) return "\u25CF";
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

    // ═══════════════════════════════════════════════════════════════
    //  Color UI helpers
    // ═══════════════════════════════════════════════════════════════

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
        JButton btn = new JButton("\u25A0");
        btn.setMargin(new Insets(0, 0, 0, 0));
        Dimension d = new Dimension(24, 24);
        btn.setPreferredSize(d);
        btn.setMinimumSize(d);
        btn.setMaximumSize(d);
        btn.setFocusable(false);
        btn.setToolTipText("Farbe w\u00e4hlen\u2026");
        btn.addActionListener(e -> {
            String hex = normalizeHex(currentHexSupplier.get());
            Color base = toColor(hex);
            Color chosen = JColorChooser.showDialog(this, "Farbe w\u00e4hlen",
                    base == null ? Color.WHITE : base);
            if (chosen != null) {
                hexConsumer.accept(toHex(chosen));
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
        tf.getDocument().addDocumentListener(new SimpleDocumentListener(new Runnable() {
            @Override
            public void run() {
                applyPreviewColor(preview, tf.getText());
            }
        }));
    }

    private void setEditorColorPreview(JComboBox<String> combo) {
        Component editor = combo.getEditor().getEditorComponent();
        if (!(editor instanceof JTextField)) return;
        final JTextField tf = (JTextField) editor;
        tf.getDocument().addDocumentListener(new SimpleDocumentListener(new Runnable() {
            @Override
            public void run() {
                String hex = normalizeHex(tf.getText());
                Color c = toColor(hex);
                if (hex == null || c == null) {
                    tf.setBackground(UIManager.getColor("TextField.background"));
                    tf.setForeground(UIManager.getColor("TextField.foreground"));
                } else {
                    tf.setBackground(c);
                    tf.setForeground(contrastColor(c));
                }
            }
        }));
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

    // ═══════════════════════════════════════════════════════════════
    //  Color utils
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    //  SimpleDocumentListener
    // ═══════════════════════════════════════════════════════════════

    private static class SimpleDocumentListener implements DocumentListener {
        private final Runnable action;

        SimpleDocumentListener(Runnable action) { this.action = action; }

        @Override
        public void insertUpdate(DocumentEvent e) { action.run(); }

        @Override
        public void removeUpdate(DocumentEvent e) { action.run(); }

        @Override
        public void changedUpdate(DocumentEvent e) { action.run(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  "Alle laden" helper
    // ═══════════════════════════════════════════════════════════════

    private ToolbarConfig createAllConfig() {
        ToolbarConfig cfg = new ToolbarConfig();
        cfg.buttonSizePx = (initialConfig != null && initialConfig.buttonSizePx > 0)
                ? initialConfig.buttonSizePx : 48;
        cfg.fontSizeRatio = (initialConfig != null && initialConfig.fontSizeRatio > 0f)
                ? initialConfig.fontSizeRatio : 0.75f;

        cfg.groupColors = new LinkedHashMap<String, String>(
                initialConfig != null && initialConfig.groupColors != null
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
            String label = mc.getLabel();
            ToolbarButtonConfig b = new ToolbarButtonConfig(id, ToolbarDefaults.defaultIconFor(id, label));
            b.order = Integer.valueOf(pos++);
            b.backgroundHex = ToolbarDefaults.defaultBackgroundHexFor(id);
            cfg.buttons.add(b);
        }
        cfg.hiddenCommandIds = new LinkedHashSet<String>();
        return cfg;
    }
}
