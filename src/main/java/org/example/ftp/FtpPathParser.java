package org.example.ftp;

import org.example.util.GlobalConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal parser for a single FTP path segment.
 * Used by {@link FtpPath} to detect PDS syntax and members.
 * Not intended for direct use elsewhere.
 */
class FtpPathParser {

    private static final Pattern PDS_PATTERN = Pattern.compile(
            GlobalConfig.get("ftp.path.pattern"), Pattern.CASE_INSENSITIVE);

    private final String rawPath;
    private String dataset;
    private String member;

    public FtpPathParser(String rawPath) {
        this.rawPath = rawPath;
        parse();
    }

    private void parse() {
        Matcher matcher = PDS_PATTERN.matcher(rawPath);
        if (matcher.matches()) {
            dataset = matcher.group(1);
            member = matcher.group(2); // May be null or empty
        } else {
            dataset = rawPath;
            member = null;
        }
    }

    /**
     * Return the full dataset name without member.
     */
    public String getDataset() {
        return dataset;
    }

    /**
     * Return true if the path refers to a PDS member, e.g. DATASET(MEMBER).
     */
    public boolean isPdsMember() {
        return member != null && !member.isEmpty();
    }

    /**
     * Return true if the path refers to a PDS listing, e.g. DATASET().
     */
    public boolean isPdsListing() {
        return member != null && member.isEmpty();
    }

    /**
     * Return true if the path contains any kind of parentheses.
     */
    public boolean isPdsSyntaxUsed() {
        return member != null;
    }

    /**
     * Return the member name (can be null or empty).
     */
    public String getMember() {
        return member;
    }

    /**
     * Return the raw, unparsed path.
     */
    public String getRawPath() {
        return rawPath;
    }
}
