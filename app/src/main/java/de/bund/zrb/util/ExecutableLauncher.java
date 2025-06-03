package de.bund.zrb.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ExecutableLauncher {

    private static final String EXECUTABLE_RESOURCE_PATH = "/llama/driver.exe";
    private static final String EXECUTABLE_NAME = "driver.exe";

    // Hardcoded, geprüfter Hashwert (z. B. von der Originaldatei erzeugt)
    private static final String EXPECTED_SHA256 = "0C1C370BD1FD2941FDDCCD65C3796322ACDBD68AD717A361D16EE4924C63EEC3";
    public static String getHash() {
        return EXPECTED_SHA256;
    }

    /**
     * Startet die ausführbare Datei, die im Ressourcenverzeichnis gespeichert ist.
     *
     * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt.
     * @throws NoSuchAlgorithmException Wenn der SHA-256-Algorithmus nicht verfügbar ist.
     */
    public void launch() throws IOException, NoSuchAlgorithmException {
        File executable = extractExecutableToTempDir();
        verifySha256Hash(executable, EXPECTED_SHA256);
        startExecutable(executable);
    }

    /**
     * Extrahiert die ausführbare Datei an den angegebenen Zielpfad und prüft den SHA-256-Hash.
     *
     * @param targetPath Der Pfad, an den die ausführbare Datei extrahiert werden soll.
     * @param expectedSha256 Der erwartete SHA-256-Hash der ausführbaren Datei.
     * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt.
     * @throws NoSuchAlgorithmException Wenn der SHA-256-Algorithmus nicht verfügbar ist.
     */
    public void extractTo(File targetPath, String expectedSha256) throws IOException, NoSuchAlgorithmException {
        if (targetPath.exists()) {
            // Datei existiert – Hash prüfen
            verifySha256Hash(targetPath, expectedSha256);
            return;
        }

        try (InputStream in = getClass().getResourceAsStream(EXECUTABLE_RESOURCE_PATH)) {
            if (in == null) {
                throw new FileNotFoundException("Executable not found in resources: " + EXECUTABLE_RESOURCE_PATH);
            }

            targetPath.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(targetPath)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            if (!targetPath.setExecutable(true)) {
                throw new IOException("Could not set executable flag on: " + targetPath.getAbsolutePath());
            }

            // Nach dem Entpacken den Hash prüfen
            verifySha256Hash(targetPath, expectedSha256);
        }
    }

    /**
     * Extrahiert die ausführbare Datei in ein temporäres Verzeichnis und macht sie ausführbar.
     *
     * @return Die temporäre Datei, die die ausführbare Datei enthält.
     * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt.
     */
    private File extractExecutableToTempDir() throws IOException {
        InputStream resourceStream = getClass().getResourceAsStream(EXECUTABLE_RESOURCE_PATH);
        if (resourceStream == null) {
            throw new FileNotFoundException("Executable not found in resources: " + EXECUTABLE_RESOURCE_PATH);
        }

        File tempFile = new File(System.getProperty("java.io.tmpdir"), EXECUTABLE_NAME);
        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        if (!tempFile.setExecutable(true)) {
            throw new IOException("Could not make the executable file runnable: " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    private void verifySha256Hash(File file, String expectedHash) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] actualHashBytes = digest.digest();
        String actualHash = bytesToHex(actualHashBytes);

        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            throw new SecurityException("Executable hash mismatch. Possible tampering detected.\nExpected: " + expectedHash + "\nActual:   " + actualHash);
        }
    }

    private void startExecutable(File executable) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(executable.getAbsolutePath());
        processBuilder.inheritIO(); // optional
        processBuilder.start();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
