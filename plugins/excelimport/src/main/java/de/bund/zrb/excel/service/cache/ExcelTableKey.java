package de.bund.zrb.excel.service.cache;

import java.io.File;
import java.io.IOException;

/**
 * Identify a cached Excel table for a given file and parsing options.
 */
public final class ExcelTableKey {

    private final String canonicalPath;
    private final boolean evaluateFormulas;
    private final int headerRowIndex;
    private final boolean stopOnEmptyCell;
    private final boolean stopOnEmptyLine;

    private ExcelTableKey(String canonicalPath,
                          boolean evaluateFormulas,
                          int headerRowIndex,
                          boolean stopOnEmptyCell,
                          boolean stopOnEmptyLine) {
        this.canonicalPath = canonicalPath;
        this.evaluateFormulas = evaluateFormulas;
        this.headerRowIndex = headerRowIndex;
        this.stopOnEmptyCell = stopOnEmptyCell;
        this.stopOnEmptyLine = stopOnEmptyLine;
    }

    public static ExcelTableKey from(File file,
                                    boolean evaluateFormulas,
                                    int headerRowIndex,
                                    boolean stopOnEmptyCell,
                                    boolean stopOnEmptyLine) throws IOException {
        return new ExcelTableKey(file.getCanonicalPath(), evaluateFormulas, headerRowIndex, stopOnEmptyCell, stopOnEmptyLine);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExcelTableKey that = (ExcelTableKey) o;
        if (evaluateFormulas != that.evaluateFormulas) return false;
        if (headerRowIndex != that.headerRowIndex) return false;
        if (stopOnEmptyCell != that.stopOnEmptyCell) return false;
        if (stopOnEmptyLine != that.stopOnEmptyLine) return false;
        return canonicalPath.equals(that.canonicalPath);
    }

    @Override
    public int hashCode() {
        int result = canonicalPath.hashCode();
        result = 31 * result + (evaluateFormulas ? 1 : 0);
        result = 31 * result + headerRowIndex;
        result = 31 * result + (stopOnEmptyCell ? 1 : 0);
        result = 31 * result + (stopOnEmptyLine ? 1 : 0);
        return result;
    }
}
