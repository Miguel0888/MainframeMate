package com.softwareag.naturalone.natural.paltransactions.internal;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * NDV password encoding.
 * <p>
 * Encodes user-id, password, library and (optional) new-password into a single
 * hex-encoded token that is sent to the NDV server during connect.
 * The algorithm uses BCD (Binary Coded Decimal) arithmetic to scramble
 * the credential bytes with a time-derived key.
 * </p>
 */
public final class PassWord {

    // --- platform identifiers ---
    private static final int ASCII_HOST = 1;
    private static final int EBCDIC_HOST = 2;

    // --- BCD helpers ---
    private static final int BCD_L_MASK = 15;
    private static final int BCD_U_MASK = 240;
    private static final int BCD_OK = 0;
    private static final int BCD_OVERFLOW = 1305;
    private static final int BCD_DIVISION_BY_ZERO = 1302;
    private static final int BCD_P_MINUS = 13;
    private static final int BCD_P_PLUS = 12;

    private static int host;

    private PassWord() {
    }

    // =================================================================
    //  Public API
    // =================================================================

    /**
     * Encode credentials for the NDV connect handshake.
     *
     * @param userId      user id (max 8 chars)
     * @param password    password (max 8 chars)
     * @param library     library name (max 8 chars)
     * @param newPassword new password (may be empty)
     * @return upper-case hex-encoded token (74 or 57 chars + sentinel)
     */
    public static String encode(String userId, String password, String library, String newPassword) {
        char[] hexUser = new char[17];
        char[] hexPassword = new char[17];
        char[] hexLibrary = new char[17];
        char[] hexNewPw = new char[17];
        char[] result = new char[74];
        byte[] work = new byte[9];
        byte[] userBytes = new byte[9];
        byte[] pwBytes = new byte[9];
        byte[] timeBytes = new byte[9];
        byte[] libBytes = new byte[9];
        byte[] newPwBytes = new byte[9];

        // Current time as scramble key – format "HH0mm0ss"
        String timeStr = (new SimpleDateFormat("HH0mm0ss"))
                .format((new GregorianCalendar()).getTime());

        determinePlatform();

        if (userId.length() > 8) {
            throw new IllegalArgumentException("the user id " + userId + " exceeds 8 bytes");
        }
        if (password.length() > 8) {
            throw new IllegalArgumentException("the password " + password + " exceeds 8 bytes");
        }
        if (library.length() > 8) {
            throw new IllegalArgumentException("the library " + library + " exceeds 8 bytes");
        }

        // Fill result with spaces
        for (int i = 0; i < 74; i++) {
            result[i] = ' ';
        }

        // Initialise byte arrays (first byte 0, rest stays 0)
        userBytes[0] = 0;
        pwBytes[0] = 0;
        libBytes[0] = 0;
        newPwBytes[0] = 0;

        // Copy credential bytes into fixed-length buffers
        byte[] tmp = userId.getBytes();
        System.arraycopy(tmp, 0, userBytes, 0, tmp.length);

        tmp = password.getBytes();
        System.arraycopy(tmp, 0, pwBytes, 0, tmp.length);

        tmp = timeStr.getBytes();
        System.arraycopy(tmp, 0, timeBytes, 0, tmp.length);

        tmp = library.getBytes();
        System.arraycopy(tmp, 0, libBytes, 0, tmp.length);

        tmp = newPassword.getBytes();
        System.arraycopy(tmp, 0, newPwBytes, 0, tmp.length);

        // Scramble user id
        System.arraycopy(userBytes, 0, work, 0, 8);
        nscConvert(work, timeBytes);
        hexUser = convertHexChar(work);

        // Scramble password
        System.arraycopy(pwBytes, 0, work, 0, 8);
        work = nscConvert(work, timeBytes);
        hexPassword = convertHexChar(work);

        // Scramble library
        System.arraycopy(libBytes, 0, work, 0, 8);
        work = nscConvert(work, timeBytes);
        hexLibrary = convertHexChar(work);

        // Scramble new password (if present)
        if (newPwBytes.length > 0) {
            System.arraycopy(newPwBytes, 0, work, 0, 8);
            nscConvert(work, timeBytes);
            hexNewPw = convertHexChar(work);
        }

        // Build result token
        // Position 0: platform indicator
        if (host == ASCII_HOST) {
            result[0] = 'C';
        }
        if (host == EBCDIC_HOST) {
            result[0] = 'E';
        }

        // Position 1..8: time key
        char[] timeChars = new char[9];
        for (int i = 0; i < 8; i++) {
            timeChars[i] = (char) timeBytes[i];
        }
        System.arraycopy(timeChars, 0, result, 1, 8);

        // Position  9..24: user hex
        System.arraycopy(hexUser, 0, result, 9, 16);
        // Position 25..40: password hex
        System.arraycopy(hexPassword, 0, result, 25, 16);
        // Position 41..56: library hex
        System.arraycopy(hexLibrary, 0, result, 41, 16);

        if (newPwBytes.length == 0) {
            result[57] = 255;
        } else {
            System.arraycopy(hexNewPw, 0, result, 57, 16);
            result[73] = 255;
        }

        return (new String(result)).toUpperCase();
    }

