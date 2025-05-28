package de.bund.zrb.ftp;

import org.apache.commons.net.ftp.FTP;

public enum FtpFileType {
    ASCII(FTP.ASCII_FILE_TYPE),
    EBCDIC(FTP.EBCDIC_FILE_TYPE),
    BINARY(FTP.BINARY_FILE_TYPE),
    LOCAL(FTP.LOCAL_FILE_TYPE);

    private final int code;

    FtpFileType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
