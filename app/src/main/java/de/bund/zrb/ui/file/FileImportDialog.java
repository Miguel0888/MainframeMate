package de.bund.zrb.ui.file;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.helper.WorkflowStorage;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.io.File;
import java.util.List;

public class FileImportDialog extends JDialog {

    private final WorkflowRunner workflowRunner;

    private Timer countdownTimer;
    private int timeLeft = Integer.MAX_VALUE;
    private final JLabel timerLabel;
    private final JComboBox<String> templateBox;
    private final JCheckBox rememberBox;
    private boolean userInteracted = false;
    private final AnimatedTimerCircle animatedCircle;

    public FileImportDialog(Frame owner, File file, WorkflowRunner workflowRunner) {
        super(owner, "Datei-Import: " + file.getName(), true);
        this.workflowRunner = workflowRunner;

        Settings settings = SettingsHelper.load();
        String defaultWorkflow = settings.defaultWorkflow;
        timeLeft = settings.importDelay;

        setLayout(new BorderLayout());

        // Timeranzeige mit Animation
        timerLabel = new JLabel(String.valueOf(timeLeft), SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 48));

        animatedCircle = new AnimatedTimerCircle(timerLabel);
        int diameter = timerLabel.getPreferredSize().height + 20;
        animatedCircle.setPreferredSize(new Dimension(diameter, diameter));


        JPanel timerPanel = new JPanel(new GridBagLayout());
        timerPanel.add(animatedCircle);

        // Rechte Seite mit Template- und Checkbox-Bereich
        templateBox = new JComboBox<>(WorkflowStorage.listWorkflowNames().toArray(new String[0]));
        templateBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        if (defaultWorkflow != null) {
            templateBox.setSelectedItem(defaultWorkflow);
        }

        rememberBox = new JCheckBox("als Standard merken");
        JPanel rememberPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rememberPanel.add(rememberBox);

        JPanel templatePanel = new JPanel();
        templatePanel.setLayout(new BoxLayout(templatePanel, BoxLayout.Y_AXIS));
        templatePanel.setMaximumSize(new Dimension(200, 100));
        templatePanel.add(templateBox);
        templatePanel.add(Box.createVerticalStrut(10));
        templatePanel.add(rememberPanel);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 20);
        centerPanel.add(timerPanel, gbc);

        gbc.gridx = 1;
        centerPanel.add(templatePanel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // Button Panel zentriert
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");
        okButton.addActionListener(e -> confirm(file));
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Timer + Interaktion
        countdownTimer = new Timer(1000, e -> {
            if (--timeLeft <= 0) {
                countdownTimer.stop();
                timerLabel.setForeground(Color.RED);
                animatedCircle.stop();
                confirm(file);
            } else {
                timerLabel.setText(String.valueOf(timeLeft));
            }
        });

        templateBox.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                cancelTimer();
            }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                cancelTimer();
            }
            public void popupMenuCanceled(PopupMenuEvent e) {
                cancelTimer();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                cancelTimer();
            }
        });

        countdownTimer.start();
        animatedCircle.start();

        pack();
        setLocationRelativeTo(owner);
    }

    private void cancelTimer() {
        if (!userInteracted) {
            countdownTimer.stop();
            timerLabel.setForeground(Color.RED);
            animatedCircle.stop();
            userInteracted = true;
        }
    }

    private void confirm(File file) {
        dispose();

        String selected = (String) templateBox.getSelectedItem();
        if (selected == null || selected.trim().isEmpty()) {
            JOptionPane.showMessageDialog(getOwner(),
                    "Kein Workflow ausgewählt.",
                    "Fehler", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<WorkflowStep> steps = WorkflowStorage.loadWorkflow(selected);
        if (steps.isEmpty()) {
            JOptionPane.showMessageDialog(getOwner(),
                    "Workflow \"" + selected + "\" ist leer oder nicht vorhanden.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (rememberBox.isSelected()) {
            Settings settings = SettingsHelper.load();
            settings.defaultWorkflow = selected;
            SettingsHelper.save(settings);
        }

        // ToDo: Remove this
        JOptionPane.showMessageDialog(getOwner(),
                "Import von Datei '" + file.getName() + "' (Pfad: " + file.getAbsolutePath() + ") wird jetzt gestartet...",
                "Import starten", JOptionPane.INFORMATION_MESSAGE);

        workflowRunner.execute(steps);
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

            // Rotierender Pfeil
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

    @Override
    public void dispose() {
        cancelTimer();   // ← Wichtig: Timer & Animation stoppen
        super.dispose();
    }
}
