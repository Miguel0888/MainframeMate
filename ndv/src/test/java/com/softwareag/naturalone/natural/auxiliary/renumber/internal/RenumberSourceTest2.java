package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RenumberSource compatibility vs original auxiliary JAR")
public class RenumberSourceTest2 {

    private static final String REN_CLASS = "com.softwareag.naturalone.natural.auxiliary.renumber.internal.RenumberSource";
    private static final String INSERT_LABELS_IFACE = "com.softwareag.naturalone.natural.auxiliary.renumber.internal.IInsertLabels";

    private static RenumberFacade original;
    private static RenumberFacade candidate;

    @BeforeAll
    static void setUp() throws Exception {
//        File originalJar = resolveOriginalJar();
//        assertNotNull(originalJar, "Provide original auxiliary JAR via -Daux.original.jar=... or env AUX_ORIGINAL_JAR=...");
//        assertTrue(originalJar.isFile(), "Original auxiliary JAR not found: " + originalJar);
//
//        URL originalUrl = originalJar.toURI().toURL();
//
//        // Load original from JAR in isolated loader to avoid picking up candidate from the test classpath.
//        URLClassLoader originalLoader = new URLClassLoader(new URL[]{originalUrl}, null);
//
//        // Load candidate from current test classpath.
//        ClassLoader candidateLoader = RenumberSourceTest2.class.getClassLoader();
//
//        original = RenumberFacade.load(originalLoader);
//        candidate = RenumberFacade.load(candidateLoader);
    }

    @Nested
    @DisplayName("updateLineReferences")
    class UpdateLineReferencesTests {

        @Test
        void noParentheses() {
            assertEquivalent("updateLineReferences_noParentheses", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "END"};
                    Object ret = f.updateLineReferences(lines, 3, false);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }

        @Test
        void shiftReferenceInSecondLine() {
            assertEquivalent("updateLineReferences_shiftReferenceInSecondLine", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "IF (0001)"};
                    Object ret = f.updateLineReferences(lines, 1, false);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }

        @Test
        void multipleReferencesSameLine() {
            assertEquivalent("updateLineReferences_multipleReferencesSameLine", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "IF (0001) AND (0001)"};
                    Object ret = f.updateLineReferences(lines, 1, false);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }

        @Test
        void referenceInSingleQuotes_renConstFalse() {
            assertEquivalent("updateLineReferences_referenceInSingleQuotes_renConstFalse", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "WRITE '(0001)'"};
                    Object ret = f.updateLineReferences(lines, 1, false);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }

        @Test
        void referenceInSingleQuotes_renConstTrue() {
            assertEquivalent("updateLineReferences_referenceInSingleQuotes_renConstTrue", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "WRITE '(0001)'"};
                    Object ret = f.updateLineReferences(lines, 1, true);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }

