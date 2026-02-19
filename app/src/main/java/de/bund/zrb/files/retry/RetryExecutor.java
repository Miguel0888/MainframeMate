package de.bund.zrb.files.retry;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Führt eine Operation mit Retry-Logik aus, basierend auf einer FtpRetryPolicy.
 * Unterstützt Abbruch (Cancellation) und detailliertes Logging.
 */
public class RetryExecutor {

    /**
     * Callback für Retry-Events (Logging, Monitoring).
     */
    public interface RetryListener {
        void onAttemptStart(int attemptNumber, int maxAttempts);
        void onAttemptFailed(int attemptNumber, Throwable error, int ftpReplyCode, boolean willRetry, long nextDelayMs);
        void onSuccess(int attemptNumber);
        void onGiveUp(int attemptNumber, Throwable lastError);
    }

    /**
     * Schnittstelle für Operationen, die den FTP Reply Code liefern können.
     */
    public interface FtpReplyCodeProvider {
        int getLastReplyCode();
    }

    private final FtpRetryPolicy policy;
    private final RetryListener listener;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public RetryExecutor(FtpRetryPolicy policy) {
        this(policy, null);
    }

    public RetryExecutor(FtpRetryPolicy policy, RetryListener listener) {
        this.policy = policy != null ? policy : FtpRetryPolicy.createDefault();
        this.listener = listener;
    }

    /**
     * Bricht die aktuelle Retry-Schleife ab.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Prüft, ob abgebrochen wurde.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Führt die Operation mit Retries aus.
     * @param operation Die auszuführende Operation
     * @param replyCodeProvider Optional: Liefert den letzten FTP Reply Code
     * @return Das Ergebnis der Operation
     * @throws Exception Wenn alle Versuche fehlgeschlagen sind
     */
    public <T> T execute(Callable<T> operation, FtpReplyCodeProvider replyCodeProvider) throws Exception {
        int maxAttempts = policy.getMaxAttempts();
        Throwable lastError = null;
        int lastReplyCode = 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Abbruch prüfen
            if (cancelled.get()) {
                throw new InterruptedException("Operation cancelled by user");
            }

            // Listener: Versuch startet
            if (listener != null) {
                listener.onAttemptStart(attempt, maxAttempts);
            }

            try {
                T result = operation.call();

                // Erfolg!
                if (listener != null) {
                    listener.onSuccess(attempt);
                }
                return result;

            } catch (Exception e) {
                lastError = e;
                lastReplyCode = replyCodeProvider != null ? replyCodeProvider.getLastReplyCode() : 0;

                // Prüfen, ob Retry erfolgen soll
                boolean willRetry = policy.shouldRetry(e, lastReplyCode, attempt);
                long delayMs = willRetry ? policy.getDelayMs(attempt - 1) : 0;

                // Listener: Versuch fehlgeschlagen
                if (listener != null) {
                    listener.onAttemptFailed(attempt, e, lastReplyCode, willRetry, delayMs);
                }

                if (!willRetry) {
                    // Kein Retry - sofort aufgeben
                    break;
                }

                // Warten vor dem nächsten Versuch
                if (delayMs > 0 && attempt < maxAttempts) {
                    try {
                        // Warte in Schritten von 100ms, um schneller auf Abbruch reagieren zu können
                        long remaining = delayMs;
                        while (remaining > 0 && !cancelled.get()) {
                            long sleepTime = Math.min(remaining, 100);
                            Thread.sleep(sleepTime);
                            remaining -= sleepTime;
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }

        // Alle Versuche fehlgeschlagen
        if (listener != null) {
            listener.onGiveUp(policy.getMaxAttempts(), lastError);
        }

        if (lastError instanceof Exception) {
            throw (Exception) lastError;
        } else if (lastError != null) {
            throw new RuntimeException("All retry attempts failed", lastError);
        } else {
            throw new RuntimeException("All retry attempts failed (no error captured)");
        }
    }

    /**
     * Führt die Operation mit Retries aus (ohne ReplyCodeProvider).
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        return execute(operation, null);
    }

    /**
     * Default-Listener, der in die Konsole loggt.
     */
    public static RetryListener createLoggingListener(final String operationName) {
        return new RetryListener() {
            @Override
            public void onAttemptStart(int attemptNumber, int maxAttempts) {
                if (attemptNumber > 1) {
                    System.out.println("[FTP/Retry] " + operationName + ": Attempt " + attemptNumber + "/" + maxAttempts);
                }
            }

            @Override
            public void onAttemptFailed(int attemptNumber, Throwable error, int ftpReplyCode, boolean willRetry, long nextDelayMs) {
                String rootCause = getRootCauseClass(error);
                String msg = "[FTP/Retry] " + operationName + ": Attempt " + attemptNumber + " failed - " +
                        rootCause + ": " + error.getMessage();
                if (ftpReplyCode > 0) {
                    msg += " (FTP Reply: " + ftpReplyCode + ")";
                }
                if (willRetry) {
                    msg += " - will retry" + (nextDelayMs > 0 ? " after " + nextDelayMs + "ms" : "");
                }
                System.err.println(msg);
            }

            @Override
            public void onSuccess(int attemptNumber) {
                if (attemptNumber > 1) {
                    System.out.println("[FTP/Retry] " + operationName + ": Succeeded on attempt " + attemptNumber);
                }
            }

            @Override
            public void onGiveUp(int attemptNumber, Throwable lastError) {
                System.err.println("[FTP/Retry] " + operationName + ": Gave up after " + attemptNumber +
                        " attempt(s) - " + getRootCauseClass(lastError) + ": " +
                        (lastError != null ? lastError.getMessage() : "unknown error"));
            }

            private String getRootCauseClass(Throwable t) {
                if (t == null) return "null";
                while (t.getCause() != null) {
                    t = t.getCause();
                }
                return t.getClass().getSimpleName();
            }
        };
    }
}

