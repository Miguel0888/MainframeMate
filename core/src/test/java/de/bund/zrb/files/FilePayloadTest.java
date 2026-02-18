package de.bund.zrb.files;

import de.bund.zrb.files.model.FilePayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilePayloadTest {

    @Test
    void computesStableHash() {
        byte[] bytes = "abc".getBytes(StandardCharsets.US_ASCII);
        FilePayload payload = FilePayload.fromBytes(bytes, StandardCharsets.US_ASCII, false);

        assertNotNull(payload.getHash());
        assertEquals(payload.getHash(), FilePayload.computeHash(bytes));
    }
}

