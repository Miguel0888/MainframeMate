package org.example.ftp;

import org.apache.commons.net.ftp.FTP;

public enum FtpFileStructure {
    FILE(FTP.FILE_STRUCTURE),
    RECORD(FTP.RECORD_STRUCTURE),
    PAGE(FTP.PAGE_STRUCTURE);

    private final int code;

    FtpFileStructure(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
