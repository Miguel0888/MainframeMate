package de.bund.zrb.ftp;

public interface FtpObserver {
    void onDirectoryChanged(String newPath);
}
