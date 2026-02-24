package com.example.toolbarkit.toolbar;

import com.example.toolbarkit.command.ToolbarCommand;
import com.example.toolbarkit.command.ToolbarCommandRegistry;
import com.example.toolbarkit.config.ToolbarConfigRepository;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Render a configurable toolbar with:
 * - left/right alignment
 * - per-button and per-group colors
 * - drag and drop reordering
 * - configuration dialog
 */
public class ConfigurableCommandToolbar extends JToolBar {

    private static final String PROP_COMMAND_ID = "commandId";
    private static final String PROP_SUPPRESS_CLICK = "suppressNextClick";
    private static final String GEAR_BUTTON_NAME = "toolbarConfigButton";

    private final ToolbarCommandRegistry registry;
    private final ToolbarConfigRepository repository;

    private final String[] iconSuggestions;

    private ToolbarConfig config;

    public ConfigurableCommandToolbar(ToolbarCommandRegistry registry,
                                      ToolbarConfigRepository repository,
                                      String[] iconSuggestions) {
        this.registry = requireNonNull(registry, "registry");
        this.repository = requireNonNull(repository, "repository");
        this.iconSuggestions = (iconSuggestions != null) ? iconSuggestions : IconSuggestions.defaults();

        setFloatable(false);

        loadConfig();
        rebuildButtons();
    }

    public ConfigurableCommandToolbar(ToolbarCommandRegistry registry,
                                      ToolbarConfigRepository repository) {
        this(registry, repository, IconSuggestions.defaults());
    }

    public void reload() {
        loadConfig();
        rebuildButtons();
    }

    // ---------------- UI build ----------------

