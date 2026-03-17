package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.config.VideoConfig;
import de.bund.zrb.service.SettingsService;
import de.bund.zrb.ui.settings.SettingsCategory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Settings panel for video recording configuration.
 * Integrates all backend settings (JCodec, VLC, FFmpeg) directly as tabs
 * within the Outlook-style SettingsDialog – no extra popup dialog needed.
 */
public class VideoSettingsPanel extends JPanel implements SettingsCategory {

    private final Component parentComponent;

    // --- General ---
    private final JComboBox<String> cbBackend;
    private final JSpinner spVideoFps;
    private final JTextField tfVideoDir;

    // --- JCodec ---
    private JComboBox<String> cbJcodecContainer;
    private JCheckBox cbJcodecAudio;

    // --- VLC ---
    private JCheckBox cbVlcAutodetect;
    private JTextField tfVlcBasePath;
    private JButton btVlcBaseBrowse;
    private JCheckBox cbVlcLogEnabled;
    private JTextField tfVlcLogPath;
    private JButton btVlcLogBrowse;
    private JSpinner spVlcVerbose;
    private JComboBox<String> cbVlcMux;
    private JTextField tfVlcVcodec;
    private JComboBox<String> cbVlcQuality;
    private JSpinner spVlcCrf;
    private JSpinner spVlcBitrate;
    private JCheckBox cbVlcDeint;
    private JTextField tfVlcDeintMode;
    private JTextField tfVlcVFilter;
    private JTextField tfVlcPreset;
    private JTextField tfVlcTune;
    private JTextField tfVlcSoutExtras;
    private JCheckBox cbVlcFullscreen;
    private JSpinner spVlcLeft, spVlcTop, spVlcWidth, spVlcHeight;
    private JCheckBox cbVlcAudioEnabled;

    // --- FFmpeg ---
    private JComboBox<String> cbContainer, cbCodec, cbPixFmt, cbQuality;
    private JCheckBox cbInterleaved, cbEvenDims;
    private JSpinner spThreads, spQscale, spCrf, spBitrate;
    private JTextField tfColorRange, tfColorSpace, tfColorTrc, tfColorPrim, tfPreset2, tfTune2, tfProfile, tfLevel;
    private JTextField tfVf, tfFallbacksCsv;
    private JTable tblExtra;
    private DefaultTableModel extraModel;

