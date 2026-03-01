package com.softwareag.naturalone.natural.paltransactions.internal;

import java.util.Calendar;

public final class PassWord {
    private PassWord() {
    }

    public static String encode(String userId, String password, String library, String newPassword) {
        if (userId.length() > 8) {
            throw new IllegalArgumentException("userId too long");
        }
        if (password.length() > 8) {
            throw new IllegalArgumentException("password too long");
        }
        if (library.length() > 8) {
            throw new IllegalArgumentException("library too long");
        }

        // Determine platform
        char platform;
        if (' ' == 32) {
            platform = 'C'; // ASCII
        } else {
            platform = 'E'; // EBCDIC
        }

        // Generate timestamp: HH0mm0ss
        Calendar cal = Calendar.getInstance();
        int hh = cal.get(Calendar.HOUR_OF_DAY);
        int mm = cal.get(Calendar.MINUTE);
        int ss = cal.get(Calendar.SECOND);
        String timeStamp = String.format("%02d0%02d0%02d", hh, mm, ss);

        // Encrypt each field
        byte[] userBytes = zuByteArray(userId, 8);
        byte[] passBytes = zuByteArray(password, 8);
        byte[] libBytes = zuByteArray(library, 8);
        byte[] newPassBytes = zuByteArray(newPassword, 8);

        nscVerschluesseln(userBytes, timeStamp);
        nscVerschluesseln(passBytes, timeStamp);
        nscVerschluesseln(libBytes, timeStamp);
        nscVerschluesseln(newPassBytes, timeStamp);

        // Build result
        char[] result = new char[74];
        result[0] = platform;
        for (int i = 0; i < 8; i++) {
            result[1 + i] = timeStamp.charAt(i);
        }
        String userHex = hexZeichenKonvertieren(userBytes);
        String passHex = hexZeichenKonvertieren(passBytes);
        String libHex = hexZeichenKonvertieren(libBytes);
        for (int i = 0; i < 16; i++) {
            result[9 + i] = userHex.charAt(i);
        }
        for (int i = 0; i < 16; i++) {
            result[25 + i] = passHex.charAt(i);
        }
        for (int i = 0; i < 16; i++) {
            result[41 + i] = libHex.charAt(i);
        }

        if (newPassword == null || newPassword.isEmpty()) {
            result[57] = (char) 0xFF;
            for (int i = 58; i < 74; i++) {
                result[i] = (char) 0;
            }
        } else {
            String newPassHex = hexZeichenKonvertieren(newPassBytes);
            for (int i = 0; i < 16; i++) {
                result[57 + i] = newPassHex.charAt(i);
            }
            result[73] = (char) 0xFF;
        }

        return new String(result).toUpperCase();
    }

    private static byte[] zuByteArray(String s, int length) {
        byte[] result = new byte[length];
        if (s != null) {
            byte[] src = s.getBytes();
            System.arraycopy(src, 0, result, 0, Math.min(src.length, length));
        }
        return result;
    }

    private static void nscVerschluesseln(byte[] data, String timeStamp) {
        // Build key from permuted timestamp bytes
        int[] perm = {3, 7, 1, 5, 0, 4, 2, 6};
        long key = 0;
        for (int i = 0; i < 8; i++) {
            key = key * 10 + (timeStamp.charAt(perm[i]) - '0');
        }

        // BCD arithmetic encryption
        long multiplier = 455470314L;
        long maxInt = 2147483647L;

        long[] bcdKey = ganzzahlZuBcd(key);
        long[] bcdMul = ganzzahlZuBcd(multiplier);
        long[] bcdMax = ganzzahlZuBcd(maxInt);

        for (int i = 0; i < data.length; i++) {
            bcdKey = bcdMultiplizieren(bcdKey, bcdMul);
            long[] bcdQuot = bcdDividieren(bcdKey, bcdMax);
            long[] bcdProd = bcdMultiplizieren(bcdQuot, bcdMax);
            bcdKey = bcdSubtrahieren(bcdKey, bcdProd);

            long keyVal = bcdZuGanzzahl(bcdKey);
            int addByte = (int) (keyVal % 256);
            data[i] = (byte) ((data[i] & 0xFF) + addByte);
        }
    }

    // BCD arithmetic operations using 20-digit packed decimal
    // Each long array holds BCD digits: [0]=sign (0=pos, 1=neg), [1..20]=digits

    private static long[] ganzzahlZuBcd(long value) {
        long[] bcd = new long[21];
        boolean neg = value < 0;
        if (neg) value = -value;
        bcd[0] = neg ? 1 : 0;
        for (int i = 20; i >= 1; i--) {
            bcd[i] = value % 10;
            value /= 10;
        }
        return bcd;
    }

    private static long bcdZuGanzzahl(long[] bcd) {
        long value = 0;
        for (int i = 1; i <= 20; i++) {
            value = value * 10 + bcd[i];
        }
        if (bcd[0] == 1) value = -value;
        return value;
    }

    private static long[] bcdMultiplizieren(long[] a, long[] b) {
        // Multiply two BCD numbers
        long[] result = new long[21];
        int[] digits = new int[41]; // temp space for multiplication

        for (int i = 20; i >= 1; i--) {
            for (int j = 20; j >= 1; j--) {
                int pos = (20 - i) + (20 - j);
                if (pos < 40) {
                    digits[pos] += (int) (a[i] * b[j]);
                }
            }
        }

        // Carry propagation
        for (int i = 0; i < 40; i++) {
            if (digits[i] >= 10) {
                digits[i + 1] += digits[i] / 10;
                digits[i] %= 10;
            }
        }

        // Copy last 20 digits to result
        for (int i = 1; i <= 20; i++) {
            int pos = 20 - i;
            result[i] = pos < 40 ? digits[pos] : 0;
        }

        result[0] = (a[0] != b[0]) ? 1 : 0;
        return result;
    }

    private static long[] bcdDividieren(long[] a, long[] b) {
        // Divide a by b, return quotient
        long aVal = bcdZuGanzzahl(a);
        long bVal = bcdZuGanzzahl(b);
        if (bVal == 0) return ganzzahlZuBcd(0);
        return ganzzahlZuBcd(aVal / bVal);
    }

    private static long[] bcdSubtrahieren(long[] a, long[] b) {
        // Subtract: a - b
        long aVal = bcdZuGanzzahl(a);
        long bVal = bcdZuGanzzahl(b);
        return ganzzahlZuBcd(aVal - bVal);
    }

    private static String hexZeichenKonvertieren(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
