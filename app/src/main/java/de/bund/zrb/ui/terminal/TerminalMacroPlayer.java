package de.bund.zrb.ui.terminal;

import com.ascert.open.ohio.Ohio;
import com.ascert.open.term.core.Terminal;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays a recorded 3270 macro (list of TEXT / AID steps) against a connected terminal.
 * Runs on a background thread and waits for keyboard unlock between AID steps.
 */
public class TerminalMacroPlayer {

    private static final Logger LOG = Logger.getLogger(TerminalMacroPlayer.class.getName());

    private final Terminal terminal;
    private final List<Map<String, String>> steps;
    private final int actionDelayMs;

    public TerminalMacroPlayer(Terminal terminal, List<Map<String, String>> steps) {
        this(terminal, steps, 1000);
    }

    public TerminalMacroPlayer(Terminal terminal, List<Map<String, String>> steps, int actionDelayMs) {
        this.terminal = terminal;
        this.steps = steps;
        this.actionDelayMs = Math.max(actionDelayMs, 0);
    }

    /**
     * Replay all steps. Must be called from a background thread.
     */
    public void play() throws Exception {
        for (Map<String, String> step : steps) {
            String type = step.get("type");
            String value = step.get("value");
            if (type == null || value == null) continue;

            switch (type) {
                case "TEXT":
                    typeText(value);
                    break;
                case "AID":
                    sendAid(value);
                    break;
                default:
                    LOG.warning("[MacroPlay] Unknown step type: " + type);
            }
        }
        LOG.info("[MacroPlay] Replay complete (" + steps.size() + " steps)");
    }

    private void typeText(String text) throws Exception {
        if (!waitForKeyboardUnlock(5_000)) {
            LOG.warning("[MacroPlay] Keyboard locked, cannot type: '" + text + "'");
            return;
        }
        com.ascert.open.term.core.InputCharHandler charHandler = terminal.getCharHandler();
        for (int i = 0; i < text.length(); i++) {
            charHandler.type(text.charAt(i));
        }
        Thread.sleep(actionDelayMs);
        LOG.fine("[MacroPlay] Typed: '" + text + "'");
    }

    private void sendAid(String aidName) throws Exception {
        if (!waitForKeyboardUnlock(10_000)) {
            LOG.warning("[MacroPlay] Keyboard locked, cannot send AID: " + aidName);
            return;
        }

        Ohio.OHIO_AID aid = resolveAid(aidName);
        if (aid == null) {
            LOG.warning("[MacroPlay] Unknown AID: " + aidName);
            return;
        }

        terminal.Fkey(aid);
        LOG.fine("[MacroPlay] Sent AID: " + aidName);
        // Wait for host to process
        Thread.sleep(actionDelayMs);
    }

    private boolean waitForKeyboardUnlock(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!terminal.isKeyboardLocked()) return true;
            Thread.sleep(100);
        }
        return !terminal.isKeyboardLocked();
    }

    static Ohio.OHIO_AID resolveAid(String name) {
        if (name == null) return null;
        try {
            return Ohio.OHIO_AID.valueOf(name);
        } catch (IllegalArgumentException e) {
            // Try common short names
            switch (name.toUpperCase()) {
                case "ENTER": return Ohio.OHIO_AID.OHIO_AID_3270_ENTER;
                case "CLEAR": return Ohio.OHIO_AID.OHIO_AID_3270_CLEAR;
                case "SYSREQ": return Ohio.OHIO_AID.OHIO_AID_3270_SYSREQ;
                case "PA1": return Ohio.OHIO_AID.OHIO_AID_3270_PA1;
                case "PA2": return Ohio.OHIO_AID.OHIO_AID_3270_PA2;
                case "PA3": return Ohio.OHIO_AID.OHIO_AID_3270_PA3;
                case "PF1":  return Ohio.OHIO_AID.OHIO_AID_3270_PF1;
                case "PF2":  return Ohio.OHIO_AID.OHIO_AID_3270_PF2;
                case "PF3":  return Ohio.OHIO_AID.OHIO_AID_3270_PF3;
                case "PF4":  return Ohio.OHIO_AID.OHIO_AID_3270_PF4;
                case "PF5":  return Ohio.OHIO_AID.OHIO_AID_3270_PF5;
                case "PF6":  return Ohio.OHIO_AID.OHIO_AID_3270_PF6;
                case "PF7":  return Ohio.OHIO_AID.OHIO_AID_3270_PF7;
                case "PF8":  return Ohio.OHIO_AID.OHIO_AID_3270_PF8;
                case "PF9":  return Ohio.OHIO_AID.OHIO_AID_3270_PF9;
                case "PF10": return Ohio.OHIO_AID.OHIO_AID_3270_PF10;
                case "PF11": return Ohio.OHIO_AID.OHIO_AID_3270_PF11;
                case "PF12": return Ohio.OHIO_AID.OHIO_AID_3270_PF12;
                case "PF13": return Ohio.OHIO_AID.OHIO_AID_3270_PF13;
                case "PF14": return Ohio.OHIO_AID.OHIO_AID_3270_PF14;
                case "PF15": return Ohio.OHIO_AID.OHIO_AID_3270_PF15;
                case "PF16": return Ohio.OHIO_AID.OHIO_AID_3270_PF16;
                case "PF17": return Ohio.OHIO_AID.OHIO_AID_3270_PF17;
                case "PF18": return Ohio.OHIO_AID.OHIO_AID_3270_PF18;
                case "PF19": return Ohio.OHIO_AID.OHIO_AID_3270_PF19;
                case "PF20": return Ohio.OHIO_AID.OHIO_AID_3270_PF20;
                case "PF21": return Ohio.OHIO_AID.OHIO_AID_3270_PF21;
                case "PF22": return Ohio.OHIO_AID.OHIO_AID_3270_PF22;
                case "PF23": return Ohio.OHIO_AID.OHIO_AID_3270_PF23;
                case "PF24": return Ohio.OHIO_AID.OHIO_AID_3270_PF24;
                default: return null;
            }
        }
    }
}

