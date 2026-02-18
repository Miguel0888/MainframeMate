package de.bund.zrb.files.impl.ftp;

final class TrailingPaddingTrimmer {

    private TrailingPaddingTrimmer() {
    }

    static byte[] trim(byte[] bytes, Byte padding) {
        if (bytes == null || bytes.length == 0 || padding == null) {
            return bytes;
        }

        int end = bytes.length;
        byte pad = padding;
        while (end > 0 && bytes[end - 1] == pad) {
            end--;
        }

        if (end == bytes.length) {
            return bytes;
        }

        byte[] trimmed = new byte[end];
        System.arraycopy(bytes, 0, trimmed, 0, end);
        return trimmed;
    }
}