    public VideoSettingsPanel(Component parentComponent) {
        this.parentComponent = parentComponent;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ====== Top: General settings ======
        JPanel pGeneral = new JPanel(new GridBagLayout());
        pGeneral.setBorder(section("Allgemein"));
        GridBagConstraints g = gbc();

        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pGeneral.add(new JLabel("Backend:"), g);
        cbBackend = new JComboBox<>(new String[]{"jcodec", "vlc", "ffmpeg"});
        g.gridx = 1; g.gridy = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        pGeneral.add(cbBackend, g);

        g.gridx = 2; g.gridy = 0; g.weightx = 0;
        pGeneral.add(Box.createHorizontalStrut(24), g);

        g.gridx = 3; g.gridy = 0; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pGeneral.add(new JLabel("FPS:"), g);
        spVideoFps = new JSpinner(new SpinnerNumberModel(15, 1, 120, 1));
        ((JSpinner.DefaultEditor) spVideoFps.getEditor()).getTextField().setColumns(4);
        g.gridx = 4; g.gridy = 0; g.weightx = 0;
        pGeneral.add(spVideoFps, g);

        // filler
        g.gridx = 5; g.gridy = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        pGeneral.add(Box.createHorizontalGlue(), g);
        g.fill = GridBagConstraints.NONE; g.weightx = 0;

        // Output dir row
        g.gridx = 0; g.gridy = 1; g.anchor = GridBagConstraints.WEST;
        pGeneral.add(new JLabel("Ausgabeordner:"), g);
        tfVideoDir = new JTextField(24);
        g.gridx = 1; g.gridy = 1; g.gridwidth = 4; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        pGeneral.add(tfVideoDir, g);
        g.gridwidth = 1; g.weightx = 0; g.fill = GridBagConstraints.NONE;

        JButton btBrowse = new JButton("\u2026");
        btBrowse.setToolTipText("Ausgabeordner w\u00e4hlen");
        btBrowse.setMargin(new Insets(0, 4, 0, 4));
        btBrowse.setFocusable(false);
        btBrowse.addActionListener(e -> chooseDir(tfVideoDir));
        g.gridx = 5; g.gridy = 1;
        pGeneral.add(btBrowse, g);

        // ====== Tabbed backend panels ======
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("JCodec", buildJcodecPanel());
        tabs.addTab("VLC", buildVlcPanel());
        tabs.addTab("FFmpeg", buildFfmpegPanel());

        // Auto-select tab matching backend
        cbBackend.addActionListener(e -> {
            String sel = String.valueOf(cbBackend.getSelectedItem());
            if ("jcodec".equals(sel)) tabs.setSelectedIndex(0);
            else if ("vlc".equals(sel)) tabs.setSelectedIndex(1);
            else if ("ffmpeg".equals(sel)) tabs.setSelectedIndex(2);
        });

        add(pGeneral, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        loadSettings();
    }

    // ==================== JCodec Panel ====================

    private JPanel buildJcodecPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = gbc();
        int r = 0;

        JLabel info = new JLabel("<html>JCodec \u2014 reines Java, kein natives Setup n\u00f6tig.<br>Kein Audio-Support (experimentelles WAV optional).</html>");
        info.setFont(info.getFont().deriveFont(Font.ITALIC));
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; g.anchor = GridBagConstraints.WEST;
        p.add(info, g);
        g.gridwidth = 1;

        cbJcodecContainer = new JComboBox<>(new String[]{"mp4", "avi", "mkv"});
        addRow(p, g, r++, "Container:", cbJcodecContainer);

        cbJcodecAudio = new JCheckBox("Experimentelles Audio (separate WAV, Java-only)");
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; g.anchor = GridBagConstraints.WEST;
        p.add(cbJcodecAudio, g);
        g.gridwidth = 1;

        // filler
        g.gridx = 0; g.gridy = r; g.weighty = 1; g.fill = GridBagConstraints.VERTICAL;
        p.add(Box.createVerticalGlue(), g);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        return wrap;
    }

    // ==================== VLC Panel ====================

    private JPanel buildVlcPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // --- Installation ---
        JPanel pTop = new JPanel(new GridBagLayout());
        pTop.setBorder(section("Installation / Pfade"));
        GridBagConstraints g = gbc();
        int row = 0;

        cbVlcAutodetect = new JCheckBox("Autodetect (PATH/Registry)");
        cbVlcAutodetect.addActionListener(e -> updateVlcUiState());
        g.gridx = 0; g.gridy = row++; g.gridwidth = 3; g.anchor = GridBagConstraints.WEST; g.weightx = 1;
        pTop.add(cbVlcAutodetect, g);
        g.gridwidth = 1;

        tfVlcBasePath = new JTextField(28);
        btVlcBaseBrowse = squareButton("\u2026");
        btVlcBaseBrowse.setToolTipText("VLC-Ordner w\u00e4hlen");
        btVlcBaseBrowse.addActionListener(e -> chooseDir(tfVlcBasePath));
        g.gridx = 0; g.gridy = row; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pTop.add(new JLabel("VLC-Basispfad:"), g);
        g.gridx = 1; g.gridy = row; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        pTop.add(tfVlcBasePath, g);
        g.gridx = 2; g.gridy = row++; g.fill = GridBagConstraints.NONE; g.weightx = 0;
        pTop.add(btVlcBaseBrowse, g);

        cbVlcLogEnabled = new JCheckBox("VLC-Logdatei schreiben");
        cbVlcLogEnabled.addActionListener(e -> updateVlcUiState());
        g.gridx = 0; g.gridy = row++; g.gridwidth = 3; g.anchor = GridBagConstraints.WEST; g.weightx = 1;
        pTop.add(cbVlcLogEnabled, g);
        g.gridwidth = 1;

        tfVlcLogPath = new JTextField(28);
        btVlcLogBrowse = squareButton("\u2026");
        btVlcLogBrowse.setToolTipText("Logdatei w\u00e4hlen");
        btVlcLogBrowse.addActionListener(e -> chooseFile(tfVlcLogPath));
        g.gridx = 0; g.gridy = row; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
        pTop.add(new JLabel("Logdatei:"), g);
        g.gridx = 1; g.gridy = row; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        pTop.add(tfVlcLogPath, g);
        g.gridx = 2; g.gridy = row++; g.fill = GridBagConstraints.NONE; g.weightx = 0;
        pTop.add(btVlcLogBrowse, g);

