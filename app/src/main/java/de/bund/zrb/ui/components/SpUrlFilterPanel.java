package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dynamic URL filter panel with editable dropdowns for each URL path segment.
 * <p>
 * Layout: {@code https:// [Domain ▾] / [seg1 ▾] / [seg2 ▾] / …}
 * <p>
 * Features:
 * <ul>
 *   <li>Each segment is an editable {@link JComboBox} pre-populated with
 *       suggestions extracted from the discovered URLs plus persisted history.</li>
 *   <li>When the user types/selects in the <b>last</b> dropdown a new empty
 *       one appears automatically; the dialog is re-packed so width grows.</li>
 *   <li>A dedicated {@code "✕ Entfernen"} item at the end of every dropdown
 *       removes that segment. Alternatively {@code Shift+Delete} on a selected
 *       dropdown item removes that item from history.</li>
 *   <li>Clicking on a {@code "/"} separator inserts a new empty segment
 *       between the two neighbours.</li>
 *   <li>Segment values without regex metacharacters are auto-quoted;
 *       values containing {@code [ ] ( ) * + ? { } | \ ^ $} are used as-is.</li>
 *   <li>Segments whose individual sub-pattern has <b>zero matches</b> in the
 *       URL set are highlighted <b style="color:red">red</b>.</li>
 *   <li>The last used segment list is persisted in
 *       {@code Settings.applicationState} under key {@code sp.filter.segments}
 *       (pipe-separated).</li>
 * </ul>
 * Default: {@code <DOMAIN> / sites / [^/]+} (3 filled + 1 empty).
 */
public class SpUrlFilterPanel extends JPanel {

    /** Sentinel item shown at the end of every dropdown to remove the segment. */
    private static final String REMOVE_ITEM = "\u2715 Entfernen";

    /** applicationState key used for persistence. */
    private static final String STATE_KEY = "sp.filter.segments";

    /** Max history entries stored per segment position. */
    private static final int MAX_HISTORY = 10;

    // ── UI components ──────────────────────────────────────────────
    private final JPanel segmentPanel;
    private final JLabel regexPreview;
    private final JLabel matchCountLabel;

    // ── Model ──────────────────────────────────────────────────────
    /** Combo boxes in display order (index 0 = domain). */
    private final List<JComboBox<String>> segmentBoxes = new ArrayList<JComboBox<String>>();
    /** Slash labels between boxes: separatorLabels.get(i) sits between box i and box i+1. */
    private final List<JLabel> separatorLabels = new ArrayList<JLabel>();

    private final List<Runnable> changeListeners = new ArrayList<Runnable>();
    private final Map<Integer, List<String>> segmentSuggestions;
    private final List<String> allUrls;
    private boolean updating = false;

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    /**
     * @param allUrls    all discovered URLs (for suggestions + per-segment highlighting)
     * @param scannedUrl the page URL that was scanned (default domain)
     */
    public SpUrlFilterPanel(List<String> allUrls, String scannedUrl) {
        this.allUrls = allUrls;
        setLayout(new BorderLayout(4, 2));
        setBorder(BorderFactory.createTitledBorder("URL-Filter (Regex pro Pfadsegment)"));

        segmentSuggestions = extractSuggestions(allUrls);

        segmentPanel = new JPanel();
        segmentPanel.setLayout(new BoxLayout(segmentPanel, BoxLayout.X_AXIS));

        // Protocol prefix label
        JLabel proto = new JLabel(" https://");
        proto.setFont(proto.getFont().deriveFont(Font.BOLD));
        segmentPanel.add(proto);

        // ── Determine initial segments (persisted or defaults) ──
        List<String> initial = loadPersistedSegments();
        if (initial == null) {
            String defaultDomain = extractDomain(scannedUrl);
            initial = new ArrayList<String>();
            initial.add(defaultDomain);
            initial.add("sites");
            initial.add("[^/]+");
        }
        for (int i = 0; i < initial.size(); i++) {
            addSegmentBox(initial.get(i), i == 0);
        }
        // Always end with one empty box
        addSegmentBox("", false);

        JScrollPane scroll = new JScrollPane(segmentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(700, 38));
        add(scroll, BorderLayout.CENTER);

        // Bottom row: regex preview + match count
        JPanel bottomRow = new JPanel(new BorderLayout(8, 0));
        regexPreview = new JLabel();
        regexPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        bottomRow.add(regexPreview, BorderLayout.CENTER);

        matchCountLabel = new JLabel();
        matchCountLabel.setFont(matchCountLabel.getFont().deriveFont(Font.BOLD));
        bottomRow.add(matchCountLabel, BorderLayout.EAST);
        add(bottomRow, BorderLayout.SOUTH);

        updatePreview();
    }

    // ════════════════════════════════════════════════════════════════
    //  Segment box management
    // ════════════════════════════════════════════════════════════════

    /**
     * Append a new segment dropdown at the <b>end</b> of the list.
     */
    private void addSegmentBox(String value, boolean isFirst) {
        insertSegmentBoxAt(segmentBoxes.size(), value, isFirst);
    }

    /**
     * Insert a segment dropdown at an arbitrary position.
     */
    private void insertSegmentBoxAt(int pos, String value, boolean skipSeparator) {
        // ── Separator "/" ──
        if (!skipSeparator) {
            JLabel sep = createSeparatorLabel();
            separatorLabels.add(pos > 0 ? pos - 1 : 0, sep);
        }

        // ── Editable combo box ──
        final JComboBox<String> box = new JComboBox<String>();
        box.setEditable(true);
        box.setPreferredSize(new Dimension(140, 26));
        box.setMaximumSize(new Dimension(200, 26));

        // Populate: URL-based suggestions
        List<String> suggestions = segmentSuggestions.get(pos);
        if (suggestions != null) {
            Set<String> added = new LinkedHashSet<String>();
            for (String s : suggestions) {
                if (added.add(s)) box.addItem(s);
            }
        }
        // Common regex helpers (not for domain)
        if (pos > 0) {
            addIfAbsent(box, "[^/]+");
            addIfAbsent(box, ".*");
        }
        // Sentinel remove item (always last)
        box.addItem(REMOVE_ITEM);

        box.getEditor().setItem(value != null ? value : "");

        // ── Listeners ──
        box.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updating) return;
                int idx = segmentBoxes.indexOf(box);
                if (idx < 0) return;
                Object selected = box.getSelectedItem();
                if (REMOVE_ITEM.equals(selected)) {
                    box.getEditor().setItem("");
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            removeSegmentAt(segmentBoxes.indexOf(box));
                        }
                    });
                    return;
                }
                onSegmentChanged(idx);
            }
        });

        Component editorComp = box.getEditor().getEditorComponent();
        if (editorComp instanceof JTextField) {
            final JTextField tf = (JTextField) editorComp;
            tf.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { scheduleUpdate(box); }
                @Override public void removeUpdate(DocumentEvent e) { scheduleUpdate(box); }
                @Override public void changedUpdate(DocumentEvent e) { scheduleUpdate(box); }
            });
            // Shift+Delete removes the highlighted dropdown item from history
            tf.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE && e.isShiftDown()) {
                        int selIdx = box.getSelectedIndex();
                        if (selIdx >= 0) {
                            String item = box.getItemAt(selIdx);
                            if (!REMOVE_ITEM.equals(item)) {
                                box.removeItemAt(selIdx);
                                e.consume();
                            }
                        }
                    }
                }
            });
        }

        // ── Insert into model ──
        segmentBoxes.add(pos, box);

        // Rebuild the segment panel to keep order correct
        rebuildSegmentPanel();
    }

    /**
     * Remove the segment at the given index.
     * Never removes the very last remaining segment (domain).
     */
    private void removeSegmentAt(int index) {
        if (index < 0 || index >= segmentBoxes.size()) return;
        // Don't remove if it's the only non-empty segment left
        int nonEmptyCount = 0;
        for (JComboBox<String> b : segmentBoxes) {
            Object item = b.getEditor().getItem();
            if (item != null && !item.toString().trim().isEmpty()) nonEmptyCount++;
        }
        if (nonEmptyCount <= 1 && !getSegmentValue(index).isEmpty()) return;

        updating = true;
        try {
            segmentBoxes.remove(index);
            // Remove corresponding separator
            if (!separatorLabels.isEmpty()) {
                int sepIdx = Math.max(0, index - 1);
                if (sepIdx < separatorLabels.size()) {
                    separatorLabels.remove(sepIdx);
                }
            }
            rebuildSegmentPanel();
            ensureTrailingEmpty();
            updatePreview();
            fireChangeEvent();
        } finally {
            updating = false;
        }
    }

    /**
     * Create a clickable "/" separator label.
     * Clicking inserts a new empty segment between the two neighbours.
     */
    private JLabel createSeparatorLabel() {
        final JLabel sep = new JLabel(" / ");
        sep.setFont(sep.getFont().deriveFont(Font.BOLD));
        sep.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sep.setToolTipText("Klick: Segment einf\u00fcgen");
        sep.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int sepIndex = separatorLabels.indexOf(sep);
                if (sepIndex < 0) return;
                int insertPos = sepIndex + 1;
                updating = true;
                try {
                    insertSegmentBoxAt(insertPos, "", false);
                    ensureTrailingEmpty();
                    updatePreview();
                    fireChangeEvent();
                    requestPackDialog();
                } finally {
                    updating = false;
                }
            }
        });
        return sep;
    }

    /**
     * Rebuild the visual contents of segmentPanel from the model lists.
     */
    private void rebuildSegmentPanel() {
        Component proto = segmentPanel.getComponent(0); // "https://" label
        segmentPanel.removeAll();
        segmentPanel.add(proto);

        for (int i = 0; i < segmentBoxes.size(); i++) {
            if (i > 0 && i - 1 < separatorLabels.size()) {
                segmentPanel.add(separatorLabels.get(i - 1));
            }
            segmentPanel.add(segmentBoxes.get(i));
        }
        segmentPanel.revalidate();
        segmentPanel.repaint();
    }

    // ════════════════════════════════════════════════════════════════
    //  Change handling
    // ════════════════════════════════════════════════════════════════

    private void scheduleUpdate(final JComboBox<String> box) {
        if (updating) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                int idx = segmentBoxes.indexOf(box);
                if (idx >= 0) onSegmentChanged(idx);
            }
        });
    }

    private void onSegmentChanged(int index) {
        if (updating) return;
        updating = true;
        try {
            String value = getSegmentValue(index);

            // If the last segment just got a value → append new empty dropdown
            if (index == segmentBoxes.size() - 1 && !value.isEmpty()) {
                addSegmentBox("", false);
                requestPackDialog();
            }

            trimTrailingEmpty();
            updatePreview();
            fireChangeEvent();
        } finally {
            updating = false;
        }
    }

    /**
     * Keep exactly ONE empty dropdown after the last non-empty one.
     */
    private void trimTrailingEmpty() {
        while (segmentBoxes.size() > 1) {
            int last = segmentBoxes.size() - 1;
            int secondLast = last - 1;
            if (getSegmentValue(last).isEmpty() && getSegmentValue(secondLast).isEmpty()) {
                segmentBoxes.remove(last);
                if (!separatorLabels.isEmpty()) {
                    separatorLabels.remove(separatorLabels.size() - 1);
                }
            } else {
                break;
            }
        }
        rebuildSegmentPanel();
    }

    /**
     * Ensure the box list ends with one empty trailing placeholder.
     */
    private void ensureTrailingEmpty() {
        if (segmentBoxes.isEmpty() || !getSegmentValue(segmentBoxes.size() - 1).isEmpty()) {
            addSegmentBox("", false);
        }
    }

    /**
     * Ask the containing dialog to re-pack so new segments are visible.
     * Grows up to the available screen area but never shrinks.
     */
    private void requestPackDialog() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w == null) return;

        // Remember current size so we never shrink
        int oldW = w.getWidth();
        int oldH = w.getHeight();

        // Let the layout manager calculate the preferred size
        w.pack();

        // Screen limits
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets si = Toolkit.getDefaultToolkit().getScreenInsets(w.getGraphicsConfiguration());
        int maxW = screen.width - si.left - si.right;
        int maxH = screen.height - si.top - si.bottom;

        // Take the larger of old vs. packed, capped at screen
        int newW = Math.min(maxW, Math.max(oldW, w.getWidth()));
        int newH = Math.min(maxH, Math.max(oldH, w.getHeight()));
        w.setSize(newW, newH);

        // Re-centre if it moved off screen
        if (w.getX() + newW > screen.width - si.right
                || w.getY() + newH > screen.height - si.bottom) {
            w.setLocationRelativeTo(w.getOwner());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Regex building
    // ════════════════════════════════════════════════════════════════

    private String getSegmentValue(int index) {
        if (index < 0 || index >= segmentBoxes.size()) return "";
        Object item = segmentBoxes.get(index).getEditor().getItem();
        String val = item != null ? item.toString().trim() : "";
        if (REMOVE_ITEM.equals(val)) return "";
        return val;
    }

    /**
     * Build a regex pattern string from the current segment values.
     * Result: {@code https?://domain/seg1/seg2$}
     */
    public String buildRegex() {
        StringBuilder sb = new StringBuilder("https?://");
        boolean first = true;
        for (int i = 0; i < segmentBoxes.size(); i++) {
            String val = getSegmentValue(i);
            if (val.isEmpty()) break;
            if (!first) sb.append("/");
            sb.append(isRegexPattern(val) ? val : Pattern.quote(val));
            first = false;
        }
        sb.append("$");
        return sb.toString();
    }

    /**
     * Compile the current filter into a {@link Pattern}.
     *
     * @return compiled pattern, or {@code null} if the regex is invalid
     */
    public Pattern compileFilter() {
        try {
            return Pattern.compile(buildRegex(), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static boolean isRegexPattern(String val) {
        for (int i = 0; i < val.length(); i++) {
            if ("[]()*+?{}|\\^$".indexOf(val.charAt(i)) >= 0) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  Preview + per-segment highlighting
    // ════════════════════════════════════════════════════════════════

    private void updatePreview() {
        String regex = buildRegex();
        boolean valid;
        try {
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            valid = true;
        } catch (PatternSyntaxException e) {
            valid = false;
        }

        if (valid) {
            regexPreview.setText("Regex: " + regex);
            regexPreview.setForeground(new Color(0, 100, 0));
        } else {
            regexPreview.setText("Ung\u00fcltiger Regex: " + regex);
            regexPreview.setForeground(Color.RED);
        }

        highlightSegments();
    }

    /**
     * For each filled segment, check how many URLs match the partial regex
     * up to and including that segment.  If zero → colour red.
     */
    private void highlightSegments() {
        Color defaultFg = UIManager.getColor("TextField.foreground");
        if (defaultFg == null) defaultFg = Color.BLACK;

        StringBuilder partialRegex = new StringBuilder("https?://");
        boolean first = true;

        for (int i = 0; i < segmentBoxes.size(); i++) {
            String val = getSegmentValue(i);
            JComboBox<String> box = segmentBoxes.get(i);
            Component editor = box.getEditor().getEditorComponent();
            if (val.isEmpty()) {
                if (editor instanceof JTextField) {
                    ((JTextField) editor).setForeground(defaultFg);
                }
                continue;
            }

            if (!first) partialRegex.append("/");
            partialRegex.append(isRegexPattern(val) ? val : Pattern.quote(val));
            first = false;

            int hits = 0;
            try {
                Pattern p = Pattern.compile(partialRegex.toString() + "(/.*)?$",
                        Pattern.CASE_INSENSITIVE);
                for (String url : allUrls) {
                    if (p.matcher(url).matches()) {
                        hits++;
                        break; // one hit is enough — not zero
                    }
                }
            } catch (PatternSyntaxException ignore) {
                hits = -1;
            }

            if (editor instanceof JTextField) {
                ((JTextField) editor).setForeground(hits == 0 ? Color.RED : defaultFg);
            }
        }
    }

    /**
     * Update the external match-count label (called by the hosting dialog).
     */
    public void setMatchCount(int matched, int total) {
        matchCountLabel.setText(matched + " / " + total + " Links");
    }

    // ════════════════════════════════════════════════════════════════
    //  Persistence via applicationState
    // ════════════════════════════════════════════════════════════════

    /**
     * Persist the current non-empty segment values into
     * {@code Settings.applicationState["sp.filter.segments"]}.
     * Called by the hosting dialog after OK.
     */
    public void saveSegments() {
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < segmentBoxes.size(); i++) {
            String v = getSegmentValue(i);
            if (v.isEmpty()) break;
            values.add(v);
        }
        if (values.isEmpty()) return;

        Settings settings = SettingsHelper.load();
        settings.applicationState.put(STATE_KEY, join(values, "|"));
        SettingsHelper.save(settings);
    }

    /**
     * Load persisted segments, or {@code null} if none stored.
     */
    private static List<String> loadPersistedSegments() {
        Settings settings = SettingsHelper.load();
        String stored = settings.applicationState.get(STATE_KEY);
        if (stored == null || stored.isEmpty()) return null;
        String[] parts = stored.split("\\|", -1);
        List<String> result = new ArrayList<String>();
        for (String p : parts) {
            result.add(p);
        }
        return result.isEmpty() ? null : result;
    }

    /** Join strings with a delimiter (Java-8 compatible). */
    private static String join(List<String> list, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  Static helpers
    // ════════════════════════════════════════════════════════════════

    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return ".*";
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return ".*";
        }
    }

    /**
     * Extract dropdown suggestions for each segment position from the URL set.
     */
    private static Map<Integer, List<String>> extractSuggestions(List<String> urls) {
        Map<Integer, Set<String>> map = new LinkedHashMap<Integer, Set<String>>();
        for (String raw : urls) {
            try {
                URL u = new URL(raw);

                Set<String> hosts = map.get(0);
                if (hosts == null) {
                    hosts = new LinkedHashSet<String>();
                    map.put(0, hosts);
                }
                hosts.add(u.getHost());

                String path = u.getPath();
                if (path != null && !path.isEmpty()) {
                    String[] parts = path.split("/");
                    int segIdx = 1;
                    for (String part : parts) {
                        if (part.isEmpty()) continue;
                        Set<String> s = map.get(segIdx);
                        if (s == null) {
                            s = new LinkedHashSet<String>();
                            map.put(segIdx, s);
                        }
                        s.add(part);
                        segIdx++;
                    }
                }
            } catch (Exception ignore) { /* skip malformed URLs */ }
        }

        Map<Integer, List<String>> result = new LinkedHashMap<Integer, List<String>>();
        for (Map.Entry<Integer, Set<String>> entry : map.entrySet()) {
            List<String> vals = new ArrayList<String>(entry.getValue());
            if (vals.size() > MAX_HISTORY) {
                vals = vals.subList(0, MAX_HISTORY);
            }
            result.put(entry.getKey(), vals);
        }
        return result;
    }

    private static void addIfAbsent(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (value.equals(box.getItemAt(i))) return;
        }
        // Insert before the sentinel REMOVE_ITEM (always last)
        int insertIdx = box.getItemCount();
        for (int i = 0; i < box.getItemCount(); i++) {
            if (REMOVE_ITEM.equals(box.getItemAt(i))) {
                insertIdx = i;
                break;
            }
        }
        box.insertItemAt(value, insertIdx);
    }

    // ════════════════════════════════════════════════════════════════
    //  Change listener support
    // ════════════════════════════════════════════════════════════════

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChangeEvent() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }
}