    // =================================================================
    //  Platform detection
    // =================================================================

    private static void determinePlatform() {
        byte space = 32;   // ASCII space = 0x20
        host = 0;
        if (space == 32) {
            host = ASCII_HOST;
        }
        if (space == 64) {
            host = EBCDIC_HOST;
        }
    }

    // =================================================================
    //  Core scramble – nscConvert
    // =================================================================

    /**
     * Scrambles 8 credential bytes with a time-derived BCD key.
     * <p>Algorithm:
     * <ol>
     *   <li>Re-order time digits to build a 6-digit integer seed.</li>
     *   <li>For each of the 8 credential bytes:<br>
     *       – Compute {@code seed = (seed * MULTIPLIER) mod MAX_INT}<br>
     *       – Extract the remainder mod 1000<br>
     *       – Add it to the credential byte.</li>
     * </ol>
     */
    private static byte[] nscConvert(byte[] data, byte[] timeKey) {
        byte[] timeCopy = new byte[9];
        byte[] dataCopy = new byte[9];
        byte[] fillBuffer = new byte[]{32, 32, 32, 32, 32, 32, 32, 32, 32};

        char[] seedChars = new char[6];
        int dataLen = 7;    // index of last byte (0..7)
        int bcdLen = 20;    // BCD working length

        long seed;
        long remainder;
        long maxInt = 2147483647L;

        byte[] bcdThousand = new byte[20];
        byte[] bcdMultiplier = new byte[20];
        byte[] bcdSeed = new byte[20];
        byte[] bcdMaxInt = new byte[20];
        byte[] bcdWork1 = new byte[20];
        byte[] bcdWork2 = new byte[20];
        byte[] bcdWork3 = new byte[20];
        byte[] bcdWork4 = new byte[20];

        System.arraycopy(data, 0, dataCopy, 0, dataLen + 1);
        System.arraycopy(timeKey, 0, timeCopy, 0, dataLen + 1);

        // Re-order time digits to form seed
        seedChars[0] = (char) timeCopy[4];
        seedChars[1] = (char) timeCopy[1];
        seedChars[2] = (char) timeCopy[3];
        seedChars[3] = (char) timeCopy[6];
        seedChars[4] = (char) timeCopy[7];
        seedChars[5] = (char) timeCopy[0];

        String seedStr = new String(seedChars);
        seed = (long) Integer.parseInt(seedStr);

        // Initialise BCD constants
        bcdCvd(bcdThousand, bcdLen, 1000L);
        bcdCvd(bcdMultiplier, bcdLen, 455470314L);
        bcdCvd(bcdSeed, bcdLen, seed);
        bcdCvd(bcdMaxInt, bcdLen, maxInt);

        // Scramble each byte
        for (int i = 0; i <= dataLen; i++) {
            // work1 = multiplier * seed
            System.arraycopy(bcdMultiplier, 0, bcdWork1, 0, 20);
            bcdMp(bcdWork1, bcdLen, bcdSeed, bcdLen);

            // work2 = work1 / maxInt  (quotient)
            System.arraycopy(bcdWork1, 0, bcdWork2, 0, 20);
            bcdDp(bcdWork2, bcdLen, bcdMaxInt, bcdLen);

            // work2 = work2 * maxInt  →  floor(work1/maxInt) * maxInt
            bcdMp(bcdWork2, bcdLen, bcdMaxInt, bcdLen);

            // seed = work1 - work2  →  (multiplier * seed) mod maxInt
            System.arraycopy(bcdWork1, 0, bcdSeed, 0, 20);
            bcdSp(bcdSeed, bcdLen, bcdWork2, bcdLen);

            // work3 = seed / 1000  (quotient)
            System.arraycopy(bcdSeed, 0, bcdWork3, 0, 20);
            bcdDp(bcdWork3, bcdLen, bcdThousand, bcdLen);

            // work3 = work3 * 1000
            bcdMp(bcdWork3, bcdLen, bcdThousand, bcdLen);

            // work4 = seed - work3  →  seed mod 1000
            System.arraycopy(bcdSeed, 0, bcdWork4, 0, 20);
            bcdSp(bcdWork4, bcdLen, bcdWork3, bcdLen);

            // Convert BCD remainder back to long
            remainder = bcdCvb(bcdWork4, bcdLen);

            // Add remainder to credential byte
            long val = (long) dataCopy[i];
            val += remainder;
            fillBuffer[i] = (byte) ((int) val);
        }

        // Copy result back
        for (int i = 0; i <= dataLen; i++) {
            data[i] = fillBuffer[i];
        }

        return data;
    }

