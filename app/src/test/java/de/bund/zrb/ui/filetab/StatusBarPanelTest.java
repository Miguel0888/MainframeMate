package de.bund.zrb.ui.filetab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusBarPanelTest {

    @Test
    void setSelectedSentenceType_handlesEmptyComboModel() {
        StatusBarPanel panel = new StatusBarPanel();
        panel.getSentenceComboBox().removeAllItems();

        assertDoesNotThrow(() -> panel.setSelectedSentenceType(null));
        assertEquals(1, panel.getSentenceComboBox().getItemCount());
        assertEquals("", panel.getSentenceComboBox().getItemAt(0));
    }
}
