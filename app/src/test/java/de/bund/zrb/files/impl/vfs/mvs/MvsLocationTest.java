package de.bund.zrb.files.impl.vfs.mvs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MVS Location state machine.
 */
class MvsLocationTest {

    @Test
    void parseEmptyReturnsRoot() {
        MvsLocation loc = MvsLocation.parse("");
        assertEquals(MvsLocationType.ROOT, loc.getType());
    }

    @Test
    void parseNullReturnsRoot() {
        MvsLocation loc = MvsLocation.parse(null);
        assertEquals(MvsLocationType.ROOT, loc.getType());
    }

    @Test
    void parseHlqReturnsHlq() {
        MvsLocation loc = MvsLocation.parse("USERID");
        assertEquals(MvsLocationType.HLQ, loc.getType());
        assertEquals("'USERID'", loc.getLogicalPath());
        assertEquals("USERID", loc.getDisplayName());
    }

    @Test
    void parseQuotedHlqReturnsHlq() {
        MvsLocation loc = MvsLocation.parse("'USERID'");
        assertEquals(MvsLocationType.HLQ, loc.getType());
        assertEquals("'USERID'", loc.getLogicalPath());
    }

    @Test
    void parseDatasetReturnsDataset() {
        MvsLocation loc = MvsLocation.parse("USERID.DATA.SET");
        assertEquals(MvsLocationType.DATASET, loc.getType());
        assertEquals("'USERID.DATA.SET'", loc.getLogicalPath());
        assertEquals("SET", loc.getDisplayName());
    }

    @Test
    void parseMemberReturnsMember() {
        MvsLocation loc = MvsLocation.parse("USERID.PDS(MEMBER)");
        assertEquals(MvsLocationType.MEMBER, loc.getType());
        assertEquals("'USERID.PDS(MEMBER)'", loc.getLogicalPath());
        assertEquals("MEMBER", loc.getDisplayName());
    }

    @Test
    void hlqQueryPathHasWildcard() {
        MvsLocation loc = MvsLocation.hlq("USERID");
        assertEquals("'USERID.*'", loc.getQueryPath());
    }

    @Test
    void datasetQueryPathIsItself() {
        MvsLocation loc = MvsLocation.dataset("USERID.PDS");
        assertEquals("'USERID.PDS'", loc.getQueryPath());
    }

    @Test
    void hlqChildIsDataset() {
        MvsLocation parent = MvsLocation.hlq("USERID");
        MvsLocation child = parent.createChild("DATA.SET");

        assertEquals(MvsLocationType.DATASET, child.getType());
        assertEquals("'USERID.DATA.SET'", child.getLogicalPath());
    }

    @Test
    void hlqChildWithFullyQualifiedName() {
        MvsLocation parent = MvsLocation.hlq("USERID");
        // Server returned fully qualified name
        MvsLocation child = parent.createChild("USERID.DATA.SET");

        assertEquals(MvsLocationType.DATASET, child.getType());
        assertEquals("'USERID.DATA.SET'", child.getLogicalPath());
    }

    @Test
    void datasetChildIsMember() {
        MvsLocation parent = MvsLocation.dataset("USERID.PDS");
        MvsLocation child = parent.createChild("MEMBER1");

        assertEquals(MvsLocationType.MEMBER, child.getType());
        assertEquals("'USERID.PDS(MEMBER1)'", child.getLogicalPath());
        assertEquals("MEMBER1", child.getDisplayName());
    }

    @Test
    void isDirectoryForHlqAndDataset() {
        assertTrue(MvsLocation.root().isDirectory());
        assertTrue(MvsLocation.hlq("USERID").isDirectory());
        assertTrue(MvsLocation.dataset("USERID.PDS").isDirectory());
        assertFalse(MvsLocation.member("USERID.PDS(MEM)").isDirectory());
    }
}

