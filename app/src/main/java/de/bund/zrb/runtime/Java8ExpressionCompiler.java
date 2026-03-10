package de.bund.zrb.runtime;

import de.bund.zrb.InMemoryJavaCompiler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

/**
 * Java 8 compatible expression compiler using {@link InMemoryJavaCompiler}.
 * Compiles the full source code into a {@code Function<Object, Object>} and invokes it.
 */
public class Java8ExpressionCompiler implements ExpressionCompiler {

    private final InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();

    @Override
    public String compileAndExecute(String key, String source, List<String> args) throws Exception {
        String className = extractClassName(source, key);
        Object instance = compiler.compile(className, source, Function.class);
        Method apply = instance.getClass().getMethod("apply", Object.class);
        Object result = apply.invoke(instance, args);
        return String.valueOf(result);
    }

    private String extractClassName(String source, String fallback) {
        for (String line : source.split("\\n")) {
            line = line.trim();
            if (line.startsWith("public class ")) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 3) return tokens[2];
            }
        }
        return "Expr_" + fallback.replaceAll("[^a-zA-Z0-9_$]", "_");
    }
}

