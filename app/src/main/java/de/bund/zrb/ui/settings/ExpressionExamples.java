package de.bund.zrb.ui.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enthält vordefinierte Beispielausdrücke für den ExpressionEditor.
 */
public final class ExpressionExamples {

    private ExpressionExamples() {
        // Verhindere Instanziierung
    }

    public static Map<String, String> getExamples() {
        Map<String, String> examples = new LinkedHashMap<>();

        examples.put("DDMMJJ",
                "import java.util.concurrent.Callable;\n" +
                        "import java.time.LocalDate;\n" +
                        "import java.time.format.DateTimeFormatter;\n" +
                        "public class Expr_DDMMJJ implements Callable<String> {\n" +
                        "    public String call() throws Exception {\n" +
                        "        return LocalDate.now().format(DateTimeFormatter.ofPattern(\"ddMMyy\"));\n" +
                        "    }\n" +
                        "}");

        examples.put("JJJJ-MM-TT",
                "import java.util.concurrent.Callable;\n" +
                        "import java.time.LocalDate;\n" +
                        "public class Expr_JJJJ_MM_TT implements Callable<String> {\n" +
                        "    public String call() throws Exception {\n" +
                        "        return LocalDate.now().toString();\n" +
                        "    }\n" +
                        "}");

        examples.put("Uhrzeit",
                "import java.util.concurrent.Callable;\n" +
                        "import java.time.LocalTime;\n" +
                        "public class Expr_Uhrzeit implements Callable<String> {\n" +
                        "    public String call() throws Exception {\n" +
                        "        return LocalTime.now().withNano(0).toString();\n" +
                        "    }\n" +
                        "}");

        examples.put("Uhr (tickend)",
                "import java.util.concurrent.Callable;\n" +
                        "import javax.swing.*;\n" +
                        "import java.awt.*;\n" +
                        "import java.time.LocalTime;\n" +
                        "public class Expr_Uhr_tickend implements Callable<String> {\n" +
                        "    public String call() throws Exception {\n" +
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

        return examples;
    }
}
