package org.example.ftp;

public interface FtpDirectoryObserver {
    void onDirectoryChanged(String newPath);
}
