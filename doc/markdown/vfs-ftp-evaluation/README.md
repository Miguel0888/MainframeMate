# VFS FTP Evaluation Spike

This folder contains the manual evaluation instructions for the VFS FTP spike.

## How to run the spike tests
Set the following environment variables, then run the test class:

- `VFS_FTP_HOST`
- `VFS_FTP_USER`
- `VFS_FTP_PASS`
- `VFS_FTP_ROOT` (optional)
- `VFS_MVS_DATASET` (optional)
- `VFS_MVS_PDS` (optional)
- `VFS_MVS_MEMBER` (optional)
- `VFS_RW_PATH` (optional; writable test file)

Run:
```bash
./gradlew :app:test --tests de.bund.zrb.files.impl.ftp.vfs.VfsFtpSpikeTest
```

## Protocol
Fill out:
- `doc/markdown/VFS-FTP-Evaluation-Protocol.md`
