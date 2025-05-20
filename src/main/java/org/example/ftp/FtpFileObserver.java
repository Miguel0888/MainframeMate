package org.example.ftp;

public interface FtpFileObserver {
    void onFileReloaded(String remotePath, String newContent);
}
