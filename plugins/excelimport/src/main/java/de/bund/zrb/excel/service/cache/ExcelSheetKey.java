package de.bund.zrb.excel.service.cache;

import java.io.File;
import java.io.IOException;

/**
 * Identify a cached sheet snapshot for a given file and formula evaluation mode.
 */
public final class ExcelSheetKey {

    private final String canonicalPath;
    private final boolean evaluateFormulas;

    private ExcelSheetKey(String canonicalPath, boolean evaluateFormulas) {
        this.canonicalPath = canonicalPath;
        this.evaluateFormulas = evaluateFormulas;
    }

    public static ExcelSheetKey from(File file, boolean evaluateFormulas) throws IOException {
        return new ExcelSheetKey(file.getCanonicalPath(), evaluateFormulas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExcelSheetKey that = (ExcelSheetKey) o;
        if (evaluateFormulas != that.evaluateFormulas) return false;
        return canonicalPath.equals(that.canonicalPath);
    }

    @Override
    public int hashCode() {
        int result = canonicalPath.hashCode();
        result = 31 * result + (evaluateFormulas ? 1 : 0);
        return result;
    }
}
