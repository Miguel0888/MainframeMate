package de.bund.zrb.runtime;

/**
 * Factory that selects the appropriate {@link ExpressionCompiler} implementation
 * based on the runtime Java version.
 * <ul>
 *     <li>Java 9+ with JShell available → {@link JShellExpressionCompiler}</li>
 *     <li>Java 8 or JShell unavailable  → {@link Java8ExpressionCompiler}</li>
 * </ul>
 * <p>
 * The factory itself is Java 8 compatible and uses no Java 9+ APIs directly.
 */
public final class ExpressionCompilerFactory {

    private ExpressionCompilerFactory() {
    }

    private static volatile ExpressionCompiler instance;

    /**
     * Returns a shared singleton compiler instance.
     */
    public static ExpressionCompiler getCompiler() {
        if (instance == null) {
            synchronized (ExpressionCompilerFactory.class) {
                if (instance == null) {
                    instance = createCompiler();
                }
            }
        }
        return instance;
    }

    private static ExpressionCompiler createCompiler() {
        if (isJShellAvailable()) {
            System.out.println("[ExpressionCompiler] Using JShell-based evaluator (Java 9+)");
            return new JShellExpressionCompiler();
        }
        System.out.println("[ExpressionCompiler] Using InMemoryJavaCompiler (Java 8 / no JShell)");
        return new Java8ExpressionCompiler();
    }

    /**
     * Check if JShell is available at runtime.
     * Works without importing jdk.jshell, so compiles on Java 8.
     */
    private static boolean isJShellAvailable() {
        if (!isAtLeastJava9()) {
            return false;
        }
        try {
            Class.forName("jdk.jshell.JShell");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isAtLeastJava9() {
        String version = System.getProperty("java.specification.version");
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        // Java 8 and earlier: "1.8", "1.7", etc.
        if (version.startsWith("1.")) {
            return false;
        }
        // Java 9+: "9", "11", "17", "21", etc.
        try {
            return Integer.parseInt(version) >= 9;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