        spVlcVerbose = new JSpinner(new SpinnerNumberModel(1, 0, 2, 1));
        addRow(pTop, g, row++, "Verbose:", spVlcVerbose);

        p.add(pTop);
        p.add(Box.createVerticalStrut(6));

        // --- Transcode ---
        JPanel pRec = new JPanel(new GridBagLayout());
        pRec.setBorder(section("Transcode / Ausgabe"));
        GridBagConstraints gt = gbc();
        int tr = 0;
        cbVlcMux = new JComboBox<>(new String[]{"mp4", "ts", "mkv", "avi"});
        tfVlcVcodec = new JTextField(10);
        cbVlcQuality = new JComboBox<>(new String[]{"crf", "bitrate"});
        spVlcCrf = new JSpinner(new SpinnerNumberModel(23, 0, 51, 1));
        spVlcBitrate = new JSpinner(new SpinnerNumberModel(4000, 0, 200000, 100));
        cbVlcDeint = new JCheckBox("Deinterlace");
        tfVlcDeintMode = new JTextField(10);
        tfVlcVFilter = new JTextField(24);
        tfVlcPreset = new JTextField(10);
        tfVlcTune = new JTextField(10);
        tfVlcSoutExtras = new JTextField(24);

        addRow(pRec, gt, tr++, "Mux:", cbVlcMux);
        addRow(pRec, gt, tr++, "vcodec:", tfVlcVcodec);
        addRow(pRec, gt, tr++, "Qualit\u00e4t:", cbVlcQuality);
        addRow(pRec, gt, tr++, "CRF:", spVlcCrf);
        addRow(pRec, gt, tr++, "Bitrate (kbps):", spVlcBitrate);
        addRow(pRec, gt, tr++, "Deinterlace:", cbVlcDeint);
        addRow(pRec, gt, tr++, "Deint-Mode:", tfVlcDeintMode);
        addRow(pRec, gt, tr++, "Video-Filter:", tfVlcVFilter);
        addRow(pRec, gt, tr++, "x264/x265 preset:", tfVlcPreset);
        addRow(pRec, gt, tr++, "x264/x265 tune:", tfVlcTune);
        addRow(pRec, gt, tr++, "sout Extras:", tfVlcSoutExtras);

        p.add(pRec);
        p.add(Box.createVerticalStrut(6));

        // --- Screen Region / Audio ---
        JPanel pSrc = new JPanel(new GridBagLayout());
        pSrc.setBorder(section("Quelle: screen:// Region / Audio"));
        GridBagConstraints gs = gbc();
        int srow = 0;
        cbVlcFullscreen = new JCheckBox("Voller Bildschirm");
        cbVlcFullscreen.addActionListener(e -> updateVlcUiState());
        gs.gridx = 0; gs.gridy = srow++; gs.gridwidth = 3; gs.anchor = GridBagConstraints.WEST;
        pSrc.add(cbVlcFullscreen, gs);
        gs.gridwidth = 1;

        spVlcLeft = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        spVlcTop = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        spVlcWidth = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        spVlcHeight = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        addRow(pSrc, gs, srow++, "Left:", spVlcLeft);
        addRow(pSrc, gs, srow++, "Top:", spVlcTop);
        addRow(pSrc, gs, srow++, "Width:", spVlcWidth);
        addRow(pSrc, gs, srow++, "Height:", spVlcHeight);
        cbVlcAudioEnabled = new JCheckBox("Audio mitschneiden");
        gs.gridx = 0; gs.gridy = srow; gs.gridwidth = 3; gs.anchor = GridBagConstraints.WEST;
        pSrc.add(cbVlcAudioEnabled, gs);

        p.add(pSrc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        return wrapper;
    }

    // ==================== FFmpeg Panel ====================

    private JPanel buildFfmpegPanel() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // Encoding
        JPanel pEnc = new JPanel(new GridBagLayout());
        pEnc.setBorder(section("Encoding / Format"));
        GridBagConstraints g = gbc();

