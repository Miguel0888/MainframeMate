package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.config.VideoConfig;
import de.bund.zrb.service.SettingsService;
import de.bund.zrb.ui.settings.SettingsCategory;
import de.bund.zrb.ui.video.VideoSettingsDialog;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

/**
 * Settings panel for video recording configuration.
 * Integrates with MainframeMate's Outlook-style SettingsDialog.
 * Provides basic on/off and output folder settings, with a button to
 * open the full VideoSettingsDialog for advanced per-backend configuration.
 */
public class VideoSettingsPanel extends JPanel implements SettingsCategory {

    private final JCheckBox cbVideoEnabled;
    private final JSpinner spVideoFps;
    private final JTextField tfVideoDir;
    private final JButton btVideoDetails;
    private final Component parentComponent;

    public VideoSettingsPanel(Component parentComponent) {
        this.parentComponent = parentComponent;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(section("Videoaufnahme"));
        GridBagConstraints g = gbc();

        // Enable/Disable checkbox
        cbVideoEnabled = new JCheckBox("Video aufzeichnen");
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3; g.anchor = GridBagConstraints.WEST;
        pnl.add(cbVideoEnabled, g);
        g.gridwidth = 1;

        // FPS spinner + advanced settings button
        JLabel lbFps = new JLabel("FPS:");
        spVideoFps = new JSpinner(new SpinnerNumberModel(15, 1, 120, 1));
        btVideoDetails = new JButton("\uD83C\uDFAC"); // 🎬
        btVideoDetails.setToolTipText("Erweiterte Video-Einstellungen (Backend, Codec, VLC, …)");
        btVideoDetails.setMargin(new Insets(0, 0, 0, 0));
        btVideoDetails.setFocusable(false);
        Dimension d26 = new Dimension(28, 28);
        btVideoDetails.setPreferredSize(d26);
        btVideoDetails.setMinimumSize(d26);
        btVideoDetails.setMaximumSize(d26);
        btVideoDetails.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            new VideoSettingsDialog(owner).setVisible(true);
        });

        g.gridx = 0; g.gridy = 1; g.gridwidth = 1; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pnl.add(lbFps, g);
        g.gridx = 1; g.gridy = 1; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pnl.add(spVideoFps, g);
        g.gridx = 2; g.gridy = 1; g.anchor = GridBagConstraints.CENTER; g.weightx = 0;
        pnl.add(btVideoDetails, g);

        // Output directory
        JLabel lbDir = new JLabel("Ausgabeordner:");
        tfVideoDir = new JTextField(28);
        JButton btBrowse = new JButton("Durchsuchen…");
        btBrowse.setFocusable(false);
        btBrowse.addActionListener(e -> chooseDir());

        g.gridx = 0; g.gridy = 2; g.gridwidth = 1; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pnl.add(lbDir, g);
        g.gridx = 1; g.gridy = 2; g.gridwidth = 2; g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        pnl.add(tfVideoDir, g);
        g.gridx = 1; g.gridy = 3; g.gridwidth = 2; g.anchor = GridBagConstraints.EAST; g.fill = GridBagConstraints.NONE; g.weightx = 0;
        pnl.add(btBrowse, g);

        add(pnl, BorderLayout.NORTH);
        add(Box.createVerticalGlue(), BorderLayout.CENTER);

        loadSettings();
    }

    @Override
    public String getId() {
        return "video";
    }

    @Override
    public String getTitle() {
        return "Video";
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void apply() {
        saveSettings();
    }

    @Override
    public void validate() throws IllegalArgumentException {
        int fps = ((Number) spVideoFps.getValue()).intValue();
        if (fps <= 0) throw new IllegalArgumentException("FPS muss > 0 sein.");
        String dir = tfVideoDir.getText().trim();
        if (dir.isEmpty()) throw new IllegalArgumentException("Bitte einen Ausgabeordner angeben.");
    }

    private void loadSettings() {
        SettingsService s = SettingsService.getInstance();
        Boolean enabled = s.get("video.enabled", Boolean.class);
        Integer fps = s.get("video.fps", Integer.class);
        String dir = s.get("video.reportsDir", String.class);

        cbVideoEnabled.setSelected(Boolean.TRUE.equals(enabled));
        spVideoFps.setValue(fps != null ? fps : 15);
        if (dir != null && !dir.trim().isEmpty()) {
            tfVideoDir.setText(dir.trim());
        } else {
            // Default: ~/Videos
            tfVideoDir.setText(VideoConfig.getReportsDir());
        }
    }

    private void saveSettings() {
        SettingsService s = SettingsService.getInstance();
        s.set("video.enabled", cbVideoEnabled.isSelected());
        s.set("video.fps", ((Number) spVideoFps.getValue()).intValue());
        String dir = tfVideoDir.getText().trim();
        if (!dir.isEmpty()) {
            s.set("video.reportsDir", dir);
            try { VideoConfig.setReportsDir(dir); } catch (Exception ignore) {}
        }
        Integer fps = ((Number) spVideoFps.getValue()).intValue();
        try { VideoConfig.setFps(fps); } catch (Exception ignore) {}
    }

    private void chooseDir() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String cur = tfVideoDir.getText().trim();
        if (!cur.isEmpty()) {
            File f = new File(cur);
            if (f.exists()) ch.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
        }
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && ch.getSelectedFile() != null) {
            tfVideoDir.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        return gbc;
    }

    private static TitledBorder section(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(title);
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD));
        return tb;
    }
}