    private void rebuildButtons() {
        removeAll();

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));

        if (config != null && config.buttons != null) {
            List<ToolbarButtonConfig> left = new ArrayList<ToolbarButtonConfig>();
            List<ToolbarButtonConfig> right = new ArrayList<ToolbarButtonConfig>();

            for (ToolbarButtonConfig b : config.buttons) {
                if (isRightAligned(b.id)) {
                    right.add(b);
                } else {
                    left.add(b);
                }
            }

            Collections.sort(left, buttonOrderComparator());
            Collections.sort(right, buttonOrderComparator());

            createButtonsIntoPanel(leftPanel, left);
            createButtonsIntoPanel(rightPanel, right);
        }

        JButton configButton = createConfigButton();
        rightPanel.add(configButton);

        leftPanel.setTransferHandler(new ToolbarPanelDropHandler(false));
        rightPanel.setTransferHandler(new ToolbarPanelDropHandler(true));

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        revalidate();
        repaint();
    }

    private JButton createConfigButton() {
        JButton btn = new JButton(cp(0x1F6E0)); // 🛠
        btn.setName(GEAR_BUTTON_NAME);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setFocusable(false);
        btn.setToolTipText("Toolbar anpassen");
        btn.setPreferredSize(new Dimension(config.buttonSizePx, config.buttonSizePx));
        btn.setFont(btn.getFont().deriveFont((float) buttonFontSizePx()));
        btn.addActionListener(e -> openConfigDialog());
        return btn;
    }

    private void createButtonsIntoPanel(JPanel panel, List<ToolbarButtonConfig> list) {
        for (final ToolbarButtonConfig btnCfg : list) {
            Optional<ToolbarCommand> cmdOpt = registry.findById(btnCfg.id);
            if (!cmdOpt.isPresent()) {
                continue;
            }

            final ToolbarCommand cmd = cmdOpt.get();

            JButton btn = new JButton(normalizeIcon(btnCfg.iconText, cmd.getId()));
            btn.putClientProperty(PROP_COMMAND_ID, btnCfg.id);
            btn.putClientProperty(PROP_SUPPRESS_CLICK, Boolean.FALSE);

            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusable(false);
            btn.setPreferredSize(new Dimension(config.buttonSizePx, config.buttonSizePx));
            btn.setFont(btn.getFont().deriveFont((float) buttonFontSizePx()));
            btn.setFocusPainted(false);

            String tooltip = buildTooltip(cmd);
            btn.setToolTipText(tooltip);

            applyButtonBackground(btn, btnCfg);

            btn.addActionListener(e -> {
                JComponent src = (JComponent) e.getSource();
                boolean suppress = Boolean.TRUE.equals(src.getClientProperty(PROP_SUPPRESS_CLICK));
                if (suppress) {
                    src.putClientProperty(PROP_SUPPRESS_CLICK, Boolean.FALSE);
                    return;
                }
                cmd.perform();
            });

            btn.setTransferHandler(new DragButtonTransferHandler());
            DragInitiatorMouseAdapter dima = new DragInitiatorMouseAdapter();
            btn.addMouseListener(dima);
            btn.addMouseMotionListener(dima);

            panel.add(btn);
        }
    }

    private String buildTooltip(ToolbarCommand cmd) {
        String label = (cmd.getLabel() != null) ? cmd.getLabel() : cmd.getId();
        if (cmd.getShortcuts() == null || cmd.getShortcuts().isEmpty()) {
            return label;
        }
        String shortcut = cmd.getShortcuts().get(0);
        return label + " (" + shortcut + ")";
    }

    private void applyButtonBackground(JButton btn, ToolbarButtonConfig btnCfg) {
        boolean applied = false;

        if (btnCfg.backgroundHex != null && btnCfg.backgroundHex.trim().length() > 0) {
            Color c = parseHexColor(btnCfg.backgroundHex);
            if (c != null) {
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBackground(c);
                applied = true;
            }
        }

        if (!applied) {
            String groupHex = groupColorFor(btnCfg.id);
            Color c = parseHexColor(groupHex);
            if (c != null) {
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBackground(c);
            }
        }
    }

    private String groupColorFor(String commandId) {
        if (commandId == null || config == null || config.groupColors == null) {
            return null;
        }
        int dot = commandId.indexOf('.');
        String group = (dot > 0) ? commandId.substring(0, dot) : "(none)";
        return config.groupColors.get(group);
    }

    // ---------------- DnD ----------------

    private static final class DragInitiatorMouseAdapter extends java.awt.event.MouseAdapter {
        private static final int DRAG_THRESHOLD_PX = 5;

        private Point pressPoint;
        private boolean dragStarted;

        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            pressPoint = e.getPoint();
            dragStarted = false;
        }

        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
            if (dragStarted || pressPoint == null) return;

            int dx = Math.abs(e.getX() - pressPoint.x);
            int dy = Math.abs(e.getY() - pressPoint.y);

            if (dx < DRAG_THRESHOLD_PX && dy < DRAG_THRESHOLD_PX) {
                return;
            }

            JComponent c = (JComponent) e.getSource();
            c.putClientProperty(PROP_SUPPRESS_CLICK, Boolean.TRUE);

            if (c instanceof AbstractButton) {
                ButtonModel m = ((AbstractButton) c).getModel();
                m.setArmed(false);
                m.setPressed(false);
            }

            TransferHandler th = c.getTransferHandler();
            if (th != null) {
                th.exportAsDrag(c, e, TransferHandler.MOVE);
                dragStarted = true;
            }
        }

        @Override
        public void mouseReleased(java.awt.event.MouseEvent e) {
            pressPoint = null;
            dragStarted = false;
        }
    }

    private final class ToolbarPanelDropHandler extends TransferHandler {

        private final boolean toRightSide;

        private ToolbarPanelDropHandler(boolean toRightSide) {
            this.toRightSide = toRightSide;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            support.setDropAction(TransferHandler.MOVE);
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;

            try {
                String movedId = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                if (movedId == null || movedId.trim().isEmpty()) return false;

                JComponent dropTarget = (JComponent) support.getComponent();
                Point p = support.getDropLocation().getDropPoint();

                int insertIndex = computeInsertIndex((JPanel) dropTarget, p);
                updateModelAfterDrop(movedId, toRightSide, insertIndex, (JPanel) dropTarget);

                rebuildButtons();
                return true;
            } catch (Exception ex) {
                System.err.println("DnD import failed: " + ex.getMessage());
                return false;
            }
        }
    }

    private static final class DragButtonTransferHandler extends TransferHandler {

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            Object id = (c instanceof JButton) ? ((JButton) c).getClientProperty(PROP_COMMAND_ID) : null;
            return new StringSelection(id == null ? "" : String.valueOf(id));
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }
    }

    private int computeInsertIndex(JPanel panel, Point dropPoint) {
        Component[] comps = panel.getComponents();
        int effectiveCount = 0;

        for (int i = 0; i < comps.length; i++) {
            if (isConfigButton(comps[i])) continue;
            Rectangle r = comps[i].getBounds();

            int midX = r.x + (r.width / 2);
            int midY = r.y + (r.height / 2);

            // Prefer the closest row when FlowLayout wraps.
            if (dropPoint.y > r.y + r.height) {
                effectiveCount++;
                continue;
            }

            if (dropPoint.y < r.y) {
                return effectiveCount;
            }

            if (dropPoint.x <= midX) {
                return effectiveCount;
            }

            effectiveCount++;
        }

        return effectiveCount;
    }

    private void updateModelAfterDrop(String movedId,
                                      boolean targetRightSide,
                                      int insertIndex,
                                      JPanel targetPanel) {
        ensureConfig();

        if (config.rightSideIds == null) {
            config.rightSideIds = new LinkedHashSet<String>();
        }

        boolean wasRight = isRightAligned(movedId);

        if (targetRightSide && !wasRight) {
            config.rightSideIds.add(movedId);
        } else if (!targetRightSide && wasRight) {
            config.rightSideIds.remove(movedId);
        }

        List<String> targetIds = extractIdsFromPanel(targetPanel);
        targetIds.remove(movedId);

        insertIndex = clamp(insertIndex, 0, targetIds.size());
        targetIds.add(insertIndex, movedId);

        applySequentialOrder(targetIds);

        if (wasRight != targetRightSide) {
            JPanel other = findSiblingPanel(targetPanel);
            if (other != null) {
                List<String> sourceIds = extractIdsFromPanel(other);
                sourceIds.remove(movedId);
                applySequentialOrder(sourceIds);
            }
        }

        repository.save(config);
    }

    private List<String> extractIdsFromPanel(JPanel panel) {
        List<String> ids = new ArrayList<String>();
        Component[] comps = panel.getComponents();

        for (int i = 0; i < comps.length; i++) {
            if (!(comps[i] instanceof JButton)) continue;
            if (isConfigButton(comps[i])) continue;

            Object id = ((JButton) comps[i]).getClientProperty(PROP_COMMAND_ID);
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }

        return ids;
    }

    private JPanel findSiblingPanel(JPanel targetPanel) {
        for (Component c : getComponents()) {
            if (c instanceof JPanel && c != targetPanel) {
                return (JPanel) c;
            }
        }
        return null;
    }

    private void applySequentialOrder(List<String> orderedIds) {
        int pos = 1;
        for (String id : orderedIds) {
            ToolbarButtonConfig cfg = findButtonCfg(id);
            if (cfg != null) {
                cfg.order = Integer.valueOf(pos++);
            }
        }
    }

    private ToolbarButtonConfig findButtonCfg(String id) {
        if (id == null || config == null || config.buttons == null) return null;
        for (ToolbarButtonConfig b : config.buttons) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    private boolean isConfigButton(Component c) {
        if (!(c instanceof JButton)) return false;
        JButton b = (JButton) c;
        return GEAR_BUTTON_NAME.equals(b.getName());
    }

    // ---------------- Config dialog ----------------

    private void openConfigDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        List<ToolbarCommand> all = new ArrayList<ToolbarCommand>(registry.getAll());

        ToolbarConfigDialog dlg = new ToolbarConfigDialog(owner, config, all, iconSuggestions);
        ToolbarConfig updated = dlg.showDialog();
        if (updated != null) {
            config = updated;
            repository.save(config);
            rebuildButtons();
        }
    }

    // ---------------- Config load/ensure ----------------

    private void loadConfig() {
        config = repository.loadOrCreate(new ToolbarConfigRepository.ConfigFactory() {
            @Override
            public ToolbarConfig createDefault() {
                List<ToolbarCommand> all = new ArrayList<ToolbarCommand>(registry.getAll());
                return ToolbarDefaults.createInitialConfig(all);
            }
        });
        ensureConfig();
    }

    private void ensureConfig() {
        if (config == null) {
            List<ToolbarCommand> all = new ArrayList<ToolbarCommand>(registry.getAll());
            config = ToolbarDefaults.createInitialConfig(all);
        }
        if (config.buttonSizePx <= 0) config.buttonSizePx = 48;
        if (config.fontSizeRatio <= 0f) config.fontSizeRatio = 0.75f;
        if (config.buttons == null) config.buttons = new ArrayList<ToolbarButtonConfig>();
        if (config.rightSideIds == null) config.rightSideIds = new LinkedHashSet<String>();
        if (config.groupColors == null) config.groupColors = new java.util.LinkedHashMap<String, String>();
        if (config.hiddenCommandIds == null) config.hiddenCommandIds = new LinkedHashSet<String>();
    }

    private int buttonFontSizePx() {
        return (int) (config.buttonSizePx * config.fontSizeRatio);
    }

    private boolean isRightAligned(String id) {
        return config != null && config.rightSideIds != null && id != null && config.rightSideIds.contains(id);
    }

    // ---------------- sorting ----------------

    private Comparator<ToolbarButtonConfig> buttonOrderComparator() {
        return new Comparator<ToolbarButtonConfig>() {
            @Override
            public int compare(ToolbarButtonConfig a, ToolbarButtonConfig b) {
                int oa = normalizeOrder(a.order);
                int ob = normalizeOrder(b.order);
                if (oa != ob) return (oa < ob) ? -1 : 1;

                String la = labelOf(a.id);
                String lb = labelOf(b.id);
                return la.compareToIgnoreCase(lb);
            }
        };
    }

    private int normalizeOrder(Integer order) {
        if (order == null) return Integer.MAX_VALUE;
        int v = order.intValue();
        return (v <= 0) ? Integer.MAX_VALUE : v;
    }

    private String labelOf(String id) {
        if (id == null) return "";
        Optional<ToolbarCommand> cmd = registry.findById(id);
        return cmd.isPresent() ? cmd.get().getLabel() : id;
    }

    // ---------------- misc helpers ----------------

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static Color parseHexColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;

        if (!s.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) {
            return null;
        }

        try {
            String h = s.substring(1);
            if (h.length() == 3) {
                int r = Integer.parseInt(h.substring(0, 1) + h.substring(0, 1), 16);
                int g = Integer.parseInt(h.substring(1, 2) + h.substring(1, 2), 16);
                int b = Integer.parseInt(h.substring(2, 3) + h.substring(2, 3), 16);
                return new Color(r, g, b);
            }
            if (h.length() == 6) {
                int rgb = Integer.parseInt(h, 16);
                return new Color(rgb);
            }
            long v = Long.parseLong(h, 16);
            int a = (int) ((v >> 24) & 0xFF);
            int r = (int) ((v >> 16) & 0xFF);
            int g = (int) ((v >> 8) & 0xFF);
            int b = (int) (v & 0xFF);
            return new Color(r, g, b, a);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String normalizeIcon(String iconText, String fallbackId) {
        String raw = (iconText == null) ? "" : iconText.trim();
        if (raw.isEmpty()) {
            return ToolbarDefaults.defaultIconFor(fallbackId);
        }
        String up = raw.toUpperCase(Locale.ROOT);
        if (up.matches("^([U]\\+|0X)?[0-9A-F]{4,6}$")) {
            String hex = up.replace("U+", "").replace("0X", "");
            try {
                int cp = Integer.parseInt(hex, 16);
                return new String(Character.toChars(cp));
            } catch (Exception ignore) {
                return raw;
            }
        }
        return raw;
    }

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static <T> T requireNonNull(T v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return v;
    }
}
