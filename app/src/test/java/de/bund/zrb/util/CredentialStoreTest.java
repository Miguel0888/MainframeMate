package de.bund.zrb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CredentialStore} utility methods (key parsing, label extraction).
 * Actual store/resolve operations require settings infrastructure and are tested via integration tests.
 */
class CredentialStoreTest {

    @Test
    void wikiKey_createsCorrectKey() {
        assertEquals("wiki:wikipedia_de", CredentialStore.wikiKey("wikipedia_de"));
        assertEquals("wiki:my_wiki", CredentialStore.wikiKey("my_wiki"));
    }

    @Test
    void componentLabel_extractsKnownPrefixes() {
        assertEquals("Wiki", CredentialStore.componentLabel("wiki:wikipedia_de"));
        assertEquals("BetaView", CredentialStore.componentLabel("betaview"));
        assertEquals("FTP", CredentialStore.componentLabel("ftp:myhost"));
        assertEquals("NDV", CredentialStore.componentLabel("ndv:server1"));
    }

    @Test
    void componentLabel_unknownPrefix_returnsPrefixAsIs() {
        assertEquals("custom", CredentialStore.componentLabel("custom:something"));
    }

    @Test
    void componentLabel_noColon_returnsFullKey() {
        assertEquals("BetaView", CredentialStore.componentLabel("betaview"));
    }

    @Test
    void componentLabel_null_returnsEmpty() {
        assertEquals("", CredentialStore.componentLabel(null));
    }

    @Test
    void componentId_extractsIdAfterColon() {
        assertEquals("wikipedia_de", CredentialStore.componentId("wiki:wikipedia_de"));
        assertEquals("myhost.example.com", CredentialStore.componentId("ftp:myhost.example.com"));
    }

    @Test
    void componentId_noColon_returnsFullKey() {
        assertEquals("betaview", CredentialStore.componentId("betaview"));
    }

    @Test
    void componentId_null_returnsEmpty() {
        assertEquals("", CredentialStore.componentId(null));
    }

    @Test
    void componentId_multipleColons_splitsOnFirst() {
        assertEquals("host:8080", CredentialStore.componentId("ftp:host:8080"));
    }
}

