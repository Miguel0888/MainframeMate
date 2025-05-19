package org.example.ftp;

import org.apache.commons.net.ftp.FTP;

public enum FtpTextFormat {
    NON_PRINT(FTP.NON_PRINT_TEXT_FORMAT),
    TELNET(FTP.TELNET_TEXT_FORMAT),
    CARRIAGE_CONTROL(FTP.CARRIAGE_CONTROL_TEXT_FORMAT);

    private final int code;

    FtpTextFormat(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
