package de.bund.zrb.ftp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TrailingPaddingTrimTest {

    @Test
    void trimsOnlyTrailingPadding_notOccurrencesInTheMiddle() throws Exception {
        byte pad = 0x00;
        byte[] input = new byte[]{'A', pad, 'B', pad, pad};

        FtpFileBuffer buffer = new FtpFileBuffer(
                new ByteArrayInputStream(input),
                pad,
                StandardCharsets.ISO_8859_1,
                "/dummy",
                null,
                false,
                null,
                false
        );

        // Expect: only the final trailing 0x00 bytes are removed. The middle 0x00 remains.
        assertArrayEquals(new byte[]{'A', pad, 'B'}, buffer.getRawBytes());
    }

    @Test
    void keepsContentUnchanged_whenNoTrailingPadding() throws Exception {
        byte pad = 0x20; // space
        byte[] input = new byte[]{'A', pad, 'B'};

        FtpFileBuffer buffer = new FtpFileBuffer(
                new ByteArrayInputStream(input),
                pad,
                StandardCharsets.ISO_8859_1,
                "/dummy",
                null,
                false,
                null,
                false
        );

        assertArrayEquals(input, buffer.getRawBytes());
    }
}

