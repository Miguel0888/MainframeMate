package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClockTimerTool.
 */
class ClockTimerToolTest {

    private ClockTimerTool tool;

    @BeforeEach
    void setUp() {
        tool = new ClockTimerTool(null);
        // Stop all timers before each test
        JsonObject stopAllInput = new JsonObject();
        stopAllInput.addProperty("action", "stop_all");
        stopAllInput.addProperty("stateFilter", "ALL");
        tool.execute(stopAllInput, null);
    }

    @AfterEach
    void tearDown() {
        // Clean up all timers
        JsonObject stopAllInput = new JsonObject();
        stopAllInput.addProperty("action", "stop_all");
        stopAllInput.addProperty("stateFilter", "ALL");
        tool.execute(stopAllInput, null);
    }

    @Test
    void getSpec_returnsValidToolSpec() {
        ToolSpec spec = tool.getSpec();

        assertNotNull(spec);
        assertEquals("clock_timer", spec.getName());
        assertNotNull(spec.getDescription());
        assertNotNull(spec.getInputSchema());
        assertTrue(spec.getInputSchema().getRequired().contains("action"));
    }

    @Test
    void execute_withMissingAction_returnsError() {
        JsonObject input = new JsonObject();

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("action"));
    }

    @Test
    void execute_withInvalidAction_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "invalid_action");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("Unbekannte Aktion"));
    }

    // ========== NOW Action Tests ==========

    @Test
    void execute_now_returnsCurrentTime() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "now");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("now", json.get("action").getAsString());
        assertTrue(json.has("epochMillis"));
        assertTrue(json.has("iso"));
        assertTrue(json.has("localTime"));
        assertTrue(json.has("zoneId"));
        assertTrue(json.has("localDate"));
        assertTrue(json.has("dayOfWeek"));

        // Verify epoch is reasonable (within last minute)
        long epochMillis = json.get("epochMillis").getAsLong();
        long now = System.currentTimeMillis();
        assertTrue(Math.abs(now - epochMillis) < 60000);
    }

    @Test
    void execute_now_withTimezone_usesSpecifiedTimezone() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "now");
        input.addProperty("zoneId", "Europe/Berlin");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("Europe/Berlin", json.get("zoneId").getAsString());
    }

    @Test
    void execute_now_withUTC_usesUTC() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "now");
        input.addProperty("zoneId", "UTC");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("UTC", json.get("zoneId").getAsString());
    }

    // ========== START_TIMER Action Tests ==========

    @Test
    void execute_startTimer_withDurationMs_startsTimer() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("durationMs", 60000);
        input.addProperty("label", "Test Timer");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("start_timer", json.get("action").getAsString());
        assertTrue(json.has("timer"));

        JsonObject timer = json.getAsJsonObject("timer");
        assertNotNull(timer.get("timerId").getAsString());
        assertEquals("Test Timer", timer.get("label").getAsString());
        assertEquals("ACTIVE", timer.get("state").getAsString());
        assertEquals(60000, timer.get("durationMs").getAsLong());
    }

    @Test
    void execute_startTimer_withDurationString_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "5m");
        input.addProperty("label", "5 Minutes Timer");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals(300000, timer.get("durationMs").getAsLong()); // 5 minutes = 300000ms
    }

    @Test
    void execute_startTimer_withColonDuration_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "01:30");  // 1 minute 30 seconds

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals(90000, timer.get("durationMs").getAsLong()); // 90 seconds = 90000ms
    }

    @Test
    void execute_startTimer_withHHMMSSDuration_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "01:00:00");  // 1 hour

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals(3600000, timer.get("durationMs").getAsLong()); // 1 hour = 3600000ms
    }

    @Test
    void execute_startTimer_withComplexDuration_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "1h30m");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals(5400000, timer.get("durationMs").getAsLong()); // 1.5 hours = 5400000ms
    }

    @Test
    void execute_startTimer_withoutDuration_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("label", "No Duration Timer");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("durationMs"));
    }

    @Test
    void execute_startTimer_withInvalidDuration_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "invalid");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("Ungültiges Dauer-Format"));
    }

    @Test
    void execute_startTimer_withOptions_setsOptions() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("durationMs", 60000);
        input.addProperty("sound", "NONE");
        input.addProperty("popup", false);
        input.addProperty("chatEvent", false);
        input.addProperty("repeat", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals("NONE", timer.get("sound").getAsString());
        assertFalse(timer.get("popup").getAsBoolean());
        assertFalse(timer.get("chatEvent").getAsBoolean());
        assertTrue(timer.get("repeat").getAsBoolean());
    }

    // ========== LIST_TIMERS Action Tests ==========

    @Test
    void execute_listTimers_returnsActiveTimers() {
        // Start a timer first
        JsonObject startInput = new JsonObject();
        startInput.addProperty("action", "start_timer");
        startInput.addProperty("durationMs", 60000);
        startInput.addProperty("label", "Test Timer");
        tool.execute(startInput, null);

        // List timers
        JsonObject listInput = new JsonObject();
        listInput.addProperty("action", "list_timers");

        McpToolResponse response = tool.execute(listInput, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("list_timers", json.get("action").getAsString());
        assertEquals("ACTIVE", json.get("stateFilter").getAsString());
        assertTrue(json.get("count").getAsInt() >= 1);

        JsonArray timers = json.getAsJsonArray("timers");
        assertTrue(timers.size() >= 1);
    }

    @Test
    void execute_listTimers_emptyList_returnsZeroCount() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "list_timers");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(0, json.get("count").getAsInt());
    }

    // ========== STOP_TIMER Action Tests ==========

    @Test
    void execute_stopTimer_stopsActiveTimer() {
        // Start a timer first
        JsonObject startInput = new JsonObject();
        startInput.addProperty("action", "start_timer");
        startInput.addProperty("durationMs", 60000);
        McpToolResponse startResponse = tool.execute(startInput, null);
        String timerId = startResponse.asJson().getAsJsonObject("timer").get("timerId").getAsString();

        // Stop the timer
        JsonObject stopInput = new JsonObject();
        stopInput.addProperty("action", "stop_timer");
        stopInput.addProperty("timerId", timerId);

        McpToolResponse response = tool.execute(stopInput, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("stop_timer", json.get("action").getAsString());
        assertEquals(timerId, json.get("timerId").getAsString());
        assertEquals("ACTIVE", json.get("previousState").getAsString());
        assertEquals("STOPPED", json.get("newState").getAsString());
    }

    @Test
    void execute_stopTimer_nonExistentTimer_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "stop_timer");
        input.addProperty("timerId", "nonexistent-timer-id");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("nicht gefunden"));
    }

    @Test
    void execute_stopTimer_missingTimerId_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "stop_timer");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("timerId"));
    }

    // ========== GET_TIMER Action Tests ==========

    @Test
    void execute_getTimer_returnsTimerDetails() {
        // Start a timer first
        JsonObject startInput = new JsonObject();
        startInput.addProperty("action", "start_timer");
        startInput.addProperty("durationMs", 60000);
        startInput.addProperty("label", "Detail Timer");
        McpToolResponse startResponse = tool.execute(startInput, null);
        String timerId = startResponse.asJson().getAsJsonObject("timer").get("timerId").getAsString();

        // Get timer details
        JsonObject getInput = new JsonObject();
        getInput.addProperty("action", "get_timer");
        getInput.addProperty("timerId", timerId);

        McpToolResponse response = tool.execute(getInput, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("get_timer", json.get("action").getAsString());
        assertTrue(json.has("timer"));
        assertTrue(json.has("remainingMs"));

        JsonObject timer = json.getAsJsonObject("timer");
        assertEquals("Detail Timer", timer.get("label").getAsString());
    }

    // ========== STOP_ALL Action Tests ==========

    @Test
    void execute_stopAll_stopsAllActiveTimers() {
        // Start multiple timers
        for (int i = 0; i < 3; i++) {
            JsonObject startInput = new JsonObject();
            startInput.addProperty("action", "start_timer");
            startInput.addProperty("durationMs", 60000);
            startInput.addProperty("label", "Timer " + i);
            tool.execute(startInput, null);
        }

        // Stop all
        JsonObject stopAllInput = new JsonObject();
        stopAllInput.addProperty("action", "stop_all");

        McpToolResponse response = tool.execute(stopAllInput, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("stop_all", json.get("action").getAsString());
        assertEquals(3, json.get("stoppedCount").getAsInt());

        // Verify no active timers
        JsonObject listInput = new JsonObject();
        listInput.addProperty("action", "list_timers");
        McpToolResponse listResponse = tool.execute(listInput, null);
        assertEquals(0, listResponse.asJson().get("count").getAsInt());
    }

    // ========== Duration Parsing Tests ==========

    @Test
    void execute_startTimer_withSeconds_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "30s");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(30000, json.getAsJsonObject("timer").get("durationMs").getAsLong());
    }

    @Test
    void execute_startTimer_withHours_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "2h");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(7200000, json.getAsJsonObject("timer").get("durationMs").getAsLong());
    }

    @Test
    void execute_startTimer_withFullDuration_parsesCorrectly() {
        JsonObject input = new JsonObject();
        input.addProperty("action", "start_timer");
        input.addProperty("duration", "2h15m30s");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        // 2h = 7200s, 15m = 900s, 30s = 30s => total = 8130s = 8130000ms
        assertEquals(8130000, json.getAsJsonObject("timer").get("durationMs").getAsLong());
    }

    // ========== Static Utility Methods Tests ==========

    @Test
    void getActiveTimerCount_returnsCorrectCount() {
        // Start a timer
        JsonObject startInput = new JsonObject();
        startInput.addProperty("action", "start_timer");
        startInput.addProperty("durationMs", 60000);
        tool.execute(startInput, null);

        assertTrue(ClockTimerTool.getActiveTimerCount() >= 1);
    }

    @Test
    void getActiveTimers_returnsActiveTimers() {
        // Start a timer
        JsonObject startInput = new JsonObject();
        startInput.addProperty("action", "start_timer");
        startInput.addProperty("durationMs", 60000);
        startInput.addProperty("label", "Active Timer");
        tool.execute(startInput, null);

        java.util.List<ClockTimerTool.TimerEntry> activeTimers = ClockTimerTool.getActiveTimers();
        assertTrue(activeTimers.size() >= 1);
        assertEquals("Active Timer", activeTimers.get(0).getLabel());
    }

    // ========== Timer Firing Tests ==========

    @Test
    void timer_fires_afterDuration() throws InterruptedException {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        AtomicReference<String> callbackLabel = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Set callback
        ClockTimerTool.setEventCallback((timerId, label, message) -> {
            callbackCalled.set(true);
            callbackLabel.set(label);
            latch.countDown();
        });

        try {
            // Start a short timer (500ms) without popup
            JsonObject input = new JsonObject();
            input.addProperty("action", "start_timer");
            input.addProperty("durationMs", 500);
            input.addProperty("label", "Quick Timer");
            input.addProperty("popup", false);  // Don't show popup in test
            input.addProperty("sound", "NONE"); // Don't play sound in test

            McpToolResponse response = tool.execute(input, null);
            assertEquals("success", response.asJson().get("status").getAsString());

            // Wait for timer to fire (with some buffer)
            boolean fired = latch.await(2, TimeUnit.SECONDS);

            assertTrue(fired, "Timer should have fired");
            assertTrue(callbackCalled.get(), "Callback should have been called");
            assertEquals("Quick Timer", callbackLabel.get());

        } finally {
            ClockTimerTool.setEventCallback(null);
        }
    }
}

