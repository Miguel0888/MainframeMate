package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTimeoutHandler;
import com.softwareag.naturalone.natural.pal.external.IPalTimeoutResultListener;
import com.softwareag.naturalone.natural.pal.external.PalTools;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Pal class.
 *
 * NOTE: Network-level tests (connect/commit/retrieve) are intentionally excluded
 * because Pal.connect() starts a non-daemon receive thread that blocks in
 * TransferArea.get() and cannot be cleanly terminated without server cooperation.
 * Network tests require a proper mock-server (see PalProtocolTest).
 */
class PalTest {

    // ═══════════════════════════════════════════════════════════════════
    //  §1 Constructor & init
    // ═══════════════════════════════════════════════════════════════════
    @Nested class ConstructorAndInit {
        @Test void constructorPositiveTimeout() {
            Pal pal = new Pal(5, null);
            assertNotNull(pal);
        }

        @Test void constructorZeroTimeout() {
            Pal pal = new Pal(0, null);
            assertNotNull(pal);
        }

        @Test void constructorNegativeTimeout() {
            Pal pal = new Pal(-1, null);
            assertNotNull(pal);
        }

        @Test void constructorWithHandler() {
            IPalTimeoutHandler h = new IPalTimeoutHandler() {
                @Override public boolean continueOperation() { return false; }
                @Override public void addResultListener(IPalTimeoutResultListener l) {}
            };
            assertNotNull(new Pal(1, h));
        }

        @Test void initDoesNotThrow() {
            Pal pal = new Pal(1, null);
            pal.init();
        }

        @Test void initDoesNotResetConnectionLost() {
            Pal pal = new Pal(1, null);
            pal.setConnectionLost(true);
            pal.init();
            // Note: init() does NOT reset connectionLost – that's by design
            assertTrue(pal.isConnectionLost());
        }

        @Test void initCanBeCalledMultipleTimes() {
            Pal pal = new Pal(1, null);
            pal.init();
            pal.init();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §2 Setters & Getters
    // ═══════════════════════════════════════════════════════════════════
    @Nested class SettersGetters {
        private Pal pal;

        @BeforeEach void setup() { pal = new Pal(1, null); pal.init(); }

        @Test void sessionId() { pal.setSessionId("S1"); assertEquals("S1", pal.getSessionId()); }
        @Test void sessionIdEmpty() { assertEquals("", pal.getSessionId()); }
        @Test void userId() { pal.setUserId("U1"); /* no getter, just verify no exception */ }
        @Test void palVersion() { pal.setPalVersion(47); assertEquals(47, pal.getPalVersion()); }
        @Test void palVersionZero() { assertEquals(0, pal.getPalVersion()); }
        @Test void ndvType() { pal.setNdvType(2); assertEquals(2, pal.getNdvType()); }
        @Test void ndvTypeDefault() { assertEquals(0, pal.getNdvType()); }
        @Test void serverCodePage() { pal.setServerCodePage("UTF-8"); }
        @Test void serverCodePageNull() { pal.setServerCodePage(null); }
        @Test void connectionLostDefault() { assertFalse(pal.isConnectionLost()); }
        @Test void connectionLostTrue() { pal.setConnectionLost(true); assertTrue(pal.isConnectionLost()); }
        @Test void connectionLostFalse() { pal.setConnectionLost(true); pal.setConnectionLost(false); assertFalse(pal.isConnectionLost()); }

        @Test void toStringBeforeConnect() {
            String s = pal.toString();
            assertNotNull(s);
            assertTrue(s.startsWith("Pal connection to"));
        }

        @Test void timeoutHandlerSet() {
            IPalTimeoutHandler handler = new IPalTimeoutHandler() {
                @Override public boolean continueOperation() { return false; }
                @Override public void addResultListener(IPalTimeoutResultListener l) {}
            };
            pal.setPalTimeoutHandler(handler);
        }

        @Test void timeoutHandlerNull() {
            pal.setPalTimeoutHandler(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §3 closeSocket/disconnect without connect
    // ═══════════════════════════════════════════════════════════════════
    @Nested class DisconnectWithoutConnect {
        @Test void closeSocketIdempotent() throws Exception {
            Pal pal = new Pal(1, null);
            pal.init();
            pal.closeSocket(); // no socket → no exception
        }

        @Test void disconnectWithoutConnect() throws Exception {
            Pal pal = new Pal(1, null);
            pal.init();
            pal.disconnect(); // no socket → no-op
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §4 connect – invalid port
    // ═══════════════════════════════════════════════════════════════════
    @Nested class ConnectValidation {
        @Test void invalidPortThrows() {
            Pal pal = new Pal(1, null);
            assertThrows(IllegalArgumentException.class, () -> pal.connect("127.0.0.1", "notAPort"));
        }

        @Test void emptyPortThrows() {
            Pal pal = new Pal(1, null);
            assertThrows(Exception.class, () -> pal.connect("127.0.0.1", ""));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §5 PalTools.getIPBytes
    // ═══════════════════════════════════════════════════════════════════
    @Nested class PalToolsTests {
        @Test void validIpv4() {
            byte[] b = PalTools.getIPBytes("192.168.1.1");
            assertNotNull(b);
            assertEquals(4, b.length);
            assertEquals((byte)192, b[0]);
            assertEquals((byte)168, b[1]);
            assertEquals((byte)1, b[2]);
            assertEquals((byte)1, b[3]);
        }

        @Test void loopback() {
            byte[] b = PalTools.getIPBytes("127.0.0.1");
            assertNotNull(b);
            assertEquals(127, b[0] & 0xFF);
        }

        @Test void hostnameReturnsNull() {
            assertNull(PalTools.getIPBytes("example.com"));
        }

        @Test void singleWordReturnsNull() {
            assertNull(PalTools.getIPBytes("localhost"));
        }

        @Test void nonNumericPartsReturnsNull() {
            assertNull(PalTools.getIPBytes("abc.def.ghi.jkl"));
        }

        @Test void emptyString() {
            assertNull(PalTools.getIPBytes(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.0.0.0", "255.255.255.255", "10.0.0.1"})
        void variousIps(String ip) {
            byte[] b = PalTools.getIPBytes(ip);
            assertNotNull(b);
            assertEquals(4, b.length);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §6 Static accessor methods (synthetic)
    // ═══════════════════════════════════════════════════════════════════
    @Nested class StaticAccessors {
        @Test void access2() {
            Pal pal = new Pal(1, null);
            pal.init();
            // intFromBuffer(byte[], int) via synthetic accessor
            byte[] data = "42\0rest".getBytes();
            int result = Pal.access$2(pal, data, 0);
            assertEquals(42, result);
        }

        @Test void access4() {
            Pal pal = new Pal(1, null);
            Pal.access$4(pal, true);
        }

        @Test void access6() {
            Pal pal = new Pal(1, null);
            Pal.access$6(pal, new java.io.IOException("test"));
        }

        @Test void access7() {
            Pal pal = new Pal(1, null);
            Pal.access$7(pal, new com.softwareag.naturalone.natural.pal.external.PalTimeoutException("t", null));
        }

        @Test void access8() {
            Pal pal = new Pal(1, null);
            Pal.access$8(pal, true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §7 PalTools.getInstanceFormat
    // ═══════════════════════════════════════════════════════════════════
    @Nested class PalToolsFormat {
        @Test void instanceFormatNotNull() {
            assertNotNull(PalTools.getInstanceFormat());
        }

        @Test void instanceFormatNotEmpty() {
            assertTrue(PalTools.getInstanceFormat().size() > 0);
        }
    }
}
