package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class JobPollingTab implements FtpTab {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel countdownLabel = new JLabel("3", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("⏳ Warte auf Datei...", SwingConstants.CENTER);
    private final JButton reloadButton = new JButton("🔄 Neu laden");
    private final JCheckBox autoRetryCheckbox = new JCheckBox("Automatisch erneut versuchen");

    private final AnimatedTimerCircle animatedCircle = new AnimatedTimerCircle(countdownLabel);

    private final FtpManager ftpManager;
    private final String path;
    private final String sentenceType;
    private final TabbedPaneManager tabManager;

    private final String searchPattern;
    private final Boolean toCompare;

    private final Timer retryTimer;
    private int retryCountdown = 3;

    public JobPollingTab(FtpManager ftpManager, TabbedPaneManager tabManager, String path, String sentenceType) {
        this(ftpManager, tabManager, path, sentenceType, null, null);
    }

    public JobPollingTab(FtpManager ftpManager, TabbedPaneManager tabManager, String path, String sentenceType, String searchPattern, Boolean toCompare) {
        this.ftpManager = ftpManager;
        this.path = path;
        this.sentenceType = sentenceType;
        this.tabManager = tabManager;
        this.searchPattern = searchPattern;
        this.toCompare = toCompare;

        retryTimer = new Timer(1000, e -> {
            retryCountdown--;
            countdownLabel.setText(String.valueOf(retryCountdown));
            if (retryCountdown <= 0) {
                retryCountdown = 3;
                countdownLabel.setText("3");
                attemptLoad();
            }
        });

        initUI();
        attemptLoad();

        autoRetryCheckbox.setSelected(true);
        updateRetryStatus();
    }

    private void initUI() {
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 28));
        animatedCircle.setPreferredSize(new Dimension(60, 60));
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.anchor = GridBagConstraints.CENTER;

        centerPanel.add(animatedCircle, gbc);
        centerPanel.add(statusLabel, gbc);
        centerPanel.add(reloadButton, gbc);
        centerPanel.add(autoRetryCheckbox, gbc);

        panel.add(centerPanel, BorderLayout.CENTER);

        reloadButton.addActionListener(e -> attemptLoad());
        autoRetryCheckbox.addActionListener(e -> updateRetryStatus());
    }

    private void updateRetryStatus() {
        if (autoRetryCheckbox.isSelected()) {
            retryCountdown = 3;
            countdownLabel.setText("3");
            retryTimer.start();
            animatedCircle.start();
        } else {
            retryTimer.stop();
            animatedCircle.stop();
        }
    }

    private void attemptLoad() {
        try {
            FtpFileBuffer buffer = ftpManager.openAbsolute(quoteMvsDataset(path));

            if (buffer != null) {
                retryTimer.stop();
                animatedCircle.stop();
                FileTabImpl realTab = new FileTabImpl(tabManager, ftpManager, buffer, sentenceType, searchPattern, toCompare);
                tabManager.replaceTab(this, realTab);
            } else {
                statusLabel.setText("❌ Datei noch nicht vorhanden");
            }
        } catch (IOException ex) {
            statusLabel.setText("Fehler beim Laden: " + ex.getMessage());
        }
    }

    private String quoteMvsDataset(String dsname) {
        if (!dsname.startsWith("'")) dsname = "'" + dsname;
        if (!dsname.endsWith("'")) dsname = dsname + "'";
        return dsname;
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {}

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public Type getType() {
        return null;
    }

    @Override
    public String getTitle() {
        return "[Warte auf Datei]";
    }

    @Override
    public String getTooltip() {
        return path;
    }

    @Override
    public void onClose() {
        retryTimer.stop();
        animatedCircle.stop();
    }

    @Override
    public void saveIfApplicable() {}

    @Override
    public void focusSearchField() {
        // dummy
    }

    @Override
    public void searchFor(String searchPattern) {
        // dummy
    }

    // Komponente mit animiertem rotierendem Pfeilkreis
    private static class AnimatedTimerCircle extends JComponent {
        private final JLabel centerLabel;
        private double angle = 0;
        private Timer animationTimer;

        public AnimatedTimerCircle(JLabel centerLabel) {
            this.centerLabel = centerLabel;
            setLayout(new GridBagLayout());
            add(centerLabel);
        }

        public void start() {
            if (animationTimer != null && animationTimer.isRunning()) return;
            animationTimer = new Timer(50, e -> {
                angle += Math.PI / 30;
                repaint();
            });
            animationTimer.start();
        }

        public void stop() {
            if (animationTimer != null) {
                animationTimer.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g2.setStroke(new BasicStroke(3f));
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawOval(x, y, size, size);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int radius = size / 2;
            int px = (int) (cx + radius * Math.cos(angle));
            int py = (int) (cy + radius * Math.sin(angle));

            g2.setColor(Color.BLUE);
            g2.fillOval(px - 5, py - 5, 10, 10);
            g2.dispose();
        }
    }
}