    // =================================================================
    //  BCD Arithmetic
    // =================================================================

    /**
     * BCD Multiply: var0 = var0 * var2  (packed decimal).
     */
    private static int bcdMp(byte[] var0, int var1, byte[] var2, int var3) {
        boolean overflow = false;
        boolean sign0positive = false;
        boolean sign2positive = false;

        int padA = 1 - var1 % 2;
        int endA = var1 + padA;
        int padB = 1 - var3 % 2;
        int endB = var3 + padB;

        // Count significant digits to detect potential overflow
        int pos;
        for (pos = padA; pos < endA && getBCD(var0, pos) == 0; ++pos) {
        }
        int significantDigits = var1 - pos;

        for (pos = padB; pos < endB && getBCD(var2, pos) == 0; ++pos) {
        }
        significantDigits += var3 - pos;

        if (significantDigits > var1 + 1) {
            overflow = true;
        }

        // Multiply digit-by-digit
        for (int i = padA; i < endA; ++i) {
            int sum = 0;
            int a = i;

            for (int b = endB - 1; a < endA && b >= padB; --b) {
                int digit = getBCD(var0, a);
                digit *= getBCD(var2, b);
                sum += digit;
                ++a;
            }

            int carry = sum / 10;
            var0 = setBCD(var0, i, (long) (sum % 10));

            if (carry > 0) {
                for (int k = i - 1; k >= padA && carry != 0; --k) {
                    sum = getBCD(var0, k) + carry;
                    carry = sum / 10;
                    var0 = setBCD(var0, k, (long) (sum % 10));
                }
                if (carry != 0) {
                    overflow = true;
                }
            }
        }

        if (overflow) {
            return BCD_OVERFLOW;
        }

        // Set result sign
        int zeroA = bcdZero(var0, var1);
        if (zeroA != 0 || isPPositive(getBCD(var0, endA))) {
            sign0positive = true;
        }
        if (bcdZero(var2, var3) != 0 || isPPositive(getBCD(var2, endB))) {
            sign2positive = true;
        }
        if (zeroA == 0 && sign0positive != sign2positive) {
            setPNegative(var0, endA);
        } else {
            setPPositive(var0, endA);
        }

        return BCD_OK;
    }

