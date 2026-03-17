package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.security.SecurityFilterService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Settings panel "Sicherheit" — manages per-connection-type whitelist/blacklist
 * for caching and indexing control.
 *
 * <p>Layout:
 * <ul>
 *   <li>Left tabs (one per connection group: LOCAL, FTP, NDV, WIKI, …)</li>
 *   <li>Each tab has: "Blacklist All" checkbox + Whitelist table + Blacklist table</li>
 * </ul>
 */
public class SecuritySettingsPanel extends AbstractSettingsPanel {

    private final SecurityFilterService filterService = SecurityFilterService.getInstance();

    /** Transient state per group, committed on apply(). */
    private final Map<String, GroupPanel> groupPanels = new LinkedHashMap<String, GroupPanel>();

    public SecuritySettingsPanel() {
        super("security", "Sicherheit");
        initPanel();
    }

    private void initPanel() {
        removeAll();
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
        tabs.setFont(tabs.getFont().deriveFont(Font.PLAIN, 12f));

        for (String group : SecurityFilterService.ALL_GROUPS) {
            String label = SecurityFilterService.GROUP_LABELS.get(group);
            if (label == null) label = group;
            GroupPanel gp = new GroupPanel(group);
            groupPanels.put(group, gp);
            tabs.addTab(label, gp);
        }

        add(tabs, BorderLayout.CENTER);

        // Info at bottom
        JLabel info = new JLabel("<html><small>"
                + "<b>Blacklist gewinnt immer:</b> Wenn ein Ordner auf der Whitelist steht, "
                + "aber eine Datei darin auf der Blacklist, wird die Datei <b>nicht</b> gecacht/indexiert.<br>"
                + "<b>\u201EAlles sperren\u201C:</b> Wenn aktiviert, werden nur Pfade auf der Whitelist gecacht/indexiert. "
                + "Standard: aktiviert f\u00FCr Lokal und FTP."
                + "</small></html>");
        info.setForeground(Color.GRAY);
        info.setBorder(new EmptyBorder(8, 12, 8, 12));
        add(info, BorderLayout.SOUTH);
    }

    @Override
    protected void applyToSettings(Settings s) {
        // SecurityFilterService persists to its own file, not to Settings
        for (Map.Entry<String, GroupPanel> entry : groupPanels.entrySet()) {
            GroupPanel gp = entry.getValue();
            filterService.setGroupRules(
                    entry.getKey(),
                    gp.blacklistAllCheck.isSelected(),
                    gp.whitelistModel.getEntries(),
                    gp.blacklistModel.getEntries()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Per-Group Panel
    // ═══════════════════════════════════════════════════════════════

    private class GroupPanel extends JPanel {
        final JCheckBox blacklistAllCheck;
        final PathListModel whitelistModel;
        final PathListModel blacklistModel;

        GroupPanel(String group) {
            setLayout(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(10, 10, 10, 10));

            String label = SecurityFilterService.GROUP_LABELS.get(group);
            if (label == null) label = group;

            // ── Top: Blacklist-All checkbox ──
            blacklistAllCheck = new JCheckBox("Alles sperren (nur Whitelist erlauben)");
            blacklistAllCheck.setSelected(filterService.isBlacklistAll(group));
            blacklistAllCheck.setToolTipText(
                    "Wenn aktiviert, werden nur Pfade gecacht/indexiert, die explizit auf der Whitelist stehen.");
            blacklistAllCheck.setFont(blacklistAllCheck.getFont().deriveFont(Font.BOLD));
            add(blacklistAllCheck, BorderLayout.NORTH);

            // ── Center: split between whitelist and blacklist ──
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setResizeWeight(0.5);
            splitPane.setContinuousLayout(true);

            // Whitelist
            whitelistModel = new PathListModel(filterService.getWhitelist(group));
            JPanel whitelistPanel = createListPanel("\u2705 Whitelist (erlaubt)", whitelistModel, group);
            splitPane.setTopComponent(whitelistPanel);

            // Blacklist
            blacklistModel = new PathListModel(filterService.getBlacklist(group));
            JPanel blacklistPanel = createListPanel("\u26D4 Blacklist (gesperrt)", blacklistModel, group);
            splitPane.setBottomComponent(blacklistPanel);

            add(splitPane, BorderLayout.CENTER);
        }

        private JPanel createListPanel(String title, PathListModel model, String group) {
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createEtchedBorder(), title,
                    TitledBorder.LEFT, TitledBorder.TOP));

            JTable table = new JTable(model);
            table.setFillsViewportHeight(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.getColumnModel().getColumn(0).setHeaderValue("Pfad");

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(0, 120));
            panel.add(scroll, BorderLayout.CENTER);

            // Buttons
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JButton addBtn = new JButton("+ Hinzuf\u00FCgen");
            addBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String path = JOptionPane.showInputDialog(
                            SecuritySettingsPanel.this,
                            "Pfad eingeben (z.B. ftp://server/dir/ oder ndv://LIB):",
                            "Pfad hinzuf\u00FCgen",
                            JOptionPane.PLAIN_MESSAGE);
                    if (path != null && !path.trim().isEmpty()) {
                        model.addEntry(path.trim());
                    }
                }
            });
            JButton removeBtn = new JButton("- Entfernen");
            removeBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] rows = table.getSelectedRows();
                    if (rows.length > 0) {
                        // Remove in reverse order
                        Arrays.sort(rows);
                        for (int i = rows.length - 1; i >= 0; i--) {
                            model.removeEntry(rows[i]);
                        }
                    }
                }
            });
            buttons.add(addBtn);
            buttons.add(removeBtn);
            panel.add(buttons, BorderLayout.SOUTH);

            return panel;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Table Model for path lists
    // ═══════════════════════════════════════════════════════════════

    private static class PathListModel extends AbstractTableModel {
        private final List<String> entries;

        PathListModel(List<String> initial) {
            this.entries = new ArrayList<String>(initial);
        }

        List<String> getEntries() {
            return new ArrayList<String>(entries);
        }

        void addEntry(String path) {
            if (!entries.contains(path)) {
                entries.add(path);
                fireTableRowsInserted(entries.size() - 1, entries.size() - 1);
            }
        }

        void removeEntry(int index) {
            if (index >= 0 && index < entries.size()) {
                entries.remove(index);
                fireTableRowsDeleted(index, index);
            }
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getColumnName(int column) {
            return "Pfad";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return entries.get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String val = String.valueOf(aValue).trim();
            if (!val.isEmpty()) {
                entries.set(rowIndex, val);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}

