package org.example.util;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Utility methods for working with byte arrays.
 */
public class ByteUtil {

    /**
     * Replaces all occurrences of a byte pattern in the input array with the given replacement.
     *
     * @param input       the source byte array
     * @param pattern     the byte pattern to search for (must not be empty)
     * @param replacement the byte sequence to insert instead
     * @return a new byte array with replacements applied
     */
    public static byte[] replaceAll(byte[] input, byte[] pattern, byte[] replacement) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");

        if (pattern.length == 0) {
            throw new IllegalArgumentException("Pattern must not be empty");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int i = 0;
        while (i < input.length) {
            if (matchesAt(input, i, pattern)) {
                out.write(replacement, 0, replacement.length);
                i += pattern.length;
            } else {
                out.write(input[i]);
                i++;
            }
        }

        return out.toByteArray();
    }

    public static boolean endsWith(byte[] array, byte[] suffix) {
        if (suffix == null || suffix.length == 0 || array.length < suffix.length) return false;

        for (int i = 0; i < suffix.length; i++) {
            if (array[array.length - suffix.length + i] != suffix[i]) return false;
        }

        return true;
    }

    private static boolean matchesAt(byte[] source, int index, byte[] pattern) {
        if (index + pattern.length > source.length) return false;
        for (int j = 0; j < pattern.length; j++) {
            if (source[index + j] != pattern[j]) return false;
        }
        return true;
    }
}
