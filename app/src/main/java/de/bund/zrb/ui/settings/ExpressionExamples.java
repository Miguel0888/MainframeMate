package de.bund.zrb.ui.settings;

import de.zrb.bund.api.ExpressionRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enthält vordefinierte Beispielausdrücke für den ExpressionEditor.
 */
public final class ExpressionExamples {

    private ExpressionExamples() {
        // Verhindere Instanziierung
    }

    /**
     * Loads the examples if they are not persisted to file
     *
     * @param registry
     */
    public static void ensureExamplesRegistered(ExpressionRegistry registry) {
        ExpressionExamples.getExamples().forEach((key, code) -> {
            if (!registry.getCode(key).isPresent()) {
                registry.register(key, code);
            }
        });
    }

    public static Map<String, String> getExamples() {
        Map<String, String> examples = new LinkedHashMap<>();

        examples.put("Date",
                "import java.time.LocalDate;\n" +
                        "import java.time.format.DateTimeFormatter;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_Date implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        if (args.isEmpty()) return \"\";\n" +
                        "        String pattern = args.get(0);\n" +
                        "        try {\n" +
                        "            return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));\n" +
                        "        } catch (Exception e) {\n" +
                        "            return \"Ungültiges Format: \" + pattern;\n" +
                        "        }\n" +
                        "    }\n" +
                        "}");

        examples.put("DDMMJJ",
                "import java.time.LocalDate;\n" +
                        "import java.time.format.DateTimeFormatter;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_DDMMJJ implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        return LocalDate.now().format(DateTimeFormatter.ofPattern(\"ddMMyy\"));\n" +
                        "    }\n" +
                        "}");

        examples.put("JJJJ-MM-TT",
                "import java.time.LocalDate;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_JJJJ_MM_TT implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        return LocalDate.now().toString();\n" +
                        "    }\n" +
                        "}");

        examples.put("Uhrzeit",
                "import java.time.LocalTime;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_Uhrzeit implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        return LocalTime.now().withNano(0).toString();\n" +
                        "    }\n" +
                        "}");

        examples.put("Uhr (tickend)",
                "import javax.swing.*;\n" +
                        "import java.awt.*;\n" +
                        "import java.time.LocalTime;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_Uhr_tickend implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        JFrame f = new JFrame(\"Uhrzeit\");\n" +
                        "        JLabel label = new JLabel(\"\", SwingConstants.CENTER);\n" +
                        "        label.setFont(new Font(\"Monospaced\", Font.BOLD, 48));\n" +
                        "        f.add(label);\n" +
                        "        f.setSize(300, 150);\n" +
                        "        f.setLocationRelativeTo(null);\n" +
                        "        f.setVisible(true);\n" +
                        "        new Timer(1000, e -> label.setText(LocalTime.now().withNano(0).toString())).start();\n" +
                        "        return \"Uhr gestartet\";\n" +
                        "    }\n" +
                        "}");

        examples.put("DateOrDelete",
                "import java.time.LocalDate;\n" +
                        "import java.time.format.DateTimeFormatter;\n" +
                        "import java.util.List;\n" +
                        "import java.util.function.Function;\n" +
                        "public class Expr_DateOrDelete implements Function<List<String>, String> {\n" +
                        "    public String apply(List<String> args) {\n" +
                        "        if (args.isEmpty()) return \"\";\n" +
                        "        String text = args.get(0);\n" +
                        "        if (text.matches(\"(?i).*l(ö|oe)sch(e|en)?.*\")) {\n" +
                        "            return \"99999\";\n" +
                        "        }\n" +
                        "        return LocalDate.now().format(DateTimeFormatter.ofPattern(\"ddMMyy\"));\n" +
                        "    }\n" +
                        "}");

        return examples;
    }
}