        cbContainer = new JComboBox<>(new String[]{"matroska", "mp4", "avi", "mov", "ts"});
        cbCodec = new JComboBox<>(new String[]{"mjpeg", "libx264", "libx265", "h264", "hevc"});
        cbPixFmt = new JComboBox<>(new String[]{"yuv420p", "yuv422p", "yuv444p", "rgb24", "bgr24"});
        cbInterleaved = new JCheckBox("Interleaved");

        int r = 0;
        addRow(pEnc, g, r++, "Container:", cbContainer);
        addRow(pEnc, g, r++, "Codec:", cbCodec);
        addRow(pEnc, g, r++, "Pixel-Format:", cbPixFmt);
        addRow(pEnc, g, r++, "", cbInterleaved);

        // Quality
        JPanel pQ = new JPanel(new GridBagLayout());
        pQ.setBorder(section("Qualit\u00e4t"));
        GridBagConstraints gq = gbc();
        cbQuality = new JComboBox<>(new String[]{"qscale", "crf", "bitrate"});
        spQscale = new JSpinner(new SpinnerNumberModel(3, 1, 31, 1));
        spCrf = new JSpinner(new SpinnerNumberModel(20, 0, 51, 1));
        spBitrate = new JSpinner(new SpinnerNumberModel(4000, 0, 200000, 100));

        CardLayout qCards = new CardLayout();
        JPanel pnlCards = new JPanel(qCards);
        pnlCards.add(wrapPair(new JLabel("QScale (1=sehr gut .. 31=schlecht):"), spQscale), "qscale");
        pnlCards.add(wrapPair(new JLabel("CRF (x264/x265, 0..51):"), spCrf), "crf");
        pnlCards.add(wrapPair(new JLabel("Bitrate (kbps):"), spBitrate), "bitrate");
        cbQuality.addActionListener(e -> qCards.show(pnlCards, String.valueOf(cbQuality.getSelectedItem())));

        addRow(pQ, gq, 0, "Modus:", cbQuality);
        gq.gridx = 0; gq.gridy = 1; gq.gridwidth = 2; gq.anchor = GridBagConstraints.WEST;
        pQ.add(pnlCards, gq);
        gq.gridwidth = 1;

        // Color / Filter
        JPanel pColor = new JPanel(new GridBagLayout());
        pColor.setBorder(section("Farbraum / Filter"));
        GridBagConstraints gc = gbc();
        tfColorRange = new JTextField(10);
        tfColorSpace = new JTextField(10);
        tfColorTrc = new JTextField(10);
        tfColorPrim = new JTextField(10);
        tfVf = new JTextField(36);
        addRow(pColor, gc, 0, "color_range:", tfColorRange);
        addRow(pColor, gc, 1, "colorspace:", tfColorSpace);
        addRow(pColor, gc, 2, "color_trc:", tfColorTrc);
        addRow(pColor, gc, 3, "color_primaries:", tfColorPrim);
        addRow(pColor, gc, 4, "FFmpeg Filter (vf):", tfVf);

        // System / Fallbacks
        JPanel pSys = new JPanel(new GridBagLayout());
        pSys.setBorder(section("System / Fallbacks"));
        GridBagConstraints gs = gbc();
        spThreads = new JSpinner(new SpinnerNumberModel(0, 0, 128, 1));
        cbEvenDims = new JCheckBox("Breite/H\u00f6he auf gerade Werte erzwingen");
        tfFallbacksCsv = new JTextField(24);
        tfFallbacksCsv.setToolTipText("CSV, z. B. avi,mp4");
        addRow(pSys, gs, 0, "Threads:", spThreads);
        addRow(pSys, gs, 1, "", cbEvenDims);
        addRow(pSys, gs, 2, "Container-Fallbacks:", tfFallbacksCsv);

        // x264/x265
        JPanel pX = new JPanel(new GridBagLayout());
        pX.setBorder(section("x264/x265 (optional)"));
        GridBagConstraints gx = gbc();
        tfPreset2 = new JTextField(10);
        tfTune2 = new JTextField(10);
        tfProfile = new JTextField(10);
        tfLevel = new JTextField(10);
        addRow(pX, gx, 0, "preset:", tfPreset2);
        addRow(pX, gx, 1, "tune:", tfTune2);
        addRow(pX, gx, 2, "profile:", tfProfile);
        addRow(pX, gx, 3, "level:", tfLevel);

