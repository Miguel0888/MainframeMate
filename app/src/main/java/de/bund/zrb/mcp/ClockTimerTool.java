package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP Tool for clock/timer functionality.
 * - Get current time
 * - Start/stop timers that run in background
 * - List active timers
 * - Show popup and play sound when timer fires
 *
 * accessType: WRITE (timers cause side effects: popup, sound, background execution)
 */
public class ClockTimerTool implements McpTool {

    private final MainframeContext context;

    // Timer management
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "ClockTimer-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, TimerEntry> timers = new ConcurrentHashMap<>();
    private static final AtomicLong timerCounter = new AtomicLong(0);

    // Chat event callback (optional, set by application)
    private static volatile TimerEventCallback eventCallback;

    // Actions
    public enum Action {
        NOW,
        START_TIMER,
        STOP_TIMER,
        LIST_TIMERS,
        GET_TIMER,
        STOP_ALL
    }

    // Timer states
    public enum TimerState {
        ACTIVE,
        FIRED,
        STOPPED
    }

    // Sound types
    public enum SoundType {
        BEEP,
        CHIME,
        NONE
    }

    // Callback interface for chat events
    @FunctionalInterface
    public interface TimerEventCallback {
        void onTimerFired(String timerId, String label, String message);
    }

    public ClockTimerTool(MainframeContext context) {
        this.context = context;
    }

