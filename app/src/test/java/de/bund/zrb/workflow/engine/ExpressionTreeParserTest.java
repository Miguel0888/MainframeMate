package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.ResolvableExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionTreeParserTest {

    private ExpressionTreeParser parser;

    @BeforeEach
    public void setup() {
        parser = new ExpressionTreeParser(new HashSet<>(Arrays.asList("wrap", "CsvToRegex", "foo")));
    }

    @Test
    public void parsesSimpleLiteral() {
        ResolvableExpression expr = parser.parse("Hallo");
        assertTrue(expr instanceof LiteralExpression);
        assertEquals("Hallo", ((LiteralExpression) expr).getValue());
    }

    @Test
    public void parsesSingleVariable() {
        ResolvableExpression expr = parser.parse("{{user}}");
        assertTrue(expr instanceof VariableExpression);
        assertEquals("user", ((VariableExpression) expr).getName());
    }

    @Test
    public void parsesQuotedLiteral() {
        ResolvableExpression expr = parser.parse("{{'abc'}}");
        assertTrue(expr instanceof VariableExpression); // Cannot be a Literal because of the surrounding curly brackets
        assertEquals("abc", ((LiteralExpression) expr).getValue());
    }

    @Test
    public void parsesFunctionWithLiteralArgument() {
        ResolvableExpression expr = parser.parse("{{wrap('x')}}");
        assertTrue(expr instanceof FunctionExpression);
        FunctionExpression f = (FunctionExpression) expr;

        assertEquals("wrap", f.getFunctionName());
        assertEquals(1, f.getArguments().size());
        assertTrue(f.getArguments().get(0) instanceof LiteralExpression);
        assertEquals("x", ((LiteralExpression) f.getArguments().get(0)).getValue());
    }

    @Test
    public void parsesFunctionWithVariableArgument() {
        ResolvableExpression expr = parser.parse("{{wrap({{v}})}}");
        assertTrue(expr instanceof FunctionExpression);
        FunctionExpression f = (FunctionExpression) expr;

        assertEquals("wrap", f.getFunctionName());
        assertEquals(1, f.getArguments().size());
        assertTrue(f.getArguments().get(0) instanceof VariableExpression);
        assertEquals("v", ((VariableExpression) f.getArguments().get(0)).getName());
    }

    @Test
    public void parsesFunctionWithMultipleArgs() {
        ResolvableExpression expr = parser.parse("{{wrap('x';'y')}}");
        FunctionExpression f = (FunctionExpression) expr;

        assertEquals("wrap", f.getFunctionName());
        assertEquals(2, f.getArguments().size());
        assertEquals("x", ((LiteralExpression) f.getArguments().get(0)).getValue());
        assertEquals("y", ((LiteralExpression) f.getArguments().get(1)).getValue());
    }

    @Test
    public void parsesNestedFunctionCall() {
        ResolvableExpression expr = parser.parse("{{wrap({{CsvToRegex('a;b')}})}}");
        FunctionExpression outer = (FunctionExpression) expr;

        assertEquals("wrap", outer.getFunctionName());
        FunctionExpression inner = (FunctionExpression) outer.getArguments().get(0);

        assertEquals("CsvToRegex", inner.getFunctionName());
        assertEquals("a;b", ((LiteralExpression) inner.getArguments().get(0)).getValue());
    }

    @Test
    public void parsesCompositeExpression() {
        ResolvableExpression expr = parser.parse("hello {{name}}!");
        assertTrue(expr instanceof CompositeExpression);
        CompositeExpression composite = (CompositeExpression) expr;

        assertEquals(3, composite.getParts().size());
        assertEquals("hello ", ((LiteralExpression) composite.getParts().get(0)).getValue());
        assertEquals("name", ((VariableExpression) composite.getParts().get(1)).getName());
        assertEquals("!", ((LiteralExpression) composite.getParts().get(2)).getValue());
    }

    @Test
    public void parsesDeeplyNestedFunctions() {
        ResolvableExpression expr = parser.parse("{{wrap({{wrap({{wrap('deep')}})}})}}");

        FunctionExpression outer = (FunctionExpression) expr;
        FunctionExpression mid = (FunctionExpression) outer.getArguments().get(0);
        FunctionExpression inner = (FunctionExpression) mid.getArguments().get(0);

        assertEquals("deep", ((LiteralExpression) inner.getArguments().get(0)).getValue());
    }

    @Test
    public void failsOnUnmatchedBraces() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Hello {{unterminated"));
    }
}
