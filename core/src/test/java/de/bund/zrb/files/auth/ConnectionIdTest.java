package de.bund.zrb.files.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConnectionIdTest {

    @Test
    void equalityAndToStringAreStable() {
        ConnectionId a = new ConnectionId("ftp", "example", "user");
        ConnectionId b = new ConnectionId("ftp", "example", "user");
        ConnectionId c = new ConnectionId("ftp", "example", "other");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals("ftp://user@example", a.toString());
    }
}

