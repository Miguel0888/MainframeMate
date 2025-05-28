package de.bund.zrb.ftp;

import org.apache.commons.net.ftp.FTP;

public enum FtpTransferMode {
    STREAM(FTP.STREAM_TRANSFER_MODE),
    BLOCK(FTP.BLOCK_TRANSFER_MODE),
    COMPRESSED(FTP.COMPRESSED_TRANSFER_MODE);

    private final int code;

    FtpTransferMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
