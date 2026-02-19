package de.bund.zrb.files.retry;

import de.bund.zrb.model.Settings;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Kapselt die Retry-Logik für FTP-Operationen basierend auf Settings.
 * Keine hardcodierten Werte - alles kommt aus den Settings.
 */
public class FtpRetryPolicy {

    public enum BackoffStrategy {
        FIXED,
        EXPONENTIAL
    }

    private final int maxAttempts;
    private final int backoffMs;
    private final BackoffStrategy backoffStrategy;
    private final int maxBackoffMs;
    private final boolean retryOnTimeout;
    private final boolean retryOnTransientIo;
    private final Set<Integer> retryOnReplyCodes;

    /**
     * Erstellt eine RetryPolicy aus den Settings.
     */
    public static FtpRetryPolicy fromSettings(Settings settings) {
        if (settings == null) {
            return createDefault();
        }
        return new FtpRetryPolicy(
                settings.ftpRetryMaxAttempts,
                settings.ftpRetryBackoffMs,
                parseBackoffStrategy(settings.ftpRetryBackoffStrategy),
                settings.ftpRetryMaxBackoffMs,
                settings.ftpRetryOnTimeout,
                settings.ftpRetryOnTransientIo,
                parseReplyCodes(settings.ftpRetryOnReplyCodes)
        );
    }

    /**
     * Erstellt die Default-Policy (entspricht bisherigem Verhalten).
     */
    public static FtpRetryPolicy createDefault() {
        return new FtpRetryPolicy(2, 0, BackoffStrategy.FIXED, 0, true, true, Collections.<Integer>emptySet());
    }

    /**
     * Erstellt eine Policy ohne Retries (nur 1 Versuch).
     */
    public static FtpRetryPolicy noRetry() {
        return new FtpRetryPolicy(1, 0, BackoffStrategy.FIXED, 0, false, false, Collections.<Integer>emptySet());
    }

    public FtpRetryPolicy(int maxAttempts, int backoffMs, BackoffStrategy backoffStrategy,
                          int maxBackoffMs, boolean retryOnTimeout, boolean retryOnTransientIo,
                          Set<Integer> retryOnReplyCodes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
        this.backoffStrategy = backoffStrategy != null ? backoffStrategy : BackoffStrategy.FIXED;
        this.maxBackoffMs = Math.max(0, maxBackoffMs);
        this.retryOnTimeout = retryOnTimeout;
        this.retryOnTransientIo = retryOnTransientIo;
        this.retryOnReplyCodes = retryOnReplyCodes != null ? retryOnReplyCodes : Collections.<Integer>emptySet();
    }

    /**
     * Prüft, ob bei diesem Fehler ein Retry erfolgen soll.
     * @param throwable Die aufgetretene Exception
     * @param ftpReplyCode Der FTP Reply Code (oder 0 wenn nicht verfügbar)
     * @param currentAttempt Der aktuelle Versuch (1-basiert)
     * @return true wenn Retry erfolgen soll
     */
    public boolean shouldRetry(Throwable throwable, int ftpReplyCode, int currentAttempt) {
        // Keine weiteren Versuche möglich
        if (currentAttempt >= maxAttempts) {
            return false;
        }

        // Prüfe auf Timeout-Exception
        if (retryOnTimeout && isTimeoutException(throwable)) {
            return true;
        }

        // Prüfe auf transiente IO-Fehler
        if (retryOnTransientIo && isTransientIoException(throwable)) {
            return true;
        }

        // Prüfe auf konfigurierte FTP Reply Codes
        if (ftpReplyCode > 0 && retryOnReplyCodes.contains(ftpReplyCode)) {
            return true;
        }

        return false;
    }

    /**
     * Berechnet die Wartezeit vor dem nächsten Versuch.
     * @param attemptIndex 0-basierter Index des nächsten Versuchs
     * @return Wartezeit in Millisekunden
     */
    public long getDelayMs(int attemptIndex) {
        if (backoffMs <= 0) {
            return 0;
        }

        long delay;
        if (backoffStrategy == BackoffStrategy.EXPONENTIAL) {
            // Exponentieller Backoff: backoffMs * 2^attemptIndex
            delay = backoffMs * (1L << attemptIndex);
        } else {
            // Fixed Backoff
            delay = backoffMs;
        }

        // Kappung anwenden
        if (maxBackoffMs > 0 && delay > maxBackoffMs) {
            delay = maxBackoffMs;
        }

        return delay;
    }

    /**
     * Prüft, ob die Exception ein Timeout darstellt.
     */
    private boolean isTimeoutException(Throwable t) {
        while (t != null) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof InterruptedIOException) {
                // InterruptedIOException kann auch Timeout sein
                String msg = t.getMessage();
                if (msg != null && (msg.contains("timed out") || msg.contains("timeout"))) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Prüft, ob die Exception ein transienter IO-Fehler ist (Connection Reset, etc.).
     */
    private boolean isTransientIoException(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("connection reset") ||
                    lower.contains("broken pipe") ||
                    lower.contains("connection refused") ||
                    lower.contains("network is unreachable")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static BackoffStrategy parseBackoffStrategy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BackoffStrategy.FIXED;
        }
        try {
            return BackoffStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BackoffStrategy.FIXED;
        }
    }

    private static Set<Integer> parseReplyCodes(String value) {
        Set<Integer> codes = new HashSet<Integer>();
        if (value == null || value.trim().isEmpty()) {
            return codes;
        }
        for (String part : value.split(",")) {
            try {
                int code = Integer.parseInt(part.trim());
                if (code > 0) {
                    codes.add(code);
                }
            } catch (NumberFormatException ignore) {
                // Skip invalid entries
            }
        }
        return codes;
    }

    // Getters für Logging

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getBackoffMs() {
        return backoffMs;
    }

    public BackoffStrategy getBackoffStrategy() {
        return backoffStrategy;
    }

    public int getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    public boolean isRetryOnTransientIo() {
        return retryOnTransientIo;
    }

    public Set<Integer> getRetryOnReplyCodes() {
        return Collections.unmodifiableSet(retryOnReplyCodes);
    }

    @Override
    public String toString() {
        return "FtpRetryPolicy{" +
                "maxAttempts=" + maxAttempts +
                ", backoffMs=" + backoffMs +
                ", strategy=" + backoffStrategy +
                ", maxBackoffMs=" + maxBackoffMs +
                ", onTimeout=" + retryOnTimeout +
                ", onTransientIo=" + retryOnTransientIo +
                ", replyCodes=" + retryOnReplyCodes +
                '}';
    }
}

