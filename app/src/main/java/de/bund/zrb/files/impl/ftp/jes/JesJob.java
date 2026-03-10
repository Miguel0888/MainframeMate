package de.bund.zrb.files.impl.ftp.jes;

/**
 * Represents a single JES job entry as returned by {@code LIST} in JES mode.
 */
public final class JesJob {

    private final String jobId;
    private final String jobName;
    private final String owner;
    private final String status;       // OUTPUT, ACTIVE, INPUT, HELD …
    private final String jobClass;
    private final String retCode;      // e.g. "CC 0000", "ABEND S0C4", null if still running
    private final int spoolFileCount;

    public JesJob(String jobId, String jobName, String owner, String status,
                  String jobClass, String retCode, int spoolFileCount) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.owner = owner;
        this.status = status;
        this.jobClass = jobClass;
        this.retCode = retCode;
        this.spoolFileCount = spoolFileCount;
    }

    public String getJobId()         { return jobId; }
    public String getJobName()       { return jobName; }
    public String getOwner()         { return owner; }
    public String getStatus()        { return status; }
    public String getJobClass()      { return jobClass; }
    public String getRetCode()       { return retCode; }
    public int    getSpoolFileCount(){ return spoolFileCount; }

    @Override
    public String toString() {
        return jobId + " " + jobName + " (" + status + ")"
                + (retCode != null ? " RC=" + retCode : "");
    }
}

