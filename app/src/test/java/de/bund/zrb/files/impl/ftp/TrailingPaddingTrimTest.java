package de.bund.zrb.files.impl.ftp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TrailingPaddingTrimTest {

    @Test
    void trimsOnlyTrailingPadding_notOccurrencesInTheMiddle() {
        byte pad = 0x00;
        byte[] input = new byte[]{'A', pad, 'B', pad, pad};

        byte[] trimmed = TrailingPaddingTrimmer.trim(input, pad);

        assertArrayEquals(new byte[]{'A', pad, 'B'}, trimmed);
    }

    @Test
    void keepsContentUnchanged_whenNoTrailingPadding() {
        byte pad = 0x20;
        byte[] input = new byte[]{'A', pad, 'B'};

        byte[] trimmed = TrailingPaddingTrimmer.trim(input, pad);

        assertArrayEquals(input, trimmed);
    }
}
