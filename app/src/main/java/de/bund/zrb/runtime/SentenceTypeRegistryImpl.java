package de.bund.zrb.runtime;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SentenceTypeRegistryImpl implements SentenceTypeRegistry {

    private static SentenceTypeRegistryImpl instance;

    private SentenceTypeSpec loadedSpec = new SentenceTypeSpec();

    private SentenceTypeRegistryImpl() {
        SentenceTypeSpec loaded = SentenceTypeSettingsHelper.loadSentenceTypes();
        if (loaded != null) {
            loadedSpec = loaded;
        }
        // Ensure default file types exist (for auto-detection by extension)
        if (ensureDefaultFileTypes(loadedSpec)) {
            SentenceTypeSettingsHelper.saveSentenceTypes(loadedSpec);
        }
    }

    public static synchronized SentenceTypeRegistryImpl getInstance() {
        if (instance == null) {
            instance = new SentenceTypeRegistryImpl();
        }
        return instance;
    }

    @Override
    public SentenceTypeSpec getSentenceTypeSpec() {
        return loadedSpec;
    }

    @Override
    public void reload() {
        loadedSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
    }

    @Override
    public void save() {
        SentenceTypeSettingsHelper.saveSentenceTypes(loadedSpec);
    }

    @Override
    public Optional<SentenceDefinition> findDefinition(@Nullable String sentenceType) {
        if (sentenceType == null || sentenceType.trim().isEmpty()) {
            return Optional.empty();
        }

        return getSentenceTypeSpec().getDefinitions().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(sentenceType))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Ensures default file type definitions exist in the spec.
     * Returns true if any new definitions were added (caller should save).
     */
    private static boolean ensureDefaultFileTypes(SentenceTypeSpec spec) {
        // name, pathPattern, extensions (comma-sep), transferMode, syntaxStyle
        String[][] defaults = {
                {"PDF",          ".*\\.pdf$",              "pdf",           "binary",  ""},
                {"MD",           ".*\\.md$",               "md,markdown",   "ascii",   ""},
                {"WORD",         ".*\\.(doc|docx)$",        "doc,docx",      "binary",  ""},
                {"EXCEL",        ".*\\.(xls|xlsx)$",        "xls,xlsx",      "binary",  ""},
                {"OUTLOOK MAIL", ".*\\.(msg|eml)$",         "msg,eml",       "binary",  ""},
                {"JCL",          ".*\\.(jcl|proc|prc)$",   "jcl,proc,prc",  "ascii",   "text/properties"},
                {"COBOL",        ".*\\.(cbl|cob|cobol)$",  "cbl,cob,cobol", "ascii",   "text/plain"},
                {"NATURAL",      "",                        "",              "ascii",
                        de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL},
                {"Java",         ".*\\.java$",             "java",          "ascii",   "text/java"},
                {"Python",       ".*\\.py$",               "py",            "ascii",   "text/python"},
                {"JavaScript",   ".*\\.js$",               "js",            "ascii",   "text/javascript"},
                {"TypeScript",   ".*\\.ts$",               "ts",            "ascii",   "text/typescript"},
                {"JSON",         ".*\\.json$",             "json",          "ascii",   "text/json"},
                {"XML",          ".*\\.xml$",              "xml",           "ascii",   "text/xml"},
                {"HTML",         ".*\\.html?$",            "html,htm",      "ascii",   "text/html"},
                {"SQL",          ".*\\.sql$",              "sql",           "ascii",   "text/sql"},
                {"YAML",         ".*\\.ya?ml$",            "yaml,yml",      "ascii",   "text/yaml"},
                {"Shell",        ".*\\.(sh|bash)$",        "sh,bash",       "ascii",   "text/unix"},
                {"Batch",        ".*\\.(bat|cmd)$",        "bat,cmd",       "ascii",   "text/bat"},
                {"Groovy",       ".*\\.(groovy|gradle)$",  "groovy,gradle", "ascii",   "text/groovy"},
        };

        boolean changed = false;
        Map<String, SentenceDefinition> defs = spec.getDefinitions();

        for (String[] d : defaults) {
            String key = d[0];
            boolean exists = defs.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase(key));
            if (exists) continue;

            SentenceDefinition sd = new SentenceDefinition();
            sd.setCategory("filetype");

            SentenceMeta meta = new SentenceMeta();
            meta.setPathPattern(d[1]);
            meta.setTransferMode(d[3]);

            if (!d[2].isEmpty()) {
                List<String> extList = new ArrayList<>();
                for (String ext : d[2].split(",")) {
                    extList.add(ext.trim());
                }
                meta.setExtensions(extList);
            }
            if (d[4] != null && !d[4].isEmpty()) {
                meta.setSyntaxStyle(d[4]);
            }

            sd.setMeta(meta);
            defs.put(key, sd);
            changed = true;
        }

        return changed;
    }
}