    /**
     * BCD Divide: var0 = var0 / var2  (quotient, packed decimal).
     */
    private static int bcdDp(byte[] var0, int var1, byte[] var2, int var3) {
        int firstDivisorDigit = 0;
        byte[] quotient = new byte[21];
        boolean sign0positive = false;
        boolean sign2positive = false;

        int padA = 1 - var1 % 2;
        int endA = var1 + padA;
        int padB = 1 - var3 % 2;
        int endB = var3 + padB;

        // Find first non-zero divisor digit
        int pos;
        for (pos = padB; pos < endB; ++pos) {
            firstDivisorDigit = getBCD(var2, pos);
            if (firstDivisorDigit != 0) {
                break;
            }
        }

        int divisorLen = endB - pos;
        if (divisorLen == 0) {
            return BCD_DIVISION_BY_ZERO;
        }

        // Two-digit estimate of divisor
        int twoDigitDivisor = 10 * firstDivisorDigit;
        ++pos;
        if (pos < endB) {
            twoDigitDivisor += getBCD(var2, pos);
        }

        // Long division loop
        for (int i = padA - 1; i < endA - divisorLen; ++i) {
            int est1 = 0;
            if (i >= padA) {
                est1 = 10 * getBCD(var0, i);
            }
            if (i < endA - 1) {
                est1 += getBCD(var0, i + 1);
            }

            int est2 = 10 * est1;
            if (i < endA - 2) {
                est2 += getBCD(var0, i + 2);
            }

            // Estimate quotient digit
            int q = est1 / firstDivisorDigit;
            if (q > 9) {
                q = 9;
            }

            // Correct overestimate
            for (int check = est2 - q * twoDigitDivisor; check < 0; --q) {
                check += twoDigitDivisor;
            }

            // Subtract q * divisor from dividend
            int borrow = 0;
            if (q > 0) {
                int a = i + divisorLen;
                for (int b = endB - 1; a >= padA && a >= i && b >= padB; --b) {
                    int d = 100 + getBCD(var0, a) + borrow;
                    d -= q * getBCD(var2, b);
                    borrow = d / 10 - 10;
                    var0 = setBCD(var0, a, (long) (d % 10));
                    --a;
                }
                while (a >= padA && borrow < 0) {
                    int d = 10 + getBCD(var0, a) + borrow;
                    borrow = d / 10 - 1;
                    var0 = setBCD(var0, a, (long) (d % 10));
                    --a;
                }
            }

            // Correct underestimate (add back)
            if (borrow < 0) {
                --q;
                borrow = 0;
                int a = i + divisorLen;
                for (int b = endB - 1; a >= padA && a >= i && b >= padB; --b) {
                    int d = getBCD(var0, a) + borrow;
                    d += getBCD(var2, b);
                    if (d > 9) {
                        d -= 10;
                        borrow = 1;
                    } else {
                        borrow = 0;
                    }
                    var0 = setBCD(var0, a, (long) d);
                    --a;
                }
                while (a >= padA && borrow != 0) {
                    int d = getBCD(var0, a) + borrow;
                    borrow = d / 10;
                    var0 = setBCD(var0, a, (long) (d % 10));
                    --a;
                }
            }

            quotient = setBCD(quotient, i + 1, (long) q);
        }

        // Copy quotient back into var0 (remainder is now in var0, overwrite with quotient)
        pos = endA - 1;
        for (int j = pos - divisorLen; j >= padA - 1; --j) {
            var0 = setBCD(var0, pos, (long) getBCD(quotient, j + 1));
            --pos;
        }
        while (pos >= padA) {
            var0 = setBCD(var0, pos, 0L);
            --pos;
        }

        // Set result sign
        int zeroA = bcdZero(var0, var1);
        if (zeroA != 0 || isPPositive(getBCD(var0, endA))) {
            sign0positive = true;
        }
        if (bcdZero(var2, var3) != 0 || isPPositive(getBCD(var2, endB))) {
            sign2positive = true;
        }
        if (zeroA == 0 && sign0positive != sign2positive) {
            setPNegative(var0, endA);
        } else {
            setPPositive(var0, endA);
        }

        return BCD_OK;
    }