        @Test
        void referenceInDoubleQuotes_renConstFalse() {
            assertEquivalent("updateLineReferences_referenceInDoubleQuotes_renConstFalse", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] lines = new String[]{"WRITE X", "WRITE \"(0001)\""};
                    Object ret = f.updateLineReferences(lines, 1, false);
                    return Outcome.of(normalize(ret), snapshot(lines), null);
                }
            });
        }
    }

    @Nested
    @DisplayName("addLineNumbers")
    class AddLineNumbersTests {

        @Test
        void stepZero() {
            assertEquivalent("addLineNumbers_stepZero", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{"A", "B", "C"};
                    String[] before = copy(source);
                    Object ret = f.addLineNumbers(source, 0, "L", false, false, false);
                    return Outcome.of(normalize(ret), snapshot(source), Arrays.asList(before));
                }
            });
        }

        @Test
        void stepAdjustToFit() {
            assertEquivalent("addLineNumbers_stepAdjustToFit", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    int len = 1500;
                    String[] source = new String[len];
                    for (int i = 0; i < len; i++) {
                        source[i] = "X";
                    }
                    Object ret = f.addLineNumbers(source, 10, "L", false, false, false);
                    List<String> normalized = castListOfString(normalize(ret));
                    List<String> checkpoints = Arrays.asList(
                            normalized.get(0),
                            normalized.get(1),
                            normalized.get(999),
                            normalized.get(len - 1)
                    );
                    return Outcome.of(checkpoints, snapshot(source), null);
                }
            });
        }

        @Test
        void updateRefs() {
            assertEquivalent("addLineNumbers_updateRefs", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{
                            "WRITE X",
                            "IF (0001)",
                            "IF (0002) AND (0001)",
                            "END"
                    };
                    String[] before = copy(source);
                    Object ret = f.addLineNumbers(source, 10, "L", true, false, false);
                    return Outcome.of(normalize(ret), snapshot(source), Arrays.asList(before));
                }
            });
        }

        @Test
        void openSystemsServerTrailingSpace() {
            assertEquivalent("addLineNumbers_openSystemsServerTrailingSpace", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{"WRITE X"};
                    Object ret = f.addLineNumbers(source, 10, "L", false, true, false);
                    return Outcome.of(normalize(ret), snapshot(source), null);
                }
            });
        }

        @Test
        void labelMode_basic() {
            assertEquivalent("addLineNumbers_labelMode_basic", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{
                            "L1. WRITE X",
                            "IF (L1.)",
                            "END"
                    };
                    Object ret = f.addLineNumbers(source, 10, "L", false, false, false);
                    return Outcome.of(normalize(ret), snapshot(source), null);
                }
            });
        }

        @Test
        void labelRefReplacementInParens() {
            assertEquivalent("addLineNumbers_labelRefReplacementInParens", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{
                            "L1. WRITE X",
                            "IF (L1.)",
                            "IF (L1.) AND (L1.)"
                    };
                    Object ret = f.addLineNumbers(source, 10, "L", false, false, false);
                    return Outcome.of(normalize(ret), snapshot(source), null);
                }
            });
        }

        @Test
        void labelMode_notTriggeredWithLeadingSpaces() {
            assertEquivalent("addLineNumbers_labelMode_notTriggeredWithLeadingSpaces", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String[] source = new String[]{
                            "  L1. WRITE X",
                            "IF (L1.)",
                            "END"
                    };
                    Object ret = f.addLineNumbers(source, 10, "L", false, false, false);
                    return Outcome.of(normalize(ret), snapshot(source), null);
                }
            });
        }
    }

    @Nested
    @DisplayName("removeLineNumbers")
    class RemoveLineNumbersTests {

        @Test
        void prefixRemoval() {
            assertEquivalent("removeLineNumbers_prefixRemoval", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    List<StringBuffer> input = list(
                            "0010 WRITE X",
                            "0020 END"
                    );
                    Object ret = f.removeLineNumbers(input, false, false, 5, 10, null);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void updateRefs_numericNormalization() {
            assertEquivalent("removeLineNumbers_updateRefs_numericNormalization", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    List<StringBuffer> input = list(
                            "0010 IF (0010)",
                            "0020 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, null);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void updateRefs_referenceNotFoundNoChange() {
            assertEquivalent("removeLineNumbers_updateRefs_referenceNotFoundNoChange", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    List<StringBuffer> input = list(
                            "0010 IF (0099)",
                            "0020 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, null);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void labelsInline() {
            assertEquivalent("removeLineNumbers_labelsInline", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object insertLabels = f.newInsertLabelsProxy(true, "L%d.", false);
                    List<StringBuffer> input = list(
                            "0010 WRITE X",
                            "0020 IF (0010)",
                            "0030 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, insertLabels);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void labelsNewLine() {
            assertEquivalent("removeLineNumbers_labelsNewLine", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object insertLabels = f.newInsertLabelsProxy(true, "L%d.", true);
                    List<StringBuffer> input = list(
                            "0010 WRITE X",
                            "0020 IF (0010)",
                            "0030 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, insertLabels);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void labelsExistingLabelReuse() {
            assertEquivalent("removeLineNumbers_labelsExistingLabelReuse", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object insertLabels = f.newInsertLabelsProxy(true, "L%d.", false);
                    List<StringBuffer> input = list(
                            "0010 ABC1. WRITE X",
                            "0020 IF (0010)",
                            "0030 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, insertLabels);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void labelsUniquenessSkipExistingInSource() {
            assertEquivalent("removeLineNumbers_labelsUniquenessSkipExistingInSource", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object insertLabels = f.newInsertLabelsProxy(true, "L%d.", false);
                    List<StringBuffer> input = list(
                            "0010 WRITE 'L1.'",
                            "0020 IF (0010)",
                            "0030 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, insertLabels);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }

        @Test
        void labelsCommentReferenceStaysNumeric() {
            assertEquivalent("removeLineNumbers_labelsCommentReferenceStaysNumeric", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object insertLabels = f.newInsertLabelsProxy(true, "L%d.", false);
                    List<StringBuffer> input = list(
                            "0010 * (0010)",
                            "0020 IF (0010)",
                            "0030 END"
                    );
                    Object ret = f.removeLineNumbers(input, true, false, 5, 10, insertLabels);
                    return Outcome.of(normalize(ret), snapshot(input), null);
                }
            });
        }
    }

    @Nested
    @DisplayName("isLineReference / isLineNumberReference")
    class DetectionTests {

        @Test
        void isLineReference_true() {
            assertEquivalent("isLineReference_true", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object ret = f.isLineReference(0, "(0010)");
                    return Outcome.of(normalize(ret), null, null);
                }
            });
        }

        @Test
        void isLineReference_false_tooShort() {
            assertEquivalent("isLineReference_false_tooShort", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Object ret = f.isLineReference(0, "(001)");
                    return Outcome.of(normalize(ret), null, null);
                }
            });
        }

        @Test
        void isLineNumberReference_commentLine_behavior() {
            assertEquivalent("isLineNumberReference_commentLine_behavior", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String line = "0010 * (0010)";
                    int pos = line.indexOf('(');
                    Object ret1 = f.isLineNumberReference(pos, line, false, true, false);
                    Object ret2 = f.isLineNumberReference(pos, line, true, true, false);
                    return Outcome.of(Arrays.asList(ret1, ret2), null, null);
                }
            });
        }

        @Test
        void isLineNumberReference_blockComment_behavior() {
            assertEquivalent("isLineNumberReference_blockComment_behavior", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String line = "/* (0010)";
                    int pos = line.indexOf('(');
                    Object ret1 = f.isLineNumberReference(pos, line, false, false, false);
                    Object ret2 = f.isLineNumberReference(pos, line, true, false, false);
                    return Outcome.of(Arrays.asList(ret1, ret2), null, null);
                }
            });
        }

        @Test
        void isLineNumberReference_inQuotes_renConstSwitch() {
            assertEquivalent("isLineNumberReference_inQuotes_renConstSwitch", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    String line = "WRITE '(0010)'";
                    int pos = line.indexOf('(');
                    Object ret1 = f.isLineNumberReference(pos, line, false, false, false);
                    Object ret2 = f.isLineNumberReference(pos, line, false, false, true);
                    return Outcome.of(Arrays.asList(ret1, ret2), null, null);
                }
            });
        }
    }

    @Nested
    @DisplayName("deterministic fuzz")
    class FuzzTests {

        @Test
        void fuzz_addLineNumbers_equivalence() {
            assertEquivalent("fuzz_addLineNumbers_equivalence", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Random rnd = new Random(12345L);
                    int len = 200;
                    String[] source = new String[len];
                    for (int i = 0; i < len; i++) {
                        String base = "S" + i;
                        if (i > 0 && rnd.nextInt(4) == 0) {
                            int ref = 1 + rnd.nextInt(i + 1);
                            base = base + " (" + pad4(ref) + ")";
                        }
                        if (rnd.nextInt(10) == 0) {
                            base = "WRITE '" + base + "'";
                        }
                        source[i] = base;
                    }

                    Object ret = f.addLineNumbers(source, 10, "Z", true, rnd.nextBoolean(), rnd.nextBoolean());
                    List<String> normalized = castListOfString(normalize(ret));
                    List<String> sample = Arrays.asList(
                            normalized.get(0),
                            normalized.get(1),
                            normalized.get(2),
                            normalized.get(50),
                            normalized.get(199)
                    );

                    return Outcome.of(sample, snapshot(source), null);
                }
            });
        }

        @Test
        void fuzz_removeLineNumbers_equivalence() {
            assertEquivalent("fuzz_removeLineNumbers_equivalence", new Case() {
                @Override
                public Outcome run(RenumberFacade f) throws Exception {
                    Random rnd = new Random(54321L);
                    int len = 120;

                    List<StringBuffer> input = new ArrayList<StringBuffer>();
                    for (int i = 0; i < len; i++) {
                        int lineNo = (i + 1) * 10;
                        String text = "L" + i;

                        if (i > 0 && rnd.nextInt(3) == 0) {
                            int targetIndex = rnd.nextInt(i + 1);
                            int targetLineNo = (targetIndex + 1) * 10;
                            text = text + " (" + pad4(targetLineNo) + ")";
                        }
                        if (rnd.nextInt(12) == 0) {
                            text = "* " + text;
                        }

                        input.add(new StringBuffer(pad4(lineNo) + " " + text));
                    }

                    Object insertLabels = f.newInsertLabelsProxy(rnd.nextBoolean(), "L%d.", rnd.nextBoolean());
                    Object ret = f.removeLineNumbers(input, true, rnd.nextBoolean(), 5, 10, insertLabels);

                    List<String> normalized = castListOfString(normalize(ret));
                    List<String> sample = Arrays.asList(
                            normalized.get(0),
                            normalized.get(1),
                            normalized.get(2),
                            normalized.get(normalized.size() / 2),
                            normalized.get(normalized.size() - 1)
                    );

                    return Outcome.of(sample, snapshot(input), null);
                }
            });
        }
    }

    private static void assertEquivalent(String name, Case testCase) {
        Outcome o1 = null;
        Outcome o2 = null;
        Throwable t1 = null;
        Throwable t2 = null;

        try {
            o1 = testCase.run(original);
        } catch (Throwable t) {
            t1 = unwrap(t);
        }

        try {
            o2 = testCase.run(candidate);
        } catch (Throwable t) {
            t2 = unwrap(t);
        }

        if (t1 != null || t2 != null) {
            if (t1 == null || t2 == null) {
                fail(formatExceptionMismatch(name, t1, t2));
            }
            assertEquals(t1.getClass().getName(), t2.getClass().getName(), formatExceptionMismatch(name, t1, t2));
            return;
        }

        if (!Objects.equals(o1, o2)) {
            fail(formatOutcomeMismatch(name, o1, o2));
        }
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) t).getCause();
            return cause == null ? t : cause;
        }
        return t;
    }

    private static String formatExceptionMismatch(String name, Throwable t1, Throwable t2) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mismatch in ").append(name).append("\n");
        sb.append("original throws: ").append(t1 == null ? "<none>" : t1.getClass().getName()).append("\n");
        sb.append("candidate throws: ").append(t2 == null ? "<none>" : t2.getClass().getName()).append("\n");
        sb.append("original message: ").append(t1 == null ? "" : safeMessage(t1)).append("\n");
        sb.append("candidate message: ").append(t2 == null ? "" : safeMessage(t2)).append("\n");
        return sb.toString();
    }

    private static String formatOutcomeMismatch(String name, Outcome o1, Outcome o2) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mismatch in ").append(name).append("\n");
        sb.append("original:  ").append(o1).append("\n");
        sb.append("candidate: ").append(o2).append("\n");
        return sb.toString();
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? "" : msg;
    }

    private interface Case {
        Outcome run(RenumberFacade f) throws Exception;
    }

    private static final class Outcome {
        private final Object returnValue;
        private final Object afterState;
        private final Object beforeState;

        private Outcome(Object returnValue, Object afterState, Object beforeState) {
            this.returnValue = returnValue;
            this.afterState = afterState;
            this.beforeState = beforeState;
        }

        static Outcome of(Object returnValue, Object afterState, Object beforeState) {
            return new Outcome(returnValue, afterState, beforeState);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Outcome)) {
                return false;
            }
            Outcome other = (Outcome) obj;
            return Objects.equals(returnValue, other.returnValue)
                    && Objects.equals(afterState, other.afterState)
                    && Objects.equals(beforeState, other.beforeState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(returnValue, afterState, beforeState);
        }

        @Override
        public String toString() {
            return "Outcome{return=" + returnValue + ", after=" + afterState + ", before=" + beforeState + "}";
        }
    }

    private static final class RenumberFacade {
        private final ClassLoader cl;

        private final Method updateLineReferences;
        private final Method addLineNumbers;
        private final Method removeLineNumbers;
        private final Method isLineNumberReference;
        private final Method isLineReference;

        private RenumberFacade(ClassLoader cl,
                               Method updateLineReferences,
                               Method addLineNumbers,
                               Method removeLineNumbers,
                               Method isLineNumberReference,
                               Method isLineReference) {
            this.cl = cl;
            this.updateLineReferences = updateLineReferences;
            this.addLineNumbers = addLineNumbers;
            this.removeLineNumbers = removeLineNumbers;
            this.isLineNumberReference = isLineNumberReference;
            this.isLineReference = isLineReference;
        }

        static RenumberFacade load(ClassLoader cl) throws Exception {
            Class<?> renCls = cl.loadClass(REN_CLASS);

            Method updateLineReferences = renCls.getMethod("updateLineReferences", String[].class, int.class, boolean.class);
            Method addLineNumbers = renCls.getMethod("addLineNumbers", String[].class, int.class, String.class, boolean.class, boolean.class, boolean.class);

            Class<?> insertLabelsIface = cl.loadClass(INSERT_LABELS_IFACE);
            Method removeLineNumbers = renCls.getMethod("removeLineNumbers", List.class, boolean.class, boolean.class, int.class, int.class, insertLabelsIface);

            Method isLineNumberReference = renCls.getMethod("isLineNumberReference", int.class, String.class, boolean.class, boolean.class, boolean.class);
            Method isLineReference = renCls.getMethod("isLineReference", int.class, String.class);

            return new RenumberFacade(cl, updateLineReferences, addLineNumbers, removeLineNumbers, isLineNumberReference, isLineReference);
        }

        Object updateLineReferences(String[] source, int delta, boolean renConst) throws Exception {
            return updateLineReferences.invoke(null, source, Integer.valueOf(delta), Boolean.valueOf(renConst));
        }

        Object addLineNumbers(String[] source,
                              int step,
                              String labelPrefix,
                              boolean updateRefs,
                              boolean openSystemsServer,
                              boolean renConst) throws Exception {
            return addLineNumbers.invoke(null, source, Integer.valueOf(step), labelPrefix,
                    Boolean.valueOf(updateRefs), Boolean.valueOf(openSystemsServer), Boolean.valueOf(renConst));
        }

        Object removeLineNumbers(List<StringBuffer> sourceWithLineNumbers,
                                 boolean updateRefs,
                                 boolean renConst,
                                 int prefixLength,
                                 int step,
                                 Object insertLabels) throws Exception {
            return removeLineNumbers.invoke(null, sourceWithLineNumbers,
                    Boolean.valueOf(updateRefs), Boolean.valueOf(renConst),
                    Integer.valueOf(prefixLength), Integer.valueOf(step), insertLabels);
        }

        Object isLineNumberReference(int pos, String line, boolean insertLabelsMode, boolean hasLineNumberPrefix, boolean renConst) throws Exception {
            return isLineNumberReference.invoke(null, Integer.valueOf(pos), line,
                    Boolean.valueOf(insertLabelsMode), Boolean.valueOf(hasLineNumberPrefix), Boolean.valueOf(renConst));
        }

        Object isLineReference(int pos, String line) throws Exception {
            return isLineReference.invoke(null, Integer.valueOf(pos), line);
        }

        Object newInsertLabelsProxy(final boolean insertLabels, final String format, final boolean createNewLine) throws Exception {
            final Class<?> iface = cl.loadClass(INSERT_LABELS_IFACE);

            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("isInsertLabels".equals(name)) {
                        return Boolean.valueOf(insertLabels);
                    }
                    if ("getLabelFormat".equals(name)) {
                        return format;
                    }
                    if ("isCreateNewLine".equals(name)) {
                        return Boolean.valueOf(createNewLine);
                    }
                    if ("toString".equals(name)) {
                        return "InsertLabelsProxy[insert=" + insertLabels + ",format=" + format + ",newLine=" + createNewLine + "]";
                    }
                    return null;
                }
            };

            return Proxy.newProxyInstance(cl, new Class[]{iface}, handler);
        }
    }

    private static File resolveOriginalJar() {
        String p = System.getProperty("aux.original.jar");
        if (p == null || p.trim().isEmpty()) {
            p = System.getenv("AUX_ORIGINAL_JAR");
        }
        if (p != null && !p.trim().isEmpty()) {
            return new File(p.trim());
        }

        // Try to find a matching jar on the classpath.
        String cp = System.getProperty("java.class.path");
        if (cp == null) {
            return null;
        }
        String[] parts = cp.split(File.pathSeparator);
        for (int i = 0; i < parts.length; i++) {
            String entry = parts[i];
            if (entry == null) {
                continue;
            }
            if (entry.endsWith(".jar") && entry.contains("com.softwareag.naturalone.natural.auxiliary")) {
                File f = new File(entry);
                if (f.isFile()) {
                    return f;
                }
            }
        }
        return null;
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            return value;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < len; i++) {
                Object element = Array.get(value, i);
                out.add(element == null ? null : element.toString());
            }
            return out;
        }
        if (value instanceof List) {
            List list = (List) value;
            List<String> out = new ArrayList<String>();
            for (Object o : list) {
                out.add(o == null ? null : o.toString());
            }
            return out;
        }
        return value.toString();
    }

    private static List<String> castListOfString(Object normalized) {
        if (normalized == null) {
            return null;
        }
        return (List<String>) normalized;
    }

    private static List<String> snapshot(String[] array) {
        return Arrays.asList(Arrays.copyOf(array, array.length));
    }

    private static List<String> snapshot(List<StringBuffer> lines) {
        List<String> result = new ArrayList<String>();
        for (StringBuffer sb : lines) {
            result.add(sb == null ? null : sb.toString());
        }
        return result;
    }

    private static String[] copy(String[] in) {
        return Arrays.copyOf(in, in.length);
    }

    private static List<StringBuffer> list(String... lines) {
        List<StringBuffer> result = new ArrayList<StringBuffer>();
        for (int i = 0; i < lines.length; i++) {
            result.add(new StringBuffer(lines[i]));
        }
        return result;
    }

    private static String pad4(int v) {
        return String.format("%04d", Integer.valueOf(v));
    }
}