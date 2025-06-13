package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.ResolvableExpression;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionTreeParserTest {

    @Test
    public void testSimpleFunctionWithVariable() {
        Set<String> funcs = new HashSet<>(Arrays.asList("CsvToRegex"));
        ExpressionTreeParser parser = new ExpressionTreeParser(funcs);

        ResolvableExpression expr = parser.parse("{{CsvToRegex({{columns}})}}");

        assertTrue(expr instanceof FunctionExpression);
        FunctionExpression func = (FunctionExpression) expr;
        assertEquals("CsvToRegex", func.getFunctionName());
        assertTrue(func.getArguments().get(0) instanceof VariableExpression);
    }

}