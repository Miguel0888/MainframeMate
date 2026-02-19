package de.bund.zrb.files.codec;

import de.bund.zrb.model.Settings;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RecordStructureCodec - MVS record marker transformation.
 */
class RecordStructureCodecTest {

    private static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    private Settings createDefaultSettings() {
        Settings settings = new Settings();
        settings.lineEnding = "FF01";        // Record marker
        settings.fileEndMarker = "FF02";     // EOF marker
        settings.removeFinalNewline = true;
        return settings;
    }

    @Test
    void parseHexBasicCases() {
        assertArrayEquals(new byte[]{(byte) 0xFF, 0x01}, RecordStructureCodec.parseHex("FF01"));
        assertArrayEquals(new byte[]{(byte) 0xFF, 0x02}, RecordStructureCodec.parseHex("FF02"));
        assertArrayEquals(new byte[]{0x00}, RecordStructureCodec.parseHex("00"));
        assertNull(RecordStructureCodec.parseHex(null));
        assertNull(RecordStructureCodec.parseHex(""));
        assertNull(RecordStructureCodec.parseHex("   "));
    }

    @Test
    void decodeReplacesRecordMarkerWithNewline() {
        Settings settings = createDefaultSettings();

        // "LINE1<FF01>LINE2<FF01>LINE3<FF01>"
        byte[] remote = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '3', (byte)0xFF, 0x01
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // removeFinalNewline=true removes the last \n
        assertEquals("LINE1\nLINE2\nLINE3", result);
    }

    @Test
    void decodeRemovesEofMarker() {
        Settings settings = createDefaultSettings();

        // "LINE1<FF01><FF02>"
        byte[] remote = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // EOF marker removed, final newline removed
        assertEquals("LINE1", result);
    }

    @Test
    void encodeAddsRecordMarkerAfterEachLine() {
        Settings settings = createDefaultSettings();

        String editorText = "LINE1\nLINE2\nLINE3";

        byte[] result = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        // Expected: "LINE1<FF01>LINE2<FF01>LINE3<FF01><FF02>"
        byte[] expected = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '3', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        assertArrayEquals(expected, result);
    }

    @Test
    void roundtripPreservesContent() {
        Settings settings = createDefaultSettings();
        settings.removeFinalNewline = false; // For exact roundtrip

        String originalText = "LINE1\nLINE2\nLINE3\n";

        // Editor -> Remote
        byte[] remote = RecordStructureCodec.encodeForRemote(originalText, ISO_8859_1, settings);

        // Remote -> Editor
        String decoded = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        assertEquals(originalText, decoded);
    }

    @Test
    void emptyInputHandling() {
        Settings settings = createDefaultSettings();

        assertEquals("", RecordStructureCodec.decodeForEditor(null, ISO_8859_1, settings));
        assertEquals("", RecordStructureCodec.decodeForEditor(new byte[0], ISO_8859_1, settings));

        byte[] encoded = RecordStructureCodec.encodeForRemote("", ISO_8859_1, settings);
        // Empty string still gets record marker and EOF
        assertNotNull(encoded);
    }

    @Test
    void noSettingsReturnsRawString() {
        byte[] input = "Hello\nWorld".getBytes(ISO_8859_1);

        String result = RecordStructureCodec.decodeForEditor(input, ISO_8859_1, null);

        assertEquals("Hello\nWorld", result);
    }

    @Test
    void decodeWithoutEofMarkerConfig() {
        Settings settings = createDefaultSettings();
        settings.fileEndMarker = ""; // Disable EOF marker

        // "LINE1<FF01>LINE2<FF01>"
        byte[] remote = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // No EOF to remove, just record markers replaced and final newline removed
        assertEquals("LINE1\nLINE2", result);
    }

    @Test
    void encodeWithoutEofMarkerConfig() {
        Settings settings = createDefaultSettings();
        settings.fileEndMarker = ""; // Disable EOF marker

        String editorText = "LINE1\nLINE2";

        byte[] result = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        // Expected: "LINE1<FF01>LINE2<FF01>" (no EOF)
        byte[] expected = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01
        };

        assertArrayEquals(expected, result);
    }

    @Test
    void hashStabilityWithRoundtrip() {
        Settings settings = createDefaultSettings();
        settings.removeFinalNewline = false;

        // Original remote bytes
        byte[] originalRemote = new byte[] {
            'A', 'B', 'C', (byte)0xFF, 0x01,
            'D', 'E', 'F', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        // Decode -> Encode
        String editorText = RecordStructureCodec.decodeForEditor(originalRemote, ISO_8859_1, settings);
        byte[] encodedBack = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        // Hash should be identical
        String hash1 = de.bund.zrb.files.model.FilePayload.computeHash(originalRemote);
        String hash2 = de.bund.zrb.files.model.FilePayload.computeHash(encodedBack);

        assertEquals(hash1, hash2, "Hash should be identical after roundtrip");
    }
}

