package de.bund.zrb;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * In-Memory Java Compiler — Java 8 compatible.
 * Uses only {@link ToolProvider#getSystemJavaCompiler()} (public API),
 * NOT the internal {@code com.sun.tools.javac.api.JavacTool} which is
 * inaccessible from unnamed modules on Java 9+.
 */
public class InMemoryJavaCompiler {

    private static JavaCompiler compiler;

    private static synchronized JavaCompiler getCompiler() {
        if (compiler == null) {
            compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException(
                        "JavaCompiler konnte nicht geladen werden. "
                                + "Stelle sicher, dass die Anwendung mit einem JDK (nicht JRE) gestartet wird "
                                + "und tools.jar im Classpath ist (Java 8).");
            }
        }
        return compiler;
    }

    public <T> T compile(String className, String sourceCode, Class<T> expectedType) throws Exception {
        JavaCompiler javaCompiler = getCompiler();

        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(
                javaCompiler.getStandardFileManager(null, null, null));

        JavaFileObject sourceFile = new JavaSourceFromString(className, sourceCode);

        List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"));

        boolean success = javaCompiler.getTask(
                null, fileManager, null, options, null,
                Collections.singletonList(sourceFile)
        ).call();

        if (!success) {
            throw new IllegalStateException("Compilation failed");
        }

        ClassLoader classLoader = fileManager.getClassLoader(null);
        Class<?> clazz = classLoader.loadClass(className);
        Object instance = clazz.newInstance();

        if (!expectedType.isInstance(instance)) {
            throw new ClassCastException("Compiled class does not implement expected type: " + expectedType.getName());
        }

        return expectedType.cast(instance);
    }
}
