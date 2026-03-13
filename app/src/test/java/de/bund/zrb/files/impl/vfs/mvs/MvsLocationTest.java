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

    // === Wildcard tests ===

    @Test
    void parseHlqWildcardReturnsHlq() {
        MvsLocation loc = MvsLocation.parse("APAB*");
        assertEquals(MvsLocationType.HLQ, loc.getType());
        assertEquals("'APAB*'", loc.getLogicalPath());
    }

    @Test
    void parseQualifiedWildcardReturnsQualifierContext() {
        MvsLocation loc = MvsLocation.parse("KKR07.ZABA*");
        assertEquals(MvsLocationType.QUALIFIER_CONTEXT, loc.getType());
        assertEquals("'KKR07.ZABA*'", loc.getLogicalPath());
    }

    @Test
    void wildcardHlqQueryPathPreservesWildcard() {
        MvsLocation loc = MvsLocation.hlq("APAB*");
        // Already ends with *, should NOT append .*
        assertEquals("'APAB*'", loc.getQueryPath());
    }

    @Test
    void wildcardQualifierContextQueryPathPreservesWildcard() {
        MvsLocation loc = MvsLocation.qualifierContext("KKR07.ZABA*");
        assertEquals("'KKR07.ZABA*'", loc.getQueryPath());
    }

    @Test
    void getWildcardBaseForHlqWildcard() {
        assertEquals("", MvsQuoteNormalizer.getWildcardBase("APAB*"));
        assertEquals("", MvsQuoteNormalizer.getWildcardBase("'APAB*'"));
    }

    @Test
    void getWildcardBaseForQualifiedWildcard() {
        assertEquals("KKR07", MvsQuoteNormalizer.getWildcardBase("KKR07.ZABA*"));
        assertEquals("KKR07", MvsQuoteNormalizer.getWildcardBase("'KKR07.ZABA*'"));
    }

    @Test
    void getWildcardBaseForDeepWildcard() {
        assertEquals("A.B", MvsQuoteNormalizer.getWildcardBase("A.B.C*"));
    }

    @Test
    void getWildcardBaseForNoWildcard() {
        assertEquals("KKR07.DATA", MvsQuoteNormalizer.getWildcardBase("KKR07.DATA"));
    }

    @Test
    void hasWildcardDetectsAsterisk() {
        assertTrue(MvsQuoteNormalizer.hasWildcard("APAB*"));
        assertTrue(MvsQuoteNormalizer.hasWildcard("'KKR07.ZABA*'"));
        assertFalse(MvsQuoteNormalizer.hasWildcard("BENUTZERKENNUNG"));
        assertFalse(MvsQuoteNormalizer.hasWildcard("'BENUTZERKENNUNG.PDS'"));
    }
}

