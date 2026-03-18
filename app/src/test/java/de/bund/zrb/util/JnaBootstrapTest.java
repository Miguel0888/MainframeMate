package de.bund.zrb.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JnaBootstrap} — verifies that the JNA native library resource
 * can be located on the classpath (prerequisite for extraction at app startup).
 */
class JnaBootstrapTest {

    @Test
    void jnidispatchResourceExistsOnClasspath() {
        // The 64-bit Windows resource must be present in the JNA JAR
        InputStream in = getClass().getResourceAsStream("/com/sun/jna/win32-x86-64/jnidispatch.dll");
        assertNotNull(in, "jnidispatch.dll (win32-x86-64) must be on classpath via JNA JAR");
        try { in.close(); } catch (Exception ignore) {}
    }

    @Test
    void configureDoesNotThrow() {
        // Should be safe to call — sets system properties if on Windows,
        // does nothing on other platforms
        assertDoesNotThrow(JnaBootstrap::configure);
    }

    @Test
    void configureRespectsExistingBootPath() {
        String previous = System.getProperty("jna.boot.library.path");
        try {
            System.setProperty("jna.boot.library.path", "/custom/path");
            JnaBootstrap.configure();
            // Should not overwrite the manually set property
            assertEquals("/custom/path", System.getProperty("jna.boot.library.path"));
        } finally {
            if (previous != null) {
                System.setProperty("jna.boot.library.path", previous);
            } else {
                System.clearProperty("jna.boot.library.path");
            }
        }
    }
}

