package de.bund.zrb.jcl.parser;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * ANTLR-based JCL parser that builds a JclOutlineModel from JCL source code.
 * Falls back to SimpleJclParser if ANTLR parsing fails.
 */
public class AntlrJclParser {

    private final SimpleJclParser fallback = new SimpleJclParser();

    /**
     * Parse JCL content using ANTLR and return outline model.
     * Falls back to regex-based parser on error.
     */
    public JclOutlineModel parse(String content, String sourceName) {
        if (content == null || content.isEmpty()) {
            JclOutlineModel empty = new JclOutlineModel();
            empty.setSourceName(sourceName);
            return empty;
        }

        try {
            return parseWithAntlr(content, sourceName);
        } catch (Exception e) {
            System.err.println("[AntlrJclParser] ANTLR parse failed, falling back to SimpleJclParser: " + e.getMessage());
            return fallback.parse(content, sourceName);
        }
    }

    private JclOutlineModel parseWithAntlr(String content, String sourceName) {
        // Ensure content ends with newline (required by grammar)
        if (!content.endsWith("\n")) {
            content = content + "\n";
        }

        CharStream input = CharStreams.fromString(content);
        JCLLexer lexer = new JCLLexer(input);
        lexer.removeErrorListeners(); // suppress console noise

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JCLParser parser = new JCLParser(tokens);
        parser.removeErrorListeners(); // suppress console noise

        // Collect errors silently
        final int[] errorCount = {0};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                errorCount[0]++;
            }
        });

        JCLParser.JclDeckContext tree = parser.jclDeck();

        // If too many errors, fall back
        if (errorCount[0] > content.split("\n").length / 2) {
            return fallback.parse(content, sourceName);
        }

        JclOutlineModel model = new JclOutlineModel();
        model.setSourceName(sourceName);
        model.setTotalLines(content.split("\n").length);

        JclElement currentJob = null;
        JclElement currentStep = null;

        for (JCLParser.StatementContext stmt : tree.statement()) {
            if (stmt.jobStmt() != null) {
                JclElement job = buildJob(stmt.jobStmt());
                model.addElement(job);
                currentJob = job;
                currentStep = null;
            } else if (stmt.execStmt() != null) {
                JclElement exec = buildExec(stmt.execStmt());
                model.addElement(exec);
                if (currentJob != null) {
                    currentJob.addChild(exec);
                }
                currentStep = exec;
            } else if (stmt.ddStmt() != null) {
                JclElement dd = buildDd(stmt.ddStmt());
                model.addElement(dd);
                if (currentStep != null) {
                    currentStep.addChild(dd);
                }
            } else if (stmt.procStmt() != null) {
                JclElement proc = buildProc(stmt.procStmt());
                model.addElement(proc);
            } else if (stmt.pendStmt() != null) {
                JclElement pend = new JclElement(JclElementType.PEND, "",
                        stmt.pendStmt().getStart().getLine(), stmt.pendStmt().getText());
                model.addElement(pend);
            } else if (stmt.setStmt() != null) {
                JclElement set = buildSet(stmt.setStmt());
                model.addElement(set);
            } else if (stmt.includeStmt() != null) {
                JclElement inc = buildInclude(stmt.includeStmt());
                model.addElement(inc);
            } else if (stmt.jcllibStmt() != null) {
                JclElement jcllib = buildJcllib(stmt.jcllibStmt());
                model.addElement(jcllib);
            } else if (stmt.ifStmt() != null) {
                JclElement ifEl = buildIf(stmt.ifStmt());
                model.addElement(ifEl);
            } else if (stmt.elseStmt() != null) {
                JclElement elseEl = new JclElement(JclElementType.ELSE, "",
                        stmt.elseStmt().getStart().getLine(), stmt.elseStmt().getText());
                model.addElement(elseEl);
            } else if (stmt.endifStmt() != null) {
                JclElement endif = new JclElement(JclElementType.ENDIF, "",
                        stmt.endifStmt().getStart().getLine(), stmt.endifStmt().getText());
                model.addElement(endif);
            } else if (stmt.outputStmt() != null) {
                JclElement out = buildOutput(stmt.outputStmt());
                model.addElement(out);
            }
        }

        return model;
    }

    // ── builder methods ─────────────────────────────────────────────

    private JclElement buildJob(JCLParser.JobStmtContext ctx) {
        String name = nameText(ctx.name());
        JclElement el = new JclElement(JclElementType.JOB, name, ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildExec(JCLParser.ExecStmtContext ctx) {
        String name = ctx.name() != null ? nameText(ctx.name()) : "";
        JclElement el = new JclElement(JclElementType.EXEC, name, ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildDd(JCLParser.DdStmtContext ctx) {
        String name = ctx.name() != null ? nameText(ctx.name()) : "";
        JclElement el = new JclElement(JclElementType.DD, name, ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildProc(JCLParser.ProcStmtContext ctx) {
        String name = ctx.name() != null ? nameText(ctx.name()) : "";
        JclElement el = new JclElement(JclElementType.PROC, name, ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildSet(JCLParser.SetStmtContext ctx) {
        JclElement el = new JclElement(JclElementType.SET, "", ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildInclude(JCLParser.IncludeStmtContext ctx) {
        JclElement el = new JclElement(JclElementType.INCLUDE, "", ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildJcllib(JCLParser.JcllibStmtContext ctx) {
        JclElement el = new JclElement(JclElementType.JCLLIB, "", ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    private JclElement buildIf(JCLParser.IfStmtContext ctx) {
        String condition = "";
        if (ctx.anyTokens() != null) {
            condition = ctx.anyTokens().getText();
        }
        return new JclElement(JclElementType.IF, condition, ctx.getStart().getLine(), ctx.getText());
    }

    private JclElement buildOutput(JCLParser.OutputStmtContext ctx) {
        String name = ctx.name() != null ? nameText(ctx.name()) : "";
        JclElement el = new JclElement(JclElementType.OUTPUT, name, ctx.getStart().getLine(), ctx.getText());
        extractParams(ctx.paramList(), el);
        return el;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private String nameText(JCLParser.NameContext ctx) {
        if (ctx == null) return "";
        return ctx.getText();
    }

    private void extractParams(JCLParser.ParamListContext paramList, JclElement el) {
        if (paramList == null) return;
        for (JCLParser.ParamContext param : paramList.param()) {
            if (param.name() != null && param.EQ() != null && param.paramValue() != null) {
                String key = nameText(param.name()).toUpperCase();
                String value = paramValueText(param.paramValue());
                el.addParameter(key, value);
            } else if (param.paramValue() != null) {
                // Positional parameter — store with index
                String value = paramValueText(param.paramValue());
                // For EXEC, the first positional is often the proc name
                if (el.getType() == JclElementType.EXEC && el.getParameter("PROC") == null
                        && el.getParameter("PGM") == null) {
                    el.addParameter("PROC", value);
                }
            }
        }
    }

    private String paramValueText(JCLParser.ParamValueContext ctx) {
        if (ctx == null) return "";
        String text = ctx.getText();
        // Strip surrounding quotes
        if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }
}

