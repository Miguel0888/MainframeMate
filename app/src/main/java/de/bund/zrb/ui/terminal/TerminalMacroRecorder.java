package de.bund.zrb.ui.terminal;

import com.ascert.open.ohio.Ohio;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Records user interactions with a 3270 terminal as a replayable macro.
 * <p>
 * Two step types are recorded:
 * <ul>
 *   <li>{@code TEXT} – one or more characters typed into a field</li>
 *   <li>{@code AID}  – an AID key press (ENTER, PF1–PF24, PA1–PA3, CLEAR, SYSREQ)</li>
 * </ul>
 * Text input is coalesced: consecutive characters are merged into a single TEXT step.
 * An AID key always flushes the pending text buffer first.
 */
public class TerminalMacroRecorder {

    private static final Logger LOG = Logger.getLogger(TerminalMacroRecorder.class.getName());
    private static final Gson GSON = new Gson();

    private final List<Map<String, String>> steps = new ArrayList<>();
    private final StringBuilder textBuffer = new StringBuilder();
    private boolean recording = true;

    // ── Recording API ───────────────────────────────────────────

    /** Record a single typed character. */
    public synchronized void recordChar(char c) {
        if (!recording) return;
        textBuffer.append(c);
    }

    /** Record a typed string (e.g. pasted text). */
    public synchronized void recordText(String text) {
        if (!recording || text == null) return;
        textBuffer.append(text);
    }

    /** Record an AID key (ENTER, PFx, PAx, CLEAR, SYSREQ). Flushes pending text first. */
    public synchronized void recordAid(Ohio.OHIO_AID aid) {
        if (!recording || aid == null) return;
        flushText();
        Map<String, String> step = new LinkedHashMap<>();
        step.put("type", "AID");
        step.put("value", aid.name());
        steps.add(step);
        LOG.fine("[MacroRec] AID: " + aid.name());
    }

    /** Record an AID key by name string. */
    public synchronized void recordAid(String aidName) {
        if (!recording || aidName == null) return;
        flushText();
        Map<String, String> step = new LinkedHashMap<>();
        step.put("type", "AID");
        step.put("value", aidName);
        steps.add(step);
        LOG.fine("[MacroRec] AID: " + aidName);
    }

    /** Stop recording (further calls are ignored). */
    public synchronized void stop() {
        flushText();
        recording = false;
    }

    /** Check if recording is active. */
    public synchronized boolean isRecording() {
        return recording;
    }

    // ── Snapshot / Serialization ────────────────────────────────

    /** Get a snapshot of all recorded steps (flushing pending text). */
    public synchronized List<Map<String, String>> getSteps() {
        flushText();
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /** Get the number of recorded steps. */
    public synchronized int size() {
        return steps.size() + (textBuffer.length() > 0 ? 1 : 0);
    }

    /** Serialize all recorded steps to a JSON string. */
    public synchronized String toJson() {
        flushText();
        return GSON.toJson(steps);
    }

    /** Deserialize steps from a JSON string. */
    public static List<Map<String, String>> fromJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        return GSON.fromJson(json, new TypeToken<List<Map<String, String>>>() {}.getType());
    }

    // ── Internal ────────────────────────────────────────────────

    private void flushText() {
        if (textBuffer.length() > 0) {
            Map<String, String> step = new LinkedHashMap<>();
            step.put("type", "TEXT");
            step.put("value", textBuffer.toString());
            steps.add(step);
            LOG.fine("[MacroRec] TEXT: '" + textBuffer + "'");
            textBuffer.setLength(0);
        }
    }
}

