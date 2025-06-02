package de.bund.zrb.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ExecutableLauncher {

    private static final String EXECUTABLE_RESOURCE_PATH = "/ai/driver.exe";
    private static final String EXECUTABLE_NAME = "driver.exe";

    // Hardcoded, geprüfter Hashwert (z. B. von der Originaldatei erzeugt)
    private static final String EXPECTED_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // ToDo: Replace with actual hash

    public void launch() throws IOException, NoSuchAlgorithmException {
        File executable = extractExecutableToTempDir();
        verifySha256Hash(executable, EXPECTED_SHA256);
        startExecutable(executable);
    }

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
