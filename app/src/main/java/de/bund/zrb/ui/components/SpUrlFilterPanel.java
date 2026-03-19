package de.bund.zrb.ui.components;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dynamic URL filter panel with editable dropdowns for each URL path segment.
 * <p>
 * The first dropdown is the domain (including subdomain), subsequent ones are
 * path segments separated by '/'. An empty trailing dropdown signals
 * "end of pattern – no trailing slash".
 * <p>
 * When the user types into the last dropdown, a new empty dropdown is appended
 * automatically. Trailing empty dropdowns beyond one are removed.
 * <p>
 * Default configuration: {@code <DOMAIN> / sites / [^/]+ / (empty)}
 * <br>This matches URLs like {@code https://domain/sites/SiteName}.
 * <p>
 * Each segment value is interpreted as a <b>regex</b> if it contains
 * metacharacters ({@code [ ] ( ) * + ? { } | \ ^ $}); otherwise it is
 * escaped for literal matching via {@link Pattern#quote(String)}.
 */
public class SpUrlFilterPanel extends JPanel {

    private final List<JComboBox<String>> segmentBoxes = new ArrayList<JComboBox<String>>();
    private final List<JLabel> separatorLabels = new ArrayList<JLabel>();
    private final JPanel segmentPanel;
    private final JLabel regexPreview;
    private final JLabel matchCountLabel;
    private final List<Runnable> changeListeners = new ArrayList<Runnable>();
    private final Map<Integer, List<String>> segmentSuggestions;
    private boolean updating = false;

    /**
     * @param allUrls    all discovered URLs (for extracting dropdown suggestions)
     * @param scannedUrl the original URL that was scanned (for default domain)
     */
    public SpUrlFilterPanel(List<String> allUrls, String scannedUrl) {
        setLayout(new BorderLayout(4, 2));
        setBorder(BorderFactory.createTitledBorder("URL-Filter (Regex pro Pfadsegment)"));

        segmentSuggestions = extractSuggestions(allUrls);

        segmentPanel = new JPanel();
        segmentPanel.setLayout(new BoxLayout(segmentPanel, BoxLayout.X_AXIS));

        // Protocol prefix label
        JLabel proto = new JLabel(" https://");
        proto.setFont(proto.getFont().deriveFont(Font.BOLD));
        segmentPanel.add(proto);

        // ── Default segments ──
        String defaultDomain = extractDomain(scannedUrl);
        addSegmentBox(defaultDomain, true);   // [0] Domain
        addSegmentBox("sites", false);        // [1] /sites
        addSegmentBox("[^/]+", false);        // [2] /[^/]+  (any single segment)
        addSegmentBox("", false);             // [3] empty → end, no trailing slash

        JScrollPane scroll = new JScrollPane(segmentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(700, 36));
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

    // ═══════════════════════════════════════════════════════════════
    //  Segment management
    // ═══════════════════════════════════════════════════════════════

    private void addSegmentBox(String defaultValue, boolean isFirst) {
        int segIndex = segmentBoxes.size();

        // Add separator "/" before non-first segments
        if (!isFirst) {
            JLabel sep = new JLabel(" / ");
            sep.setFont(sep.getFont().deriveFont(Font.BOLD));
            separatorLabels.add(sep);
            segmentPanel.add(sep);
        }

        // Editable combo box
        JComboBox<String> box = new JComboBox<String>();
        box.setEditable(true);
        box.setPreferredSize(new Dimension(140, 26));
        box.setMaximumSize(new Dimension(200, 26));

        // Populate with suggestions from actual URL data
        List<String> suggestions = segmentSuggestions.get(segIndex);
        if (suggestions != null) {
            Set<String> added = new LinkedHashSet<String>();
            for (String s : suggestions) {
                if (added.add(s)) {
                    box.addItem(s);
                }
            }
        }
        // Add common regex helpers if not already present
        if (segIndex > 0) {
            addIfAbsent(box, "[^/]+");
            addIfAbsent(box, ".*");
        }

        // Set default value (after populating items, so it shows correctly)
        box.getEditor().setItem(defaultValue != null ? defaultValue : "");

        // ── Change listeners ──
        final int idx = segIndex;

        box.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!updating) onSegmentChanged(idx);
            }
        });

        Component editorComp = box.getEditor().getEditorComponent();
        if (editorComp instanceof JTextField) {
            ((JTextField) editorComp).getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { scheduleUpdate(idx); }
                @Override public void removeUpdate(DocumentEvent e) { scheduleUpdate(idx); }
                @Override public void changedUpdate(DocumentEvent e) { scheduleUpdate(idx); }
            });
        }

        segmentBoxes.add(box);
        segmentPanel.add(box);
    }

    private void scheduleUpdate(final int index) {
        if (updating) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                onSegmentChanged(index);
            }
        });
    }

    private void onSegmentChanged(int index) {
        if (updating) return;
        updating = true;
        try {
            String value = getSegmentValue(index);

            // If the last segment just got a value → add a new empty dropdown
            if (index == segmentBoxes.size() - 1 && !value.isEmpty()) {
                addSegmentBox("", false);
                segmentPanel.revalidate();
                segmentPanel.repaint();
            }

            // Remove trailing empty segments beyond the last non-empty + 1
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
                // Remove the very last empty one
                JComboBox<String> removed = segmentBoxes.remove(last);
                segmentPanel.remove(removed);
                if (!separatorLabels.isEmpty()) {
                    JLabel sep = separatorLabels.remove(separatorLabels.size() - 1);
                    segmentPanel.remove(sep);
                }
            } else {
                break;
            }
        }
        segmentPanel.revalidate();
        segmentPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Regex building
    // ═══════════════════════════════════════════════════════════════

    private String getSegmentValue(int index) {
        if (index < 0 || index >= segmentBoxes.size()) return "";
        Object item = segmentBoxes.get(index).getEditor().getItem();
        return item != null ? item.toString().trim() : "";
    }

    /**
     * Build a regex pattern string from the current segment values.
     * <p>
     * Result looks like: {@code https?://domain/seg1/seg2$}
     */
    public String buildRegex() {
        StringBuilder sb = new StringBuilder("https?://");
        boolean first = true;
        for (int i = 0; i < segmentBoxes.size(); i++) {
            String val = getSegmentValue(i);
            if (val.isEmpty()) break; // empty → end
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

    /**
     * Check if a value contains regex metacharacters (and should therefore
     * be used as-is rather than quoted).
     */
    private static boolean isRegexPattern(String val) {
        for (int i = 0; i < val.length(); i++) {
            if ("[]()*+?{}|\\^$".indexOf(val.charAt(i)) >= 0) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Preview
    // ═══════════════════════════════════════════════════════════════

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
    }

    /**
     * Update the external match-count label (called by the hosting dialog).
     */
    public void setMatchCount(int matched, int total) {
        matchCountLabel.setText(matched + " / " + total + " Links");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

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
     * <ul>
     *   <li>Position 0 → unique hostnames</li>
     *   <li>Position 1..n → unique path segments at that depth</li>
     * </ul>
     */
    private static Map<Integer, List<String>> extractSuggestions(List<String> urls) {
        Map<Integer, Set<String>> map = new LinkedHashMap<Integer, Set<String>>();
        for (String raw : urls) {
            try {
                URL u = new URL(raw);

                // Segment 0 = host
                Set<String> hosts = map.get(0);
                if (hosts == null) {
                    hosts = new LinkedHashSet<String>();
                    map.put(0, hosts);
                }
                hosts.add(u.getHost());

                // Segments 1..n from path
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
            result.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return result;
    }

    private static void addIfAbsent(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (value.equals(box.getItemAt(i))) return;
        }
        box.addItem(value);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Change listener support
    // ═══════════════════════════════════════════════════════════════

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChangeEvent() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }
}
