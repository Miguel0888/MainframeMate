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
    void parseSlashReturnsRoot() {
        MvsLocation loc = MvsLocation.parse("/");
        assertEquals(MvsLocationType.ROOT, loc.getType());
    }

    @Test
    void parseQuotedSlashReturnsRoot() {
        MvsLocation loc = MvsLocation.parse("'/'");
        assertEquals(MvsLocationType.ROOT, loc.getType());
    }

    @Test
    void parseHlqReturnsHlq() {
        MvsLocation loc = MvsLocation.parse("BENUTZERKENNUNG");
        assertEquals(MvsLocationType.HLQ, loc.getType());
        assertEquals("'BENUTZERKENNUNG'", loc.getLogicalPath());
        assertEquals("BENUTZERKENNUNG", loc.getDisplayName());
    }

    @Test
    void parseQuotedHlqReturnsHlq() {
        MvsLocation loc = MvsLocation.parse("'BENUTZERKENNUNG'");
        assertEquals(MvsLocationType.HLQ, loc.getType());
        assertEquals("'BENUTZERKENNUNG'", loc.getLogicalPath());
    }

    @Test
    void parseDatasetReturnsDataset() {
        MvsLocation loc = MvsLocation.parse("BENUTZERKENNUNG.DATA.SET");
        assertEquals(MvsLocationType.QUALIFIER_CONTEXT, loc.getType());
        assertEquals("'BENUTZERKENNUNG.DATA.SET'", loc.getLogicalPath());
        assertEquals("SET", loc.getDisplayName());
    }

    @Test
    void parseMemberReturnsMember() {
        MvsLocation loc = MvsLocation.parse("BENUTZERKENNUNG.PDS(MEMBER)");
        assertEquals(MvsLocationType.MEMBER, loc.getType());
        assertEquals("'BENUTZERKENNUNG.PDS(MEMBER)'", loc.getLogicalPath());
        assertEquals("MEMBER", loc.getDisplayName());
    }

    @Test
    void hlqQueryPathHasWildcard() {
        MvsLocation loc = MvsLocation.hlq("BENUTZERKENNUNG");
        assertEquals("'BENUTZERKENNUNG.*'", loc.getQueryPath());
    }

    @Test
    void qualifierContextQueryPathHasWildcard() {
        MvsLocation loc = MvsLocation.qualifierContext("BENUTZERKENNUNG.PDS");
        assertEquals("'BENUTZERKENNUNG.PDS.*'", loc.getQueryPath());
    }

    @Test
    void hlqChildIsQualifierContext() {
        MvsLocation parent = MvsLocation.hlq("BENUTZERKENNUNG");
        MvsLocation child = parent.createChild("DATA.SET");

        assertEquals(MvsLocationType.QUALIFIER_CONTEXT, child.getType());
        assertEquals("'BENUTZERKENNUNG.DATA.SET'", child.getLogicalPath());
    }

    @Test
    void hlqChildWithFullyQualifiedName() {
        MvsLocation parent = MvsLocation.hlq("BENUTZERKENNUNG");
        // Server returned fully qualified name
        MvsLocation child = parent.createChild("BENUTZERKENNUNG.DATA.SET");

        assertEquals(MvsLocationType.QUALIFIER_CONTEXT, child.getType());
        assertEquals("'BENUTZERKENNUNG.DATA.SET'", child.getLogicalPath());
    }

    @Test
    void datasetChildIsMember() {
        MvsLocation parent = MvsLocation.dataset("BENUTZERKENNUNG.PDS");
        MvsLocation child = parent.createChild("MEMBER1");

        assertEquals(MvsLocationType.MEMBER, child.getType());
        assertEquals("'BENUTZERKENNUNG.PDS(MEMBER1)'", child.getLogicalPath());
        assertEquals("MEMBER1", child.getDisplayName());
    }

    @Test
    void isDirectoryForHlqAndDataset() {
        assertTrue(MvsLocation.root().isDirectory());
        assertTrue(MvsLocation.hlq("BENUTZERKENNUNG").isDirectory());
        assertTrue(MvsLocation.qualifierContext("BENUTZERKENNUNG.PDS").isDirectory());
        assertTrue(MvsLocation.dataset("BENUTZERKENNUNG.PDS").isDirectory());
        assertFalse(MvsLocation.member("BENUTZERKENNUNG.PDS(MEM)").isDirectory());
    }
}

