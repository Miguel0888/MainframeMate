package de.bund.zrb;

import com.sun.tools.javac.api.JavacTool;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.util.Collections;

public class InMemoryJavaCompiler {

    public <T> T compile(String className, String sourceCode, Class<T> expectedType) throws Exception {
//        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaCompiler compiler = JavacTool.create(); // Fix for pure JREs
        if (compiler == null) {
            throw new IllegalStateException(
                    "Kein JavaCompiler verfügbar. Stelle sicher, dass ein JDK verwendet wird (nicht nur ein JRE)."
            );
        }
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(
                compiler.getStandardFileManager(null, null, null));

        JavaFileObject sourceFile = new JavaSourceFromString(className, sourceCode);
        boolean success = compiler.getTask(null, fileManager, null, null, null, Collections.singletonList(sourceFile)).call();

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
