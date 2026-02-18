package de.bund.zrb.files;

import de.bund.zrb.files.path.MvsPathDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvsPathDialectTest {

    @Test
    void mapsRootToDoubleQuotes() {
        MvsPathDialect dialect = new MvsPathDialect();
        assertEquals("''", dialect.toAbsolutePath("/"));
        assertEquals("''", dialect.toAbsolutePath(""));
    }

    @Test
    void quotesDatasetPaths() {
        MvsPathDialect dialect = new MvsPathDialect();
        assertEquals("'ABC.DEF'", dialect.toAbsolutePath("ABC.DEF"));
    }

    @Test
    void detectsAndSplitsMemberPath() {
        MvsPathDialect dialect = new MvsPathDialect();
        String abs = dialect.toAbsolutePath("ABC.DEF(GHIJ)");
        assertEquals("'ABC.DEF(GHIJ)'", abs);

        String[] parts = dialect.splitMember(abs);
        assertEquals("ABC.DEF", parts[0]);
        assertEquals("GHIJ", parts[1]);
    }

    @Test
    void resolvesCandidatesForDotMemberNotation_firstTriesParentheses() {
        MvsPathDialect dialect = new MvsPathDialect();
        java.util.List<String> candidates = dialect.resolveCandidates("ABC.DEF.GHIJ");
        assertEquals("'ABC.DEF(GHIJ)'", candidates.get(0));
        assertEquals("'ABC.DEF.GHIJ'", candidates.get(1));
    }

    @Test
    void childOfBuildsDatasetMemberSpec() {
        MvsPathDialect dialect = new MvsPathDialect();
        assertEquals("'ABC.DEF(MEM)'", dialect.childOf("'ABC.DEF'", "MEM"));
    }
}
