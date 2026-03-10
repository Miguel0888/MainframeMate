package de.bund.zrb.files.impl.ftp.jes;

/**
 * Represents a single spool file (DD) within a JES job.
 */
public final class JesSpoolFile {

    private final int id;           // spool-file number (used in GET JOBxxxxx.n)
    private final String ddName;    // e.g. JESMSGLG, JESJCL, JESYSMSG, SYSPRINT
    private final String stepName;  // e.g. JES2, STEP01
    private final String procStep;  // procedure step or empty
    private final String dsClass;   // output class
    private final long byteCount;
    private final int recordCount;

    public JesSpoolFile(int id, String ddName, String stepName, String procStep,
                        String dsClass, long byteCount, int recordCount) {
        this.id = id;
        this.ddName = ddName;
        this.stepName = stepName;
        this.procStep = procStep;
        this.dsClass = dsClass;
        this.byteCount = byteCount;
        this.recordCount = recordCount;
    }

    public int    getId()          { return id; }
    public String getDdName()      { return ddName; }
    public String getStepName()    { return stepName; }
    public String getProcStep()    { return procStep; }
    public String getDsClass()     { return dsClass; }
    public long   getByteCount()   { return byteCount; }
    public int    getRecordCount() { return recordCount; }

    @Override
    public String toString() {
        return id + " " + ddName + " (" + stepName + ") " + recordCount + " records";
    }
}