    /**
     * Set the callback for timer events (e.g., to post messages to chat)
     */
    public static void setEventCallback(TimerEventCallback callback) {
        eventCallback = callback;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("action", new ToolSpec.Property("string",
                "Aktion: now (Uhrzeit), start_timer, stop_timer, list_timers, get_timer, stop_all"));
        properties.put("zoneId", new ToolSpec.Property("string",
                "Zeitzone für 'now', z.B. 'Europe/Berlin' (optional, default: System)"));
        properties.put("durationMs", new ToolSpec.Property("integer",
                "Timer-Dauer in Millisekunden (für start_timer)"));
        properties.put("duration", new ToolSpec.Property("string",
                "Timer-Dauer als String, z.B. '5m', '90s', '1h30m', '00:05:00' (für start_timer)"));
        properties.put("label", new ToolSpec.Property("string",
                "Timer-Beschriftung/Nachricht (für start_timer)"));
        properties.put("sound", new ToolSpec.Property("string",
                "Sound beim Ablauf: BEEP (default), CHIME, NONE"));
        properties.put("repeat", new ToolSpec.Property("boolean",
                "Wiederhol-Timer (default: false)"));
        properties.put("repeatIntervalMs", new ToolSpec.Property("integer",
                "Wiederholungsintervall in ms (default: durationMs)"));
        properties.put("popup", new ToolSpec.Property("boolean",
                "Popup beim Ablauf anzeigen (default: true)"));
        properties.put("chatEvent", new ToolSpec.Property("boolean",
                "Chat-Nachricht beim Ablauf (default: true)"));
        properties.put("timerId", new ToolSpec.Property("string",
                "Timer-ID (für stop_timer, get_timer)"));
        properties.put("silent", new ToolSpec.Property("boolean",
                "Beim Stoppen keinen Sound abbrechen (default: false)"));
        properties.put("stateFilter", new ToolSpec.Property("string",
                "Filter für list_timers: ACTIVE (default), FIRED, STOPPED, ALL"));
        properties.put("limit", new ToolSpec.Property("integer",
                "Max. Anzahl Timer in list_timers (default: 100)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties,
                Collections.singletonList("action"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("action", "start_timer");
        example.put("duration", "5m");
        example.put("label", "Kaffee ☕");

        return new ToolSpec(
                "clock_timer",
                "Uhrzeit und Timer-Funktionen. " +
                "action=now: Gibt aktuelle Uhrzeit zurück. " +
                "action=start_timer: Startet einen Timer (duration='5m' oder durationMs=300000). " +
                "action=stop_timer: Stoppt einen Timer (timerId erforderlich). " +
                "action=list_timers: Listet aktive Timer. " +
                "action=get_timer: Details zu einem Timer. " +
                "action=stop_all: Stoppt alle Timer. " +
                "Timer laufen im Hintergrund und zeigen beim Ablauf ein Popup + Sound.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        try {
            if (input == null || !input.has("action") || input.get("action").isJsonNull()) {
                return errorResponse("Pflichtfeld fehlt: action", "ToolExecutionError", resultVar);
            }

            String actionStr = input.get("action").getAsString().toUpperCase();
            Action action;
            try {
                action = Action.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                return errorResponse("Unbekannte Aktion: " + actionStr, "ToolExecutionError", resultVar);
            }

            switch (action) {
                case NOW:
                    return handleNow(input, resultVar);
                case START_TIMER:
                    return handleStartTimer(input, resultVar);
                case STOP_TIMER:
                    return handleStopTimer(input, resultVar);
                case LIST_TIMERS:
                    return handleListTimers(input, resultVar);
                case GET_TIMER:
                    return handleGetTimer(input, resultVar);
                case STOP_ALL:
                    return handleStopAll(input, resultVar);
                default:
                    return errorResponse("Aktion nicht implementiert: " + action, "ToolExecutionError", resultVar);
            }

        } catch (Exception e) {
            return errorResponse(e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
                    "ToolExecutionError", resultVar);
        }
    }

    // ========== Action Handlers ==========

    private McpToolResponse handleNow(JsonObject input, String resultVar) {
        String zoneIdStr = getOptionalString(input, "zoneId", null);
        ZoneId zoneId = zoneIdStr != null ? ZoneId.of(zoneIdStr) : ZoneId.systemDefault();

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        long epochMillis = now.toInstant().toEpochMilli();

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "now");
        response.addProperty("zoneId", zoneId.getId());
        response.addProperty("epochMillis", epochMillis);
        response.addProperty("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        response.addProperty("localTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        response.addProperty("localDate", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        response.addProperty("dayOfWeek", now.getDayOfWeek().toString());

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleStartTimer(JsonObject input, String resultVar) {
        // Parse duration
        Long durationMs = null;
        if (input.has("durationMs") && !input.get("durationMs").isJsonNull()) {
            durationMs = input.get("durationMs").getAsLong();
        }
        String durationStr = getOptionalString(input, "duration", null);

        if (durationMs == null && durationStr == null) {
            return errorResponse("Entweder 'durationMs' oder 'duration' muss angegeben werden",
                    "ToolExecutionError", resultVar);
        }

        if (durationMs == null) {
            durationMs = parseDuration(durationStr);
            if (durationMs == null) {
                return errorResponse("Ungültiges Dauer-Format: " + durationStr +
                        ". Erlaubt: '5m', '90s', '1h30m', '00:05:00'", "ToolExecutionError", resultVar);
            }
        }

        if (durationMs <= 0) {
            return errorResponse("Dauer muss positiv sein", "ToolExecutionError", resultVar);
        }

        // Parse options
        String label = getOptionalString(input, "label", null);
        SoundType sound = parseSoundType(getOptionalString(input, "sound", "BEEP"));
        boolean repeat = getOptionalBoolean(input, "repeat", false);
        long repeatIntervalMs = getOptionalLong(input, "repeatIntervalMs", durationMs);
        boolean popup = getOptionalBoolean(input, "popup", true);
        boolean chatEvent = getOptionalBoolean(input, "chatEvent", true);

        // Create timer
        String timerId = "t-" + Long.toHexString(System.currentTimeMillis()) +
                "-" + timerCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        long targetTime = now + durationMs;

        TimerEntry entry = new TimerEntry(
                timerId, label, now, targetTime, durationMs,
                TimerState.ACTIVE, repeat, repeatIntervalMs, sound, popup, chatEvent
        );

        // Schedule the timer
        ScheduledFuture<?> future = scheduler.schedule(
                () -> fireTimer(timerId),
                durationMs,
                TimeUnit.MILLISECONDS
        );
        entry.setFuture(future);

        timers.put(timerId, entry);

        // Build response
        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "start_timer");
        response.add("timer", entry.toJson());

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleStopTimer(JsonObject input, String resultVar) {
        String timerId = getOptionalString(input, "timerId", null);
        if (timerId == null || timerId.isEmpty()) {
            return errorResponse("Pflichtfeld fehlt: timerId", "ToolExecutionError", resultVar);
        }

        TimerEntry entry = timers.get(timerId);
        if (entry == null) {
            return errorResponse("Timer nicht gefunden: " + timerId, "ToolExecutionError", resultVar,
                    "Nutze list_timers, um aktive Timer zu sehen.");
        }

        TimerState previousState = entry.getState();

        // Cancel the scheduled task
        ScheduledFuture<?> future = entry.getFuture();
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }

        entry.setState(TimerState.STOPPED);

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "stop_timer");
        response.addProperty("timerId", timerId);
        response.addProperty("previousState", previousState.name());
        response.addProperty("newState", TimerState.STOPPED.name());

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleListTimers(JsonObject input, String resultVar) {
        String filterStr = getOptionalString(input, "stateFilter", "ACTIVE");
        int limit = getOptionalInt(input, "limit", 100);

        TimerState filterState = null;
        if (!"ALL".equalsIgnoreCase(filterStr)) {
            try {
                filterState = TimerState.valueOf(filterStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                filterState = TimerState.ACTIVE;
            }
        }

        List<TimerEntry> filtered = new ArrayList<>();
        for (TimerEntry entry : timers.values()) {
            if (filterState == null || entry.getState() == filterState) {
                filtered.add(entry);
            }
            if (filtered.size() >= limit) {
                break;
            }
        }

        // Sort by target time
        filtered.sort(Comparator.comparingLong(TimerEntry::getTargetEpochMillis));

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "list_timers");
        response.addProperty("stateFilter", filterStr.toUpperCase());
        response.addProperty("count", filtered.size());

        JsonArray timersArray = new JsonArray();
        for (TimerEntry entry : filtered) {
            timersArray.add(entry.toJsonSummary());
        }
        response.add("timers", timersArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleGetTimer(JsonObject input, String resultVar) {
        String timerId = getOptionalString(input, "timerId", null);
        if (timerId == null || timerId.isEmpty()) {
            return errorResponse("Pflichtfeld fehlt: timerId", "ToolExecutionError", resultVar);
        }

        TimerEntry entry = timers.get(timerId);
        if (entry == null) {
            return errorResponse("Timer nicht gefunden: " + timerId, "ToolExecutionError", resultVar,
                    "Nutze list_timers, um aktive Timer zu sehen.");
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "get_timer");
        response.add("timer", entry.toJson());

        // Add remaining time
        if (entry.getState() == TimerState.ACTIVE) {
            long remaining = entry.getTargetEpochMillis() - System.currentTimeMillis();
            response.addProperty("remainingMs", Math.max(0, remaining));
        }

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleStopAll(JsonObject input, String resultVar) {
        String filterStr = getOptionalString(input, "stateFilter", "ACTIVE");
        boolean silent = getOptionalBoolean(input, "silent", true);

        TimerState filterState = null;
        if (!"ALL".equalsIgnoreCase(filterStr)) {
            try {
                filterState = TimerState.valueOf(filterStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                filterState = TimerState.ACTIVE;
            }
        }

        int stoppedCount = 0;
        for (TimerEntry entry : timers.values()) {
            if (filterState == null || entry.getState() == filterState) {
                if (entry.getState() == TimerState.ACTIVE) {
                    ScheduledFuture<?> future = entry.getFuture();
                    if (future != null && !future.isDone()) {
                        future.cancel(false);
                    }
                    entry.setState(TimerState.STOPPED);
                    stoppedCount++;
                }
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("action", "stop_all");
        response.addProperty("stoppedCount", stoppedCount);

        return new McpToolResponse(response, resultVar, null);
    }

    // ========== Timer Firing ==========

    private void fireTimer(String timerId) {
        TimerEntry entry = timers.get(timerId);
        if (entry == null || entry.getState() != TimerState.ACTIVE) {
            return;
        }

        // Update state
        entry.setState(TimerState.FIRED);
        entry.setFiredAtEpochMillis(System.currentTimeMillis());

        // Show popup on EDT
        if (entry.isPopup()) {
            SwingUtilities.invokeLater(() -> showTimerPopup(entry));
        }

        // Play sound
        if (entry.getSound() != SoundType.NONE) {
            playSound(entry.getSound());
        }

        // Send chat event
        if (entry.isChatEvent() && eventCallback != null) {
            String message = formatTimerMessage(entry);
            eventCallback.onTimerFired(timerId, entry.getLabel(), message);
        }

        // Handle repeat
        if (entry.isRepeat()) {
            long interval = entry.getRepeatIntervalMs();
            long newTarget = System.currentTimeMillis() + interval;
            entry.setTargetEpochMillis(newTarget);
            entry.setState(TimerState.ACTIVE);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> fireTimer(timerId),
                    interval,
                    TimeUnit.MILLISECONDS
            );
            entry.setFuture(future);
        }
    }

    private void showTimerPopup(TimerEntry entry) {
        String title = "⏰ Timer abgelaufen";
        String message = entry.getLabel() != null && !entry.getLabel().isEmpty()
                ? entry.getLabel()
                : "Timer " + entry.getTimerId() + " ist abgelaufen!";

        // Show non-blocking dialog
        JOptionPane pane = new JOptionPane(
                message,
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION
        );
        JDialog dialog = pane.createDialog(null, title);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);

        // Auto-close after 30 seconds
        scheduler.schedule(() -> {
            SwingUtilities.invokeLater(() -> {
                if (dialog.isVisible()) {
                    dialog.dispose();
                }
            });
        }, 30, TimeUnit.SECONDS);
    }

    private void playSound(SoundType sound) {
        switch (sound) {
            case BEEP:
                Toolkit.getDefaultToolkit().beep();
                break;
            case CHIME:
                // Try to play a chime sound, fallback to beep
                try {
                    // Multiple beeps as "chime"
                    for (int i = 0; i < 3; i++) {
                        Toolkit.getDefaultToolkit().beep();
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    private String formatTimerMessage(TimerEntry entry) {
        StringBuilder sb = new StringBuilder("⏰ Timer abgelaufen");
        if (entry.getLabel() != null && !entry.getLabel().isEmpty()) {
            sb.append(": ").append(entry.getLabel());
        }
        sb.append(" (").append(entry.getTimerId()).append(")");
        return sb.toString();
    }

    // ========== Duration Parsing ==========

    private Long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return null;
        }

        duration = duration.trim().toLowerCase();

        // Try HH:MM:SS or MM:SS format
        if (duration.contains(":")) {
            return parseColonDuration(duration);
        }

        // Try suffix format: 5m, 90s, 1h30m, etc.
        return parseSuffixDuration(duration);
    }

    private Long parseColonDuration(String duration) {
        String[] parts = duration.split(":");
        try {
            if (parts.length == 2) {
                // MM:SS
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return (minutes * 60L + seconds) * 1000L;
            } else if (parts.length == 3) {
                // HH:MM:SS
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return (hours * 3600L + minutes * 60L + seconds) * 1000L;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private Long parseSuffixDuration(String duration) {
        // Pattern: matches things like "5m", "90s", "1h", "1h30m", "2h15m30s"
        Pattern pattern = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
        Matcher matcher = pattern.matcher(duration);

        if (!matcher.matches()) {
            // Try simple number (assume seconds)
            try {
                return Long.parseLong(duration) * 1000L;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        long totalMs = 0;
        String hours = matcher.group(1);
        String minutes = matcher.group(2);
        String seconds = matcher.group(3);

        if (hours == null && minutes == null && seconds == null) {
            // Try simple number (assume seconds)
            try {
                return Long.parseLong(duration) * 1000L;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (hours != null) {
            totalMs += Long.parseLong(hours) * 3600 * 1000;
        }
        if (minutes != null) {
            totalMs += Long.parseLong(minutes) * 60 * 1000;
        }
        if (seconds != null) {
            totalMs += Long.parseLong(seconds) * 1000;
        }

        return totalMs > 0 ? totalMs : null;
    }

    private SoundType parseSoundType(String sound) {
        if (sound == null || sound.isEmpty()) {
            return SoundType.BEEP;
        }
        try {
            return SoundType.valueOf(sound.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SoundType.BEEP;
        }
    }

    // ========== Helper Methods ==========

    private String getOptionalString(JsonObject input, String key, String defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsString();
        }
        return defaultValue;
    }

    private boolean getOptionalBoolean(JsonObject input, String key, boolean defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    private int getOptionalInt(JsonObject input, String key, int defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsInt();
        }
        return defaultValue;
    }

    private long getOptionalLong(JsonObject input, String key, long defaultValue) {
        if (input.has(key) && !input.get(key).isJsonNull()) {
            return input.get(key).getAsLong();
        }
        return defaultValue;
    }

    private McpToolResponse errorResponse(String message, String errorType, String resultVar) {
        return errorResponse(message, errorType, resultVar, null);
    }

    private McpToolResponse errorResponse(String message, String errorType, String resultVar, String hint) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("toolName", "clock_timer");
        response.addProperty("errorType", errorType);
        response.addProperty("message", message);
        if (hint != null) {
            response.addProperty("hint", hint);
        }
        return new McpToolResponse(response, resultVar, null);
    }

    // ========== Static Utility Methods ==========

    /**
     * Get count of active timers (for UI display)
     */
    public static int getActiveTimerCount() {
        int count = 0;
        for (TimerEntry entry : timers.values()) {
            if (entry.getState() == TimerState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get list of all timers (for UI)
     */
    public static List<TimerEntry> getAllTimers() {
        return new ArrayList<>(timers.values());
    }

    /**
     * Get list of active timers (for UI)
     */
    public static List<TimerEntry> getActiveTimers() {
        List<TimerEntry> active = new ArrayList<>();
        for (TimerEntry entry : timers.values()) {
            if (entry.getState() == TimerState.ACTIVE) {
                active.add(entry);
            }
        }
        active.sort(Comparator.comparingLong(TimerEntry::getTargetEpochMillis));
        return active;
    }

    /**
     * Shutdown the timer scheduler (call on application exit)
     */
    public static void shutdown() {
        scheduler.shutdownNow();
    }

    // ========== Timer Entry Class ==========

    public static class TimerEntry {
        private final String timerId;
        private final String label;
        private final long createdAtEpochMillis;
        private volatile long targetEpochMillis;
        private final long durationMs;
        private volatile TimerState state;
        private final boolean repeat;
        private final long repeatIntervalMs;
        private final SoundType sound;
        private final boolean popup;
        private final boolean chatEvent;
        private volatile ScheduledFuture<?> future;
        private volatile long firedAtEpochMillis;

        public TimerEntry(String timerId, String label, long createdAtEpochMillis,
                          long targetEpochMillis, long durationMs, TimerState state,
                          boolean repeat, long repeatIntervalMs, SoundType sound,
                          boolean popup, boolean chatEvent) {
            this.timerId = timerId;
            this.label = label;
            this.createdAtEpochMillis = createdAtEpochMillis;
            this.targetEpochMillis = targetEpochMillis;
            this.durationMs = durationMs;
            this.state = state;
            this.repeat = repeat;
            this.repeatIntervalMs = repeatIntervalMs;
            this.sound = sound;
            this.popup = popup;
            this.chatEvent = chatEvent;
        }

        // Getters
        public String getTimerId() { return timerId; }
        public String getLabel() { return label; }
        public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
        public long getTargetEpochMillis() { return targetEpochMillis; }
        public long getDurationMs() { return durationMs; }
        public TimerState getState() { return state; }
        public boolean isRepeat() { return repeat; }
        public long getRepeatIntervalMs() { return repeatIntervalMs; }
        public SoundType getSound() { return sound; }
        public boolean isPopup() { return popup; }
        public boolean isChatEvent() { return chatEvent; }
        public ScheduledFuture<?> getFuture() { return future; }
        public long getFiredAtEpochMillis() { return firedAtEpochMillis; }

        // Setters
        public void setTargetEpochMillis(long targetEpochMillis) {
            this.targetEpochMillis = targetEpochMillis;
        }
        public void setState(TimerState state) { this.state = state; }
        public void setFuture(ScheduledFuture<?> future) { this.future = future; }
        public void setFiredAtEpochMillis(long firedAtEpochMillis) {
            this.firedAtEpochMillis = firedAtEpochMillis;
        }

        public long getRemainingMs() {
            if (state != TimerState.ACTIVE) return 0;
            return Math.max(0, targetEpochMillis - System.currentTimeMillis());
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("timerId", timerId);
            if (label != null) obj.addProperty("label", label);
            obj.addProperty("createdAtEpochMillis", createdAtEpochMillis);
            obj.addProperty("targetEpochMillis", targetEpochMillis);
            obj.addProperty("durationMs", durationMs);
            obj.addProperty("state", state.name());
            obj.addProperty("repeat", repeat);
            if (repeat) obj.addProperty("repeatIntervalMs", repeatIntervalMs);
            obj.addProperty("sound", sound.name());
            obj.addProperty("popup", popup);
            obj.addProperty("chatEvent", chatEvent);
            if (firedAtEpochMillis > 0) {
                obj.addProperty("firedAtEpochMillis", firedAtEpochMillis);
            }
            return obj;
        }

        public JsonObject toJsonSummary() {
            JsonObject obj = new JsonObject();
            obj.addProperty("timerId", timerId);
            if (label != null) obj.addProperty("label", label);
            obj.addProperty("state", state.name());
            obj.addProperty("targetEpochMillis", targetEpochMillis);
            if (state == TimerState.ACTIVE) {
                obj.addProperty("remainingMs", getRemainingMs());
            }
            return obj;
        }
    }
}

