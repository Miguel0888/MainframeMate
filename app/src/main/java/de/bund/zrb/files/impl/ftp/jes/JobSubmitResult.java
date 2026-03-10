package de.bund.zrb.files.impl.ftp.jes;

import java.time.LocalDateTime;

/**
 * Result of a JCL job submission via FTP JES.
 */
public final class JobSubmitResult {

    private final String jobId;
    private final String jobName;
    private final String host;
    private final String user;
    private final LocalDateTime submittedAt;

    public JobSubmitResult(String jobId, String jobName, String host, String user) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.host = host;
        this.user = user;
        this.submittedAt = LocalDateTime.now();
    }

    public String getJobId()        { return jobId; }
    public String getJobName()      { return jobName; }
    public String getHost()         { return host; }
    public String getUser()         { return user; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }

    @Override
    public String toString() {
        return "JobSubmitResult{jobId='" + jobId + "', jobName='" + jobName
                + "', host='" + host + "', user='" + user
                + "', submittedAt=" + submittedAt + '}';
    }
}

