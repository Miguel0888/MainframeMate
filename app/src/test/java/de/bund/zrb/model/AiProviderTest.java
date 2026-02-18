package de.bund.zrb.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderTest {

    @Test
    void containsCloudProvider() {
        assertTrue(Arrays.asList(AiProvider.values()).contains(AiProvider.CLOUD));
    }
}
