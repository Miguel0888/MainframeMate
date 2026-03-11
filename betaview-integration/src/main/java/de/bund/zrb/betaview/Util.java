package de.bund.zrb.betaview;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

public final class Util {

    private Util() {
        // Prevent instantiation
    }

    public static byte[] readResponseBody(HttpURLConnection conn) throws IOException {
        InputStream raw;
        try {
            raw = conn.getInputStream();
        } catch (IOException e) {
            raw = conn.getErrorStream();
        }
        if (raw == null) {
            return new byte[0];
        }

        String encoding = conn.getHeaderField("Content-Encoding");
        InputStream in = raw;
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            in = new GZIPInputStream(raw);
        }

        try (InputStream input = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int n;
            while ((n = input.read(data)) >= 0) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        }
    }
}