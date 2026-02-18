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
}