    /**
     * BCD Subtract: var0 = var0 - var2  (packed decimal).
     */
    private static int bcdSp(byte[] var0, int var1, byte[] var2, int var3) {
        byte carry = 0;
        int maxLen = var1 < var3 ? var3 : var1;
        int padA = 1 - var1 % 2;
        int posA = var1 + padA;
        int padB = 1 - var3 % 2;
        int posB = var3 + padB;
        boolean overflowDetected = false;

        if (isPNegative(getBCD(var0, posA)) != isPNegative(getBCD(var2, posB))) {
            // Different signs → actually add magnitudes
            carry = 0;

            while (true) {
                --maxLen;
                if (maxLen < 0) {
                    // Propagate carry
                    int d;
                    for (; posA > padA && carry != 0; var0 = setBCD(var0, posA, (long) d)) {
                        --posA;
                        d = getBCD(var0, posA) + carry;
                        if (d > 9) {
                            d -= 10;
                            carry = 1;
                        } else {
                            carry = 0;
                        }
                    }

                    // Check if var2 still has non-zero digits
                    while (posB > padB) {
                        --posB;
                        if (getBCD(var2, posB) != 0) {
                            overflowDetected = true;
                            break;
                        }
                    }
                    break;
                }

                --posA;
                --posB;
                int d = getBCD(var0, posA) + carry;
                d += getBCD(var2, posB);
                if (d > 9) {
                    d -= 10;
                    carry = 1;
                } else {
                    carry = 0;
                }
                var0 = setBCD(var0, posA, (long) d);
            }
        } else {
            // Same signs → subtract magnitudes
            byte borrow = 0;

            while (true) {
                --maxLen;
                if (maxLen < 0) {
                    // Propagate borrow
                    int d;
                    for (; posA > padA && borrow != 0; var0 = setBCD(var0, posA, (long) d)) {
                        --posA;
                        d = getBCD(var0, posA) + borrow;
                        if (d < 0) {
                            d += 10;
                            borrow = 1;
                        } else {
                            borrow = 0;
                        }
                    }

                    // If still borrowing, negate result and flip sign
                    if (borrow != 0) {
                        borrow = 0;
                        padA = 1 - var1 % 2;
                        posA = var1 + padA;
                        if (isPPositive(getBCD(var0, posA))) {
                            var0 = setPNegative(var0, posA);
                        } else {
                            var0 = setPPositive(var0, posA);
                        }

                        int digit;
                        for (; posA > padA; var0 = setBCD(var0, posA, (long) digit)) {
                            --posA;
                            digit = -getBCD(var0, posA) - borrow;
                            if (digit < 0) {
                                digit += 10;
                                borrow = 1;
                            } else {
                                borrow = 0;
                            }
                        }
                    }

                    // Check if var2 still has non-zero digits
                    while (posB > padB) {
                        --posB;
                        if (getBCD(var2, posB) != 0) {
                            overflowDetected = true;
                            break;
                        }
                    }
                    break;
                }

                --posA;
                --posB;
                int d = getBCD(var0, posA) - borrow;
                d -= getBCD(var2, posB);
                if (d < 0) {
                    d += 10;
                    borrow = 1;
                } else {
                    borrow = 0;
                }
                var0 = setBCD(var0, posA, (long) d);
            }
        }

        if (carry != 0) {
            overflowDetected = true;
        }

        if (bcdZero(var0, var1) != 0) {
            setPPositive(var0, var1 + padA);
        }

        return overflowDetected ? BCD_OVERFLOW : BCD_OK;
    }

