package de.bund.zrb.ui.mail;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mail.service.MailService;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Bridges {@link MailService} new-mail events to the {@link MailMarqueePanel}
 * in the menu bar.  Reads colour preferences from {@link Settings}.
 * <p>
 * Typical lifecycle:
 * <pre>
 *   MailMarqueePanel marquee = new MailMarqueePanel();
 *   MailNotificationBridge bridge = new MailNotificationBridge(marquee);
 *   bridge.install();   // registers listener
 *   // …
 *   bridge.uninstall(); // removes listener
 * </pre>
 */
public class MailNotificationBridge {

    private static final Logger LOG = Logger.getLogger(MailNotificationBridge.class.getName());

    private final MailMarqueePanel marquee;
    private MailService.NewMailListener listener;

    public MailNotificationBridge(MailMarqueePanel marquee) {
        this.marquee = marquee;
    }

    /**
     * Start listening for new mails.
     */
    public void install() {
        listener = new MailService.NewMailListener() {
            @Override
            public void onNewMail(String sender, String subject) {
                Settings s = SettingsHelper.load();
                if (!s.mailNotifyEnabled) return;

                Color color = resolveColor(s, sender);
                // Build display text: "sender – subject" or just "sender"
                String text;
                if (subject != null && !subject.trim().isEmpty()) {
                    text = sender + "  \u2014  " + subject;
                } else {
                    text = sender;
                }
                marquee.addNotification(text, color);
            }
        };
        MailService.getInstance().addNewMailListener(listener);
    }

    /**
     * Stop listening.
     */
    public void uninstall() {
        if (listener != null) {
            MailService.getInstance().removeNewMailListener(listener);
            listener = null;
        }
    }

    /**
     * Resolve the colour for a given sender address:
     * 1. Check per-sender overrides (case-insensitive substring match)
     * 2. Fall back to the default notification colour
     * 3. Fall back to red if nothing configured
     */
    private static Color resolveColor(Settings s, String sender) {
        String senderLower = sender != null ? sender.toLowerCase() : "";

        // Per-sender overrides
        if (s.mailNotifySenderColors != null && !s.mailNotifySenderColors.isEmpty()) {
            for (Map.Entry<String, String> entry : s.mailNotifySenderColors.entrySet()) {
                if (senderLower.contains(entry.getKey().toLowerCase())) {
                    Color c = parseHexColor(entry.getValue());
                    if (c != null) return c;
                }
            }
        }

        // Default colour from settings
        if (s.mailNotifyDefaultColor != null && !s.mailNotifyDefaultColor.isEmpty()) {
            Color c = parseHexColor(s.mailNotifyDefaultColor);
            if (c != null) return c;
        }

        return new Color(0xCC, 0x00, 0x00); // fallback red
    }

    private static Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            int rgb = Integer.parseInt(hex, 16);
            return new Color(rgb);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

