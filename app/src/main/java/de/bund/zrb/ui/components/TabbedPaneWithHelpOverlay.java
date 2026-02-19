package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;

import javax.swing.*;
import java.awt.*;

/**
 * Composite-Komponente, die ein JTabbedPane mit einem Overlay-Slot im Tab-Bereich enthält.
 * Der Overlay-Component (z.B. ein Hilfe-Button) wird rechtsbündig über der Tab-Leiste platziert,
 * aber nicht als eigener Tab behandelt.
 */
public class TabbedPaneWithHelpOverlay extends JPanel {

    private final JTabbedPane tabbedPane;
    private JComponent helpComponent;
    private int rightMargin = 6;

    public TabbedPaneWithHelpOverlay() {
        this(new JTabbedPane());
    }

    public TabbedPaneWithHelpOverlay(JTabbedPane tabbedPane) {
        super(null); // null-Layout für eigene doLayout()-Logik
        this.tabbedPane = tabbedPane;
        add(tabbedPane);
    }

    /**
     * Setzt die Help-Komponente, die als Overlay über der Tab-Leiste erscheint.
     * @param component Die Komponente (z.B. ein HelpButton), oder null zum Entfernen.
     */
    public void setHelpComponent(JComponent component) {
        if (helpComponent != null) {
            remove(helpComponent);
        }
        helpComponent = component;
        if (helpComponent != null) {
            helpComponent.setFocusable(false);
            helpComponent.setOpaque(false);
            add(helpComponent);
            // Sichtbarkeit basierend auf Einstellungen
            updateHelpVisibility();
        }
        revalidate();
        repaint();
    }

    /**
     * Aktualisiert die Sichtbarkeit des Hilfe-Buttons basierend auf den Benutzereinstellungen.
     */
    public void updateHelpVisibility() {
        if (helpComponent != null) {
            boolean showHelp = SettingsHelper.load().showHelpIcons;
            helpComponent.setVisible(showHelp);
        }
    }

    public JComponent getHelpComponent() {
        return helpComponent;
    }

    public void setRightMargin(int px) {
        this.rightMargin = px;
        revalidate();
        repaint();
    }

    public int getRightMargin() {
        return rightMargin;
    }

    /**
     * Gibt das interne JTabbedPane zurück, um Tab-spezifische Funktionen zu nutzen.
     */
    public JTabbedPane getTabbedPaneDelegate() {
        return tabbedPane;
    }

    // Delegiert häufig verwendete JTabbedPane-Methoden
    public void addTab(String title, Component component) {
        tabbedPane.addTab(title, component);
    }

    public void addTab(String title, Icon icon, Component component) {
        tabbedPane.addTab(title, icon, component);
    }

    public void insertTab(String title, Icon icon, Component component, String tip, int index) {
        tabbedPane.insertTab(title, icon, component, tip, index);
    }

    public void setTabComponentAt(int index, Component component) {
        tabbedPane.setTabComponentAt(index, component);
    }

    public int getTabCount() {
        return tabbedPane.getTabCount();
    }

    public String getTitleAt(int index) {
        return tabbedPane.getTitleAt(index);
    }

    public void setSelectedIndex(int index) {
        tabbedPane.setSelectedIndex(index);
    }

    public int getSelectedIndex() {
        return tabbedPane.getSelectedIndex();
    }

    public void setSelectedComponent(Component c) {
        tabbedPane.setSelectedComponent(c);
    }

    public Component getSelectedComponent() {
        return tabbedPane.getSelectedComponent();
    }

    public Component getComponentAt(int index) {
        return tabbedPane.getComponentAt(index);
    }

    public void remove(int index) {
        tabbedPane.remove(index);
    }

    public int indexOfComponent(Component component) {
        return tabbedPane.indexOfComponent(component);
    }

    public void setEnabledAt(int index, boolean enabled) {
        tabbedPane.setEnabledAt(index, enabled);
    }

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        tabbedPane.addChangeListener(l);
    }

    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth();
        int height = getHeight();

        // TabbedPane füllt den gesamten Bereich
        tabbedPane.setBounds(insets.left, insets.top,
                width - insets.left - insets.right,
                height - insets.top - insets.bottom);

        // Overlay positionieren
        layoutHelpComponent();

        // Z-Order sicherstellen: helpComponent oben
        if (helpComponent != null) {
            setComponentZOrder(helpComponent, 0);
            setComponentZOrder(tabbedPane, 1);
        }
    }

    private void layoutHelpComponent() {
        if (helpComponent == null) return;

        Insets insets = getInsets();
        Dimension overlaySize = helpComponent.getPreferredSize();
        int overlayWidth = overlaySize.width;
        int overlayHeight = overlaySize.height;

        int panelWidth = getWidth();

        // X-Position: rechtsbündig mit Margin
        int x = panelWidth - insets.right - rightMargin - overlayWidth;
        if (x < insets.left) {
            x = insets.left;
        }

        // Y-Position: vertikal zentriert im Tab-Strip
        int y = insets.top + 2; // Fallback
        if (tabbedPane.getTabCount() > 0) {
            Rectangle r0 = tabbedPane.getBoundsAt(0);
            if (r0 != null) {
                y = insets.top + r0.y + (r0.height - overlayHeight) / 2;
            }
        }

        helpComponent.setBounds(x, y, overlayWidth, overlayHeight);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // Layout nach UI-Updates erneut ausführen
        SwingUtilities.invokeLater(this::layoutHelpComponent);
    }

    @Override
    public Dimension getPreferredSize() {
        return tabbedPane.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return tabbedPane.getMinimumSize();
    }
}

