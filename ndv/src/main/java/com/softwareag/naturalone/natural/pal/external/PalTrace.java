package com.softwareag.naturalone.natural.pal.external;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class PalTrace {
    private static FileWriter protokollDatei;

    private PalTrace() {
    }

    public static synchronized void open(boolean append) throws IOException {
        String palTrace = System.getProperty("PALTRACE");
        if (!"ON".equals(palTrace)) {
            return;
        }
        if (protokollDatei != null) {
            throw new IllegalStateException("Trace file is already open");
        }
        String fileName = System.getProperty("PALTRACEFILE", "NatPal.trc");
        String tmpDir = System.getProperty("java.io.tmpdir");
        String separator = System.getProperty("file.separator");
        String path = tmpDir + separator + fileName;
        protokollDatei = new FileWriter(path, append);
    }

    public static synchronized void close() throws IOException {
        if (protokollDatei != null) {
            protokollDatei.close();
            protokollDatei = null;
        }
    }

    public static synchronized void flush() throws IOException {
        if (protokollDatei != null) {
            protokollDatei.flush();
        }
    }

    public static synchronized void header(String transactionName) throws IOException {
        if (protokollDatei == null) {
            return;
        }
        protokollDatei.write("[" + Thread.currentThread().getId() + "] ------ Transaction '" + transactionName + "' ------\r\n");
    }

    public static synchronized void buffer(byte[] data, boolean received, String sessionId) throws IOException {
        if (protokollDatei == null) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss:SSS");
        protokollDatei.write(sdf.format(new Date()) + " ");
        if (received) {
            protokollDatei.write("<<===== Pal data from server <<=======\r\n");
        } else {
            protokollDatei.write("=====>> Pal data to server ======>>\r\n");
        }
        for (int i = 0; i < data.length; i++) {
            if (i % 30 == 0) {
                if (i > 0) {
                    protokollDatei.write("\r\n");
                }
                protokollDatei.write(String.format("%04d ", i));
            }
            protokollDatei.write(String.format("%02X ", data[i] & 0xFF));
        }
        if (data.length > 0) {
            protokollDatei.write("\r\n");
        }
    }

    public static synchronized void text(String text) throws IOException {
        if (protokollDatei == null) {
            return;
        }
        protokollDatei.write(text);
    }

    public static synchronized void type(String typeName, boolean received) throws IOException {
        if (protokollDatei == null) {
            return;
        }
        if (received) {
            protokollDatei.write("<<<<< " + typeName + "\r\n");
        } else {
            protokollDatei.write(">>>> " + typeName + "\r\n");
        }
    }
}
