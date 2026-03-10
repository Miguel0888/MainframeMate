package de.bund.zrb.runtime;

import java.util.List;

/**
 * Abstraction for compiling and executing expression source code.
 * <p>
 * Two implementations exist:
 * <ul>
 *     <li>Java8ExpressionCompiler — uses javax.tools.ToolProvider (Java 8+)</li>
 *     <li>JShellExpressionCompiler — uses JShell via reflection (Java 9+)</li>
 * </ul>
 */
public interface ExpressionCompiler {

    /**
     * Compile and execute the given source code with the supplied arguments.
     *
     * @param key    expression key (used for class naming in compiler mode)
     * @param source full Java source code of the expression
     * @param args   runtime arguments passed to the expression
     * @return the string result of the expression execution
     * @throws Exception on compilation or execution failure
     */
    String compileAndExecute(String key, String source, List<String> args) throws Exception;
}

