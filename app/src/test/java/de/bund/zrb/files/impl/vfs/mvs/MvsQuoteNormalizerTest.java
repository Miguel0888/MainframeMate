package de.bund.zrb.files.impl.vfs.mvs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MVS Quote Normalizer.
 */
class MvsQuoteNormalizerTest {

    @Test
    void normalizeAddsQuotesToUnquotedPath() {
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("HLQ"));
        assertEquals("'HLQ.DATA.SET'", MvsQuoteNormalizer.normalize("HLQ.DATA.SET"));
        assertEquals("'HLQ.PDS(MEM)'", MvsQuoteNormalizer.normalize("HLQ.PDS(MEM)"));
    }

    @Test
    void normalizePreservesAlreadyQuotedPath() {
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("'HLQ'"));
        assertEquals("'HLQ.DATA.SET'", MvsQuoteNormalizer.normalize("'HLQ.DATA.SET'"));
    }

    @Test
    void normalizeRemovesDoubleQuotes() {
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("''HLQ''"));
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("'''HLQ'''"));
    }

    @Test
    void normalizeHandlesWildcards() {
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.normalize("HLQ.*"));
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.normalize("'HLQ.*'"));
    }

    @Test
    void normalizeHandlesEmptyAndNull() {
        assertEquals("''", MvsQuoteNormalizer.normalize(null));
        assertEquals("''", MvsQuoteNormalizer.normalize(""));
        assertEquals("''", MvsQuoteNormalizer.normalize("  "));
    }

    @Test
    void normalizeRemovesTrailingDots() {
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("HLQ."));
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("HLQ.."));
        assertEquals("'HLQ.DATA'", MvsQuoteNormalizer.normalize("HLQ.DATA."));
        assertEquals("'HLQ'", MvsQuoteNormalizer.normalize("'HLQ.'"));
    }

    @Test
    void normalizePreservesTrailingDotsForWildcards() {
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.normalize("HLQ.*"));
        // Wildcard pattern should not be modified
    }

    @Test
    void unquoteRemovesOuterQuotes() {
        assertEquals("HLQ", MvsQuoteNormalizer.unquote("'HLQ'"));
        assertEquals("HLQ.DATA.SET", MvsQuoteNormalizer.unquote("'HLQ.DATA.SET'"));
    }

    @Test
    void unquoteRemovesMultipleQuotes() {
        assertEquals("HLQ", MvsQuoteNormalizer.unquote("''HLQ''"));
        assertEquals("HLQ", MvsQuoteNormalizer.unquote("'''HLQ'''"));
    }

    @Test
    void unquotePreservesUnquotedPath() {
        assertEquals("HLQ", MvsQuoteNormalizer.unquote("HLQ"));
        assertEquals("HLQ.DATA.SET", MvsQuoteNormalizer.unquote("HLQ.DATA.SET"));
    }

    @Test
    void toWildcardQueryAddsWildcard() {
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.toWildcardQuery("HLQ"));
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.toWildcardQuery("'HLQ'"));
    }

    @Test
    void toWildcardQueryPreservesExistingWildcard() {
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.toWildcardQuery("HLQ.*"));
        assertEquals("'HLQ.*'", MvsQuoteNormalizer.toWildcardQuery("'HLQ.*'"));
    }

    @Test
    void extractHlqFromDataset() {
        assertEquals("USERID", MvsQuoteNormalizer.extractHlq("USERID.DATA.SET"));
        assertEquals("USERID", MvsQuoteNormalizer.extractHlq("'USERID.DATA.SET'"));
        assertEquals("USERID", MvsQuoteNormalizer.extractHlq("'USERID.PDS(MEM)'"));
    }
}

