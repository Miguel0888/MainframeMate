package de.bund.zrb.files.codec;

import de.bund.zrb.model.Settings;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RecordStructureCodec - MVS record marker transformation.
 *
 * Note: Padding removal happens during FTP download (CommonsNetFtpFileService.readAllBytes),
 * not in this codec. These tests assume padding has already been removed.
 */
class RecordStructureCodecTest {

    private static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    private Settings createDefaultSettings() {
        Settings settings = new Settings();
        settings.lineEnding = "FF01";        // Record marker
        settings.fileEndMarker = "FF02";     // EOF marker
        settings.removeFinalNewline = true;
        settings.padding = "00";             // Padding (removed during FTP read)
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

        // "LINE1<FF01>LINE2<FF01>LINE3<FF01>" (padding already removed)
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
        // removeFinalNewline=true (default) is required for exact roundtrip:
        // encode adds FF01 after each line, decode replaces FF01→\n, then removes final \n

        String originalText = "LINE1\nLINE2\nLINE3";

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
    void removeFinalNewlineChecksRaw0x0A() {
        Settings settings = createDefaultSettings();
        settings.removeFinalNewline = true;

        // "LINE1<FF01>" ends with 0x0A after marker replacement
        byte[] remote = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // Final 0x0A should be removed
        assertEquals("LINE1", result);
    }

    // ========================================================================
    // Tests for trailing whitespace/padding stripping (MVS FB record padding fix)
    // ========================================================================

    @Test
    void decodeStripsTrailingSpacePaddingFromEachLine() {
        Settings settings = createDefaultSettings();

        // Simulate MVS FB LRECL=10: each record padded with spaces to 10 chars
        // "HELLO     <FF01>WORLD     <FF01><FF02>"
        byte[] remote = new byte[] {
            'H', 'E', 'L', 'L', 'O', ' ', ' ', ' ', ' ', ' ', (byte)0xFF, 0x01,
            'W', 'O', 'R', 'L', 'D', ' ', ' ', ' ', ' ', ' ', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // Trailing spaces must be stripped from each line
        assertEquals("HELLO\nWORLD", result);
    }

    @Test
    void decodeStripsTrailingCRFromLines() {
        Settings settings = createDefaultSettings();

        // Records with \r before record marker (CRLF artifact)
        // "LINE1\r<FF01>LINE2\r<FF01><FF02>"
        byte[] remote = new byte[] {
            'L', 'I', 'N', 'E', '1', 0x0D, (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', 0x0D, (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // Trailing \r must be stripped
        assertEquals("LINE1\nLINE2", result);
    }

    @Test
    void decodeStripsTrailingNullsFromLines() {
        Settings settings = createDefaultSettings();

        // Records with residual null bytes (in case readAllBytes didn't catch all)
        byte[] remote = new byte[] {
            'A', 'B', 'C', 0x00, 0x00, (byte)0xFF, 0x01,
            'D', 'E', 'F', 0x00, (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // Trailing nulls stripped
        assertEquals("ABC\nDEF", result);
    }

    @Test
    void decodeStripsMixedTrailingPadding() {
        Settings settings = createDefaultSettings();

        // Record with mixed trailing padding: spaces + \r + null
        byte[] remote = new byte[] {
            'T', 'E', 'S', 'T', ' ', ' ', 0x0D, 0x00, (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        assertEquals("TEST", result);
    }

    @Test
    void decodePreservesInternalSpaces() {
        Settings settings = createDefaultSettings();

        // "HELLO WORLD     <FF01><FF02>" — internal space must be preserved
        byte[] remote = new byte[] {
            'H', 'E', 'L', 'L', 'O', ' ', 'W', 'O', 'R', 'L', 'D', ' ', ' ', ' ', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        String result = RecordStructureCodec.decodeForEditor(remote, ISO_8859_1, settings);

        // Only trailing spaces stripped; internal space between HELLO and WORLD preserved
        assertEquals("HELLO WORLD", result);
    }

    @Test
    void encodeStripsTrailingSpacesBeforeMarker() {
        Settings settings = createDefaultSettings();

        // Simulate user text with accidental trailing spaces
        String editorText = "LINE1   \nLINE2  ";

        byte[] result = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        // Expected: "LINE1<FF01>LINE2<FF01><FF02>" — trailing spaces stripped
        byte[] expected = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        assertArrayEquals(expected, result);
    }

    @Test
    void encodeStripsTrailingCRBeforeMarker() {
        Settings settings = createDefaultSettings();

        // Text with stray \r characters
        String editorText = "LINE1\r\nLINE2\r";

        byte[] result = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        // Expected: "LINE1<FF01>LINE2<FF01><FF02>" — \r stripped from each line
        byte[] expected = new byte[] {
            'L', 'I', 'N', 'E', '1', (byte)0xFF, 0x01,
            'L', 'I', 'N', 'E', '2', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        assertArrayEquals(expected, result);
    }

    @Test
    void roundtripWithSpacePaddedRecords() {
        Settings settings = createDefaultSettings();

        // Simulate MVS FB LRECL=10: records padded with spaces
        byte[] originalRemote = new byte[] {
            'H', 'E', 'L', 'L', 'O', ' ', ' ', ' ', ' ', ' ', (byte)0xFF, 0x01,
            'W', 'O', 'R', 'L', 'D', ' ', ' ', ' ', ' ', ' ', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };

        // Decode strips trailing spaces
        String editorText = RecordStructureCodec.decodeForEditor(originalRemote, ISO_8859_1, settings);
        assertEquals("HELLO\nWORLD", editorText);

        // User adds a character to first line
        editorText = "HELLOX\nWORLD";

        // Encode back — records are shorter than original (no padding, just content)
        byte[] encoded = RecordStructureCodec.encodeForRemote(editorText, ISO_8859_1, settings);

        byte[] expected = new byte[] {
            'H', 'E', 'L', 'L', 'O', 'X', (byte)0xFF, 0x01,
            'W', 'O', 'R', 'L', 'D', (byte)0xFF, 0x01,
            (byte)0xFF, 0x02
        };
        assertArrayEquals(expected, encoded);
    }

    @Test
    void rtrimHelperTests() {
        assertEquals("HELLO", RecordStructureCodec.rtrim("HELLO   "));
        assertEquals("HELLO", RecordStructureCodec.rtrim("HELLO\r"));
        assertEquals("HELLO", RecordStructureCodec.rtrim("HELLO\r  "));
        assertEquals("HELLO", RecordStructureCodec.rtrim("HELLO\0\0"));
        assertEquals("", RecordStructureCodec.rtrim("   "));
        assertEquals("", RecordStructureCodec.rtrim(""));
        assertNull(RecordStructureCodec.rtrim(null));
        assertEquals("HELLO WORLD", RecordStructureCodec.rtrim("HELLO WORLD  "));
    }
}

