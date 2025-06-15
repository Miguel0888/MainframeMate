package de.bund.zrb.ui.file;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.helper.WorkflowStorage;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowTemplate;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.Map;

public class FileImportDialog extends JDialog {

    private final WorkflowRunner workflowRunner;
    private final JComboBox<String> variableBox = new JComboBox<>();;

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

        updateVariableBoxEntries((String) templateBox.getSelectedItem(), variableBox);
        templateBox.addActionListener(e -> {
            String selectedWorkflow = (String) templateBox.getSelectedItem();
            updateVariableBoxEntries(selectedWorkflow, variableBox);
        });

        JPanel templatePanel = new JPanel();
        templatePanel.setLayout(new BoxLayout(templatePanel, BoxLayout.Y_AXIS));
        templatePanel.setMaximumSize(new Dimension(200, 100));
        templatePanel.add(templateBox);
        variableBox.setEditable(true);
        Component editorComponent = variableBox.getEditor().getEditorComponent();
        editorComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                cancelTimer();
            }
        });

        editorComponent.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                cancelTimer();
            }
        });
        addDeleteOptionForVariables();
        templatePanel.add(variableBox);

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

    private void addDeleteOptionForVariables() {
        variableBox.setRenderer(new VariableEntryRenderer());

        variableBox.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                cancelTimer();
                JList<?> list = getComboBoxList(variableBox);
                if (list != null && list.getClientProperty("listener-added") == null) {
                    list.putClientProperty("listener-added", Boolean.TRUE); // verhindern von Doppelt-Registrierung
                    list.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            int index = list.locationToIndex(e.getPoint());
                            if (index >= 0) {
                                Rectangle bounds = list.getCellBounds(index, index);
                                int x = e.getX();
                                // Abschätzung: Rechts neben dem Text (Symbolbreite)
                                if (x > bounds.x + bounds.width - 30) {
                                    String selectedWorkflow = (String) templateBox.getSelectedItem();
                                    String selectedVar = (String) list.getModel().getElementAt(index);

                                    SwingUtilities.invokeLater(() -> {
                                        Settings settings = SettingsHelper.load();
                                        List<String> listVars = settings.fileImportVariables.get(selectedWorkflow);
                                        if (listVars != null && listVars.removeIf(v -> v.equalsIgnoreCase(selectedVar))) {
                                            SettingsHelper.save(settings);
                                            updateVariableBoxEntries(selectedWorkflow, variableBox);
                                        }
                                    });
                                }
                            }
                        }
                    });
                    ;
                }
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                cancelTimer();
            }
            public void popupMenuCanceled(PopupMenuEvent e) {
                cancelTimer();
            }
        });;

    }

    @SuppressWarnings("rawtypes")
    private JList getComboBoxList(JComboBox box) {
        for (int i = 0; i < box.getUI().getAccessibleChildrenCount(box); i++) {
            Object child = box.getUI().getAccessibleChild(box, i);
            if (child instanceof ComboPopup) {
                return ((ComboPopup) child).getList();
            }
        }
        return null;
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

        WorkflowTemplate template = WorkflowStorage.loadWorkflow(selected);
        if (template == null || template.getData().isEmpty()) {
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

        // Variable "file" aus dem Dateipfad erzeugen
        Map<String, String> overrides = new java.util.LinkedHashMap<>();
        String selectedVar = ((String) variableBox.getEditor().getItem()).trim();
        if (!selectedVar.isEmpty()) {
            overrides.put(selectedVar, file.getAbsolutePath());

            // Merken für spätere Aufrufe
            Settings settings = SettingsHelper.load();
            List<String> list = settings.fileImportVariables
                    .computeIfAbsent(selected, k -> new java.util.ArrayList<>());

            list.removeIf(v -> v.equalsIgnoreCase(selectedVar)); // Duplikat entfernen

            if (rememberBox.isSelected()) {
                list.add(selectedVar); // am Ende einfügen = wird Standard
            } else {
                list.add(0, selectedVar); // oben einfügen = temporäre Nutzung
            }

            SettingsHelper.save(settings);
        }

        // Neuen Runner aufrufen (inkl. overrides für {{file}})
        try {
            workflowRunner.execute(template, overrides);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(getOwner(),
                    "Import von Datei '" + file.getName() + "' (Pfad: " + file.getAbsolutePath() + ") fehlgeschlagen: " + e.getMessage(),
                    "Import starten", JOptionPane.INFORMATION_MESSAGE);
        }

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

    private void updateVariableBoxEntries(String workflowName, JComboBox<String> box) {
        box.removeAllItems();
        Settings settings = SettingsHelper.load();
        List<String> candidates = settings.fileImportVariables.get(workflowName);

        if (candidates != null && !candidates.isEmpty()) {
            for (String var : candidates) {
                box.addItem(var);
            }
            box.setSelectedItem(candidates.get(0));
        } else {
            box.addItem("file");
            box.setSelectedItem("file");
        }
    }

    private class VariableEntryRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel label = new JLabel();
        private final JLabel deleteIcon = new JLabel(" ❌");

        public VariableEntryRenderer() {
            setLayout(new BorderLayout());
            label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            add(label, BorderLayout.CENTER);
            add(deleteIcon, BorderLayout.EAST);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                                                      String value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            label.setText(value);
            deleteIcon.setVisible(index >= 0); // nicht bei Editor oben
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

}
