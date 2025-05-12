package org.example.ftp;

public interface FtpObserver {
    void onDirectoryChanged(String newPath);
}