        // Extra FFmpeg options
        JPanel pExtra = new JPanel(new BorderLayout());
        pExtra.setBorder(section("Weitere FFmpeg-Videooptionen"));
        extraModel = new DefaultTableModel(new Object[]{"Schl\u00fcssel", "Wert"}, 0) {
            @Override public boolean isCellEditable(int row2, int c) { return true; }
        };
        tblExtra = new JTable(extraModel);
        tblExtra.setFillsViewportHeight(true);
        pExtra.add(new JScrollPane(tblExtra), BorderLayout.CENTER);
        JPanel pExtraBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton btAdd = new JButton("Neu");
        JButton btDel = new JButton("L\u00f6schen");
        btAdd.addActionListener(e -> extraModel.addRow(new Object[]{"", ""}));
        btDel.addActionListener(e -> {
            int i = tblExtra.getSelectedRow();
            if (i >= 0) extraModel.removeRow(i);
        });
        pExtraBtns.add(btAdd);
        pExtraBtns.add(btDel);
        pExtra.add(pExtraBtns, BorderLayout.SOUTH);

        form.add(pEnc);     form.add(Box.createVerticalStrut(6));
        form.add(pQ);       form.add(Box.createVerticalStrut(6));
        form.add(pColor);   form.add(Box.createVerticalStrut(6));
        form.add(pSys);     form.add(Box.createVerticalStrut(6));
        form.add(pX);       form.add(Box.createVerticalStrut(6));
        form.add(pExtra);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new JScrollPane(form, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        return wrapper;
    }

    // ==================== SettingsCategory ====================

    @Override
    public String getId() { return "video"; }

    @Override
    public String getTitle() { return "Video"; }

    @Override
    public JComponent getComponent() { return this; }

    @Override
    public void apply() { saveSettings(); }

    @Override
    public void validate() throws IllegalArgumentException {
        int fps = ((Number) spVideoFps.getValue()).intValue();
        if (fps <= 0) throw new IllegalArgumentException("FPS muss > 0 sein.");
        String dir = tfVideoDir.getText().trim();
        if (dir.isEmpty()) throw new IllegalArgumentException("Bitte einen Ausgabeordner angeben.");
    }

    // ==================== Load / Save ====================

