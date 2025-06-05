package de.bund.zrb;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.util.Collections;

public class InMemoryJavaCompiler {

    public <T> T compile(String className, String sourceCode, Class<T> expectedType) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
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
