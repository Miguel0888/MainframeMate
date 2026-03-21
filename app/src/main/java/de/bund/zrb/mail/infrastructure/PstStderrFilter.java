package de.bund.zrb.mail.infrastructure;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Reusable stderr filter that suppresses java-libpst console noise.
 * <p>
 * java-libpst writes directly to {@code System.err} when it encounters
 * unusual structures in PST/OST files.  These are harmless warnings but
 * flood the console with lines like:
 * <ul>
 *   <li>{@code Unknown message type: IPM.AbchPerson}</li>
 *   <li>{@code Can't get children for folder Posteingang(8578) …}</li>
 *   <li>{@code getNodeInfo: block doesn't exist! …}</li>
 *   <li>Hex dumps ({@code f1f1 436a  ññCj})</li>
 *   <li>Standalone numbers ({@code 4})</li>
 *   <li>Separator lines ({@code ---})</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   PstStderrFilter.Guard guard = PstStderrFilter.install();
 *   try {
 *       // … PST operations …
 *   } finally {
 *       guard.uninstall();
 *   }
 * </pre>
 */
public final class PstStderrFilter {

    private PstStderrFilter() {}

    /**
     * Install a filtered stderr.  The returned {@link Guard} MUST be
     * closed (via {@link Guard#uninstall()}) to restore the original stream.
     */
    public static Guard install() {
        PrintStream original = System.err;
        PrintStream filtered = createFilteredStream(original);
        System.setErr(filtered);
        return new Guard(original);
    }

    /** RAII-style guard that restores the original stderr. */
    public static final class Guard {
        private final PrintStream original;
        private boolean restored;

        Guard(PrintStream original) {
            this.original = original;
        }

        /** Restore the original stderr. Safe to call multiple times. */
        public void uninstall() {
            if (!restored) {
                System.setErr(original);
                restored = true;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════

    private static PrintStream createFilteredStream(final PrintStream original) {
        return new PrintStream(new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    String msg = line.toString().trim();
                    if (!shouldSuppress(msg)) {
                        original.println(msg);
                    }
                    line.setLength(0);
                } else {
                    line.append((char) b);
                }
            }

            private boolean shouldSuppress(String msg) {
                if (msg.isEmpty()) return true;
                if (msg.startsWith("Unknown message type:")) return true;
                if (msg.startsWith("Can't get children for folder")) return true;
                if (msg.startsWith("getNodeInfo:")) return true;
                if (msg.equals("---")) return true;
                // Standalone numbers (e.g. "4", "1")
                if (msg.matches("^\\d+$")) return true;
                // Hex dump lines (e.g. "f1f1 436a  ññCj")
                if (msg.matches("^[0-9a-f]{4}\\s.*")) return true;
                // "using alternate child tree with N items"
                if (msg.contains("using alternate child tree with")) return true;
                return false;
            }
        });
    }
}