    @SuppressWarnings("unchecked")
    private void loadSettings() {
        SettingsService s = SettingsService.getInstance();

        // General
        String backend = s.get("video.backend", String.class);
        if (backend == null || backend.trim().isEmpty()) backend = "jcodec";
        cbBackend.setSelectedItem(backend.trim().toLowerCase(Locale.ROOT));

        Integer fps = s.get("video.fps", Integer.class);
        spVideoFps.setValue(fps != null && fps > 0 ? fps : 15);

        String dir = s.get("video.reportsDir", String.class);
        tfVideoDir.setText(dir != null && !dir.trim().isEmpty() ? dir.trim() : VideoConfig.getReportsDir());

        // JCodec
        String jc = s.get("video.jcodec.container", String.class);
        cbJcodecContainer.setSelectedItem(jc != null && !jc.trim().isEmpty() ? jc.trim() : "mp4");
        cbJcodecAudio.setSelected(Boolean.TRUE.equals(s.get("video.jcodec.audio.enabled", Boolean.class)));

        // VLC
        Boolean autod = s.get("video.vlc.autodetect", Boolean.class);
        cbVlcAutodetect.setSelected(Boolean.TRUE.equals(autod));
        tfVlcBasePath.setText(or(s.get("video.vlc.basePath", String.class), defaultVlcBasePath()));
        cbVlcLogEnabled.setSelected(Boolean.TRUE.equals(s.get("video.vlc.log.enabled", Boolean.class)));
        tfVlcLogPath.setText(or(s.get("video.vlc.log.path", String.class), defaultVlcLogPath()));
        Integer verb = s.get("video.vlc.verbose", Integer.class);
        spVlcVerbose.setValue(verb != null ? Math.max(0, Math.min(2, verb)) : 1);

        sel(cbVlcMux, s.get("video.vlc.mux", String.class), "mp4");
        tfVlcVcodec.setText(or(s.get("video.vlc.vcodec", String.class), "h264"));
        sel(cbVlcQuality, s.get("video.vlc.quality", String.class), "crf");
        setInt(spVlcCrf, or(s.get("video.vlc.crf", Integer.class), 23));
        setInt(spVlcBitrate, or(s.get("video.vlc.bitrateKbps", Integer.class), 4000));
        cbVlcDeint.setSelected(Boolean.TRUE.equals(s.get("video.vlc.deinterlace.enabled", Boolean.class)));
        tfVlcDeintMode.setText(or(s.get("video.vlc.deinterlace.mode", String.class), ""));
        tfVlcVFilter.setText(or(s.get("video.vlc.videoFilter", String.class), ""));
        tfVlcPreset.setText(or(s.get("video.vlc.venc.preset", String.class), ""));
        tfVlcTune.setText(or(s.get("video.vlc.venc.tune", String.class), ""));
        tfVlcSoutExtras.setText(or(s.get("video.vlc.soutExtras", String.class), ""));

        cbVlcFullscreen.setSelected(Boolean.TRUE.equals(s.get("video.vlc.screen.fullscreen", Boolean.class)));
        setInt(spVlcLeft, or(s.get("video.vlc.screen.left", Integer.class), 0));
        setInt(spVlcTop, or(s.get("video.vlc.screen.top", Integer.class), 0));
        setInt(spVlcWidth, or(s.get("video.vlc.screen.width", Integer.class), 0));
        setInt(spVlcHeight, or(s.get("video.vlc.screen.height", Integer.class), 0));
        cbVlcAudioEnabled.setSelected(Boolean.TRUE.equals(s.get("video.vlc.audio.enabled", Boolean.class)));

        // FFmpeg
        sel(cbContainer, s.get("video.container", String.class), "matroska");
        sel(cbCodec, s.get("video.codec", String.class), "mjpeg");
        sel(cbPixFmt, s.get("video.pixfmt", String.class), "yuv420p");
        cbInterleaved.setSelected(Boolean.TRUE.equals(s.get("video.interleaved", Boolean.class)));

        sel(cbQuality, s.get("video.quality", String.class), "qscale");
        setInt(spQscale, or(s.get("video.qscale", Integer.class), 3));
        setInt(spCrf, or(s.get("video.crf", Integer.class), 20));
        setInt(spBitrate, or(s.get("video.bitrateKbps", Integer.class), 0));

        tfColorRange.setText(or(s.get("video.color.range", String.class), "pc"));
        tfColorSpace.setText(or(s.get("video.color.space", String.class), "bt709"));
        tfColorTrc.setText(or(s.get("video.color.trc", String.class), "bt709"));
        tfColorPrim.setText(or(s.get("video.color.primaries", String.class), "bt709"));
        tfVf.setText(or(s.get("video.vf", String.class), "scale=in_range=pc:out_range=pc,format=yuv420p"));

        setInt(spThreads, or(s.get("video.threads", Integer.class), 0));
        cbEvenDims.setSelected(Boolean.TRUE.equals(s.get("video.enforceEvenDims", Boolean.class)));

        List<String> fbs = s.get("video.container.fallbacks", List.class);
        tfFallbacksCsv.setText(fbs != null ? joinCsv(fbs) : "avi,mp4");

        tfPreset2.setText(or(s.get("video.preset", String.class), ""));
        tfTune2.setText(or(s.get("video.tune", String.class), ""));
        tfProfile.setText(or(s.get("video.profile", String.class), ""));
        tfLevel.setText(or(s.get("video.level", String.class), ""));

        Map<String, String> extra = s.get("video.ffopts", Map.class);
        extraModel.setRowCount(0);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                extraModel.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        }