    /**
     * Convert long to BCD (packed decimal).
     */
    private static int bcdCvd(byte[] target, int length, long value) {
        int pad = 1 - length % 2;
        int end = length + pad;
        long v = value;
        target = setPPositive(target, end);

        while (end > pad) {
            --end;
            target = setBCD(target, end, v % 10L);
            v /= 10L;
            if (v == 0L) {
                break;
            }
        }

        while (end > 0) {
            --end;
            target = setBCD(target, end, 0L);
        }

        if (v != 0L) {
            return BCD_OVERFLOW;
        }
        return BCD_OK;
    }

    /**
     * Convert BCD (packed decimal) to long.
     */
    private static long bcdCvb(byte[] source, int length) {
        int pad = 1 - length % 2;
        int end = length + pad;

        long result = 0L;
        for (int i = pad; i < end; ++i) {
            result *= 10L;
            result += (long) getBCD(source, i);
            if (result < 0L) {
                return BCD_OVERFLOW;
            }
        }

        if (isPNegative(getBCD(source, end))) {
            result = -result;
        }

        return result;
    }

    // =================================================================
    //  BCD nibble access
    // =================================================================

    private static int getBCD(byte[] data, int pos) {
        return ((pos % 2 != 0) ? data[pos >> 1] : (data[pos >> 1] >> 4)) & BCD_L_MASK;
    }

    private static byte[] setBCD(byte[] data, int pos, long value) {
        if (pos % 2 != 0) {
            data[pos >> 1] = (byte) ((int) ((long) (data[pos >> 1] & BCD_U_MASK) | value & 15L));
        } else {
            data[pos >> 1] = (byte) ((int) (value << 4 & 240L | (long) (data[pos >> 1] & BCD_L_MASK)));
        }
        return data;
    }

    // =================================================================
    //  BCD sign helpers
    // =================================================================

    private static boolean isPPositive(int nibble) {
        return !isPNegative(nibble);
    }

    private static boolean isPNegative(int nibble) {
        return (nibble & BCD_L_MASK) == BCD_P_MINUS || (nibble & BCD_L_MASK) == 11;
    }

    private static byte[] setPPositive(byte[] data, int pos) {
        data[pos >> 1] = (byte) (BCD_P_PLUS | data[pos >> 1] & BCD_U_MASK);
        return data;
    }

    private static byte[] setPNegative(byte[] data, int pos) {
        data[pos >> 1] = (byte) (BCD_P_MINUS | data[pos >> 1] & BCD_U_MASK);
        return data;
    }

    /**
     * Check if a BCD number is zero (all digits zero).
     * Returns 1 if zero, 0 otherwise.
     */
    private static int bcdZero(byte[] data, int length) {
        int idx = 0;
        if (length <= 0) {
            return 0;
        }
        for (int count = length / 2; count > 0; --count) {
            if (data[idx++] != 0) {
                return 0;
            }
        }
        return (data[0] & BCD_U_MASK) != 0 ? 0 : 1;
    }

    // =================================================================
    //  Hex conversion
    // =================================================================

    /**
     * Convert 8 bytes to 16 hex characters.
     */
    private static char[] convertHexChar(byte[] data) {
        int len = 8;
        char[] result = new char[17];
        int pos = 0;

        for (int i = 0; i < len; ++i) {
            int val = data[i] & 255;
            byte[] hexBytes = Integer.toHexString(val).getBytes();
            if (hexBytes.length == 1) {
                result[pos++] = '0';
                result[pos++] = (char) hexBytes[0];
            } else {
                result[pos++] = (char) hexBytes[0];
                result[pos++] = (char) hexBytes[1];
            }
        }

        return result;
    }
}

