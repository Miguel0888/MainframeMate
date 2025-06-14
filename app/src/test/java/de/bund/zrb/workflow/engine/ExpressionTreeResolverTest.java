package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;



public class ExpressionTreeResolverTest {

    private VariableRegistry variableRegistry;
    private ExpressionRegistry expressionRegistry;
    private ExpressionTreeParser parser;
    private ExpressionTreeResolver resolver;

    @BeforeEach
    public void setup() {
        // Simpler Fake-Registry für Variablen
        variableRegistry = new VariableRegistry() {
            private final Map<String, String> vars = new HashMap<>();

            @Override
            public void set(String key, String value) {
                vars.put(key, value);
            }

            @Override
            public String get(String key, long timeoutMillis) {
                return vars.get(key);
            }

            @Override
            public boolean contains(String key) {
                return vars.containsKey(key);
            }

            @Override
            public Set<String> getKeys() {
                return vars.keySet();
            }

            @Override
            public void clear() {
                vars.clear();
            }

            @Override
            public Map<String, String> getAllVariables() {
                return Collections.emptyMap();
            }

            @Override
            public boolean has(String name) {
                return false;
            }
        };

        // Simpler Fake-Registry für Expression-Funktionen
        expressionRegistry = new ExpressionRegistry() {
            @Override
            public String evaluate(String name, List<String> args) {
                if ("CsvToRegex".equals(name)) {
                    return String.join("|", args.get(0).split(";"));
                }
                if ("wrap".equals(name)) {
                    return "[" + args.get(0) + "]";
                }
                return "unknown";
            }

            @Override
            public void remove(String key) {

            }

            @Override
            public Set<String> getKeys() {
                return Collections.emptySet();
            }

            @Override
            public void reload() {

            }

            @Override
            public void save() {

            }

            @Override
            public Set<String> getFunctionNames() {
                return new HashSet<>(Arrays.asList("CsvToRegex", "wrap"));
            }

            @Override
            public void register(String key, String code) {
                // Für Test irrelevant
            }

            @Override
            public Optional<String> getCode(String key) {
                return Optional.empty();
            }

            @Override
            public String getSource(String key) {
                return "";
            }
        };

        parser = new ExpressionTreeParser(expressionRegistry.getFunctionNames());
        resolver = new ExpressionTreeResolver(variableRegistry, expressionRegistry, 1000);
    }

    @Test
    public void testLiteralOnly() throws Exception {
        ResolvableExpression expr = parser.parse("Hallo Welt");
        String result = resolver.resolve(expr);
        assertEquals("Hallo Welt", result);
    }

    @Test
    public void testSingleVariable() throws Exception {
        variableRegistry.set("foo", "bar");
        ResolvableExpression expr = parser.parse("{{foo}}");
        String result = resolver.resolve(expr);
        assertEquals("bar", result);
    }

    @Test
    public void testNestedFunction() throws Exception {
        variableRegistry.set("columns", "A;B;C");
        ResolvableExpression expr = parser.parse("{{CsvToRegex({{columns}})}}");
        String result = resolver.resolve(expr);
        assertEquals("A|B|C", result);
    }

    @Test
    public void testFunctionWithLiteralArgument() throws Exception {
        ResolvableExpression expr = parser.parse("{{wrap('test')}}");
        String result = resolver.resolve(expr);
        assertEquals("[test]", result);
    }

    @Test
    public void testMultipleVariables() throws Exception {
        variableRegistry.set("x", "M1");
        variableRegistry.set("y", "M2");
        ResolvableExpression expr = parser.parse("{{wrap({{x}};{{y}})}}");
        String result = resolver.resolve(expr);
        assertEquals("[M1;M2]", result);
    }

    @Test
    public void testFunctionWithQuotedSemicolon() throws Exception {
        ResolvableExpression expr = parser.parse("{{CsvToRegex('M1;M2;M3')}}");
        String result = resolver.resolve(expr);
        assertEquals("M1|M2|M3", result);
    }

    @Test
    public void testVariableInsideFunction() throws Exception {
        variableRegistry.set("columns", "M1;M2");
        ResolvableExpression expr = parser.parse("{{wrap({{CsvToRegex({{columns}})}})}}");
        String result = resolver.resolve(expr);
        assertEquals("[M1|M2]", result);
    }

    @Test
    public void testUnknownFunctionFails() {
        Exception ex = assertThrows(IllegalStateException.class, () ->
                parser.parse("{{doesNotExist('x')}}")
        );
        assertTrue(ex.getMessage().contains("Unknown function"));
    }


    @Test
    public void testMissingVariableFails() {
        ResolvableExpression expr = parser.parse("{{missingVar}}");
        Exception ex = assertThrows(IllegalStateException.class, () -> resolver.resolve(expr));
        assertTrue(ex.getMessage().contains("not set"));
    }
}