        updateVlcUiState();
    }

    private void saveSettings() {
        SettingsService s = SettingsService.getInstance();

        // General
        s.set("video.backend", String.valueOf(cbBackend.getSelectedItem()));
        int fpsVal = ((Number) spVideoFps.getValue()).intValue();
        s.set("video.fps", fpsVal);
        String dir = tfVideoDir.getText().trim();
        if (!dir.isEmpty()) {
            s.set("video.reportsDir", dir);
            try { VideoConfig.setReportsDir(dir); } catch (Exception ignore) {}
        }
        try { VideoConfig.setFps(fpsVal); } catch (Exception ignore) {}

        // JCodec
        s.set("video.jcodec.container", String.valueOf(cbJcodecContainer.getSelectedItem()));
        s.set("video.jcodec.audio.enabled", cbJcodecAudio.isSelected());

        // VLC
        s.set("video.vlc.autodetect", cbVlcAutodetect.isSelected());
        s.set("video.vlc.basePath", clean(tfVlcBasePath.getText()));
        s.set("video.vlc.log.enabled", cbVlcLogEnabled.isSelected());
        s.set("video.vlc.log.path", clean(tfVlcLogPath.getText()));
        s.set("video.vlc.verbose", ((Number) spVlcVerbose.getValue()).intValue());

        s.set("video.vlc.mux", str(cbVlcMux));
        s.set("video.vlc.vcodec", clean(tfVlcVcodec.getText()));
        s.set("video.vlc.quality", str(cbVlcQuality));
        s.set("video.vlc.crf", ((Number) spVlcCrf.getValue()).intValue());
        s.set("video.vlc.bitrateKbps", ((Number) spVlcBitrate.getValue()).intValue());
        s.set("video.vlc.deinterlace.enabled", cbVlcDeint.isSelected());
        s.set("video.vlc.deinterlace.mode", clean(tfVlcDeintMode.getText()));
        s.set("video.vlc.videoFilter", clean(tfVlcVFilter.getText()));
        s.set("video.vlc.venc.preset", emptyToNull(tfVlcPreset.getText()));
        s.set("video.vlc.venc.tune", emptyToNull(tfVlcTune.getText()));
        s.set("video.vlc.soutExtras", clean(tfVlcSoutExtras.getText()));

        s.set("video.vlc.screen.fullscreen", cbVlcFullscreen.isSelected());
        s.set("video.vlc.screen.left", ((Number) spVlcLeft.getValue()).intValue());
        s.set("video.vlc.screen.top", ((Number) spVlcTop.getValue()).intValue());
        s.set("video.vlc.screen.width", ((Number) spVlcWidth.getValue()).intValue());
        s.set("video.vlc.screen.height", ((Number) spVlcHeight.getValue()).intValue());
        s.set("video.vlc.audio.enabled", cbVlcAudioEnabled.isSelected());

        // FFmpeg
        s.set("video.container", str(cbContainer));
        s.set("video.codec", str(cbCodec));
        s.set("video.pixfmt", str(cbPixFmt));
        s.set("video.interleaved", cbInterleaved.isSelected());

        s.set("video.quality", str(cbQuality));
        s.set("video.qscale", ((Number) spQscale.getValue()).intValue());
        s.set("video.crf", ((Number) spCrf.getValue()).intValue());
        s.set("video.bitrateKbps", ((Number) spBitrate.getValue()).intValue());

        s.set("video.color.range", clean(tfColorRange.getText()));
        s.set("video.color.space", clean(tfColorSpace.getText()));
        s.set("video.color.trc", clean(tfColorTrc.getText()));
        s.set("video.color.primaries", clean(tfColorPrim.getText()));
        s.set("video.vf", clean(tfVf.getText()));

        s.set("video.threads", ((Number) spThreads.getValue()).intValue());
        s.set("video.enforceEvenDims", cbEvenDims.isSelected());

        List<String> fbs = parseCsv(tfFallbacksCsv.getText());
        s.set("video.container.fallbacks", fbs);

        s.set("video.preset", emptyToNull(tfPreset2.getText()));
        s.set("video.tune", emptyToNull(tfTune2.getText()));
        s.set("video.profile", emptyToNull(tfProfile.getText()));
        s.set("video.level", emptyToNull(tfLevel.getText()));

        Map<String, String> extra = new LinkedHashMap<>();
        for (int i = 0; i < extraModel.getRowCount(); i++) {
            String k = clean(String.valueOf(extraModel.getValueAt(i, 0)));
            String v = String.valueOf(extraModel.getValueAt(i, 1));
            if (!k.isEmpty() && v != null) extra.put(k, v);
        }
        s.set("video.ffopts", extra);

        // Propagate to VideoConfig runtime
        try {
            VideoConfig.setContainer(str(cbContainer));
            VideoConfig.setCodec(str(cbCodec));
            VideoConfig.setPixelFmt(str(cbPixFmt));
            VideoConfig.setInterleaved(cbInterleaved.isSelected());
            VideoConfig.setQualityMode(str(cbQuality));
            VideoConfig.setQscale(((Number) spQscale.getValue()).intValue());
            VideoConfig.setCrf(((Number) spCrf.getValue()).intValue());
            VideoConfig.setBitrateKbps(((Number) spBitrate.getValue()).intValue());
            VideoConfig.setColorRange(clean(tfColorRange.getText()));
            VideoConfig.setColorspace(clean(tfColorSpace.getText()));
            VideoConfig.setColorTrc(clean(tfColorTrc.getText()));
            VideoConfig.setColorPrimaries(clean(tfColorPrim.getText()));
            VideoConfig.setVf(clean(tfVf.getText()));
            VideoConfig.setThreads(((Number) spThreads.getValue()).intValue());
            VideoConfig.setEnforceEvenDims(cbEvenDims.isSelected());
            VideoConfig.setContainerFallbacks(fbs);
            VideoConfig.setPreset(emptyToNull(tfPreset2.getText()));
            VideoConfig.setTune(emptyToNull(tfTune2.getText()));
            VideoConfig.setProfile(emptyToNull(tfProfile.getText()));
            VideoConfig.setLevel(emptyToNull(tfLevel.getText()));
            VideoConfig.getExtraVideoOptions().clear();
            VideoConfig.getExtraVideoOptions().putAll(extra);
        } catch (Exception ignore) {
            // VideoConfig may fail on edge values – save to SettingsService was already done
        }
    }

    // ==================== UI State Helpers ====================

    private void updateVlcUiState() {
        boolean autod = cbVlcAutodetect.isSelected();
        tfVlcBasePath.setEnabled(!autod);
        btVlcBaseBrowse.setEnabled(!autod);

        boolean logOn = cbVlcLogEnabled.isSelected();
        tfVlcLogPath.setEnabled(logOn);
        btVlcLogBrowse.setEnabled(logOn);

        boolean regionEnabled = !cbVlcFullscreen.isSelected();
        spVlcLeft.setEnabled(regionEnabled);
        spVlcTop.setEnabled(regionEnabled);
        spVlcWidth.setEnabled(regionEnabled);
        spVlcHeight.setEnabled(regionEnabled);
    }

    // ==================== Layout Helpers ====================

    private void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent comp) {
        g.gridx = 0; g.gridy = row; g.anchor = GridBagConstraints.WEST; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        if (label != null && !label.isEmpty()) p.add(new JLabel(label), g);
        g.gridx = 1; g.gridy = row; g.anchor = GridBagConstraints.WEST; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(comp, g);
        g.fill = GridBagConstraints.NONE; g.weightx = 0;
    }

    private JPanel wrapPair(JComponent l, JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = gbc();
        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.WEST;
        p.add(l, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
        return p;
    }

    private JButton squareButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(0, 4, 0, 4));
        Dimension d = new Dimension(26, 26);
        b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
        b.setFocusable(false);
        return b;
    }

    private void chooseDir(JTextField target) {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String cur = target.getText().trim();
        if (!cur.isEmpty()) {
            File f = new File(cur);
            if (f.exists()) ch.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
        }
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && ch.getSelectedFile() != null) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseFile(JTextField target) {
        JFileChooser ch = new JFileChooser();
        String cur = target.getText().trim();
        if (!cur.isEmpty()) {
            File f = new File(cur);
            if (f.exists()) ch.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
        }
        if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION && ch.getSelectedFile() != null) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        return g;
    }

    private static TitledBorder section(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(title);
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD));
        return tb;
    }

    // ==================== Value Helpers ====================

    private static void setInt(JSpinner sp, int v) { sp.setValue(v); }
    private static <T> T or(T v, T d) { return v != null ? v : d; }
    private static void sel(JComboBox<String> cb, String v, String d) {
        cb.setSelectedItem(v != null && !v.isEmpty() ? v : d);
    }
    private static String str(JComboBox<String> cb) {
        Object o = cb.getSelectedItem();
        return o == null ? "" : o.toString();
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String emptyToNull(String s) {
        s = clean(s);
        return s.isEmpty() ? null : s;
    }

    private static String joinCsv(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null) return Collections.emptyList();
        String[] parts = csv.split(",");
        ArrayList<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String defaultVlcBasePath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "C:/Program Files/VideoLAN/VLC";
        if (os.contains("mac")) return "/Applications/VLC.app/Contents/MacOS/lib";
        return "/usr/lib";
    }

    private static String defaultVlcLogPath() {
        return new File(System.getProperty("user.home"), ".mainframemate/vlc.log").getAbsolutePath();
    }
}
