package de.bund.zrb.files.impl.local;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfsLocalFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listReadWriteDeleteCycle() throws Exception {
        FileService service = new VfsLocalFileService(tempDir);

        Path file = tempDir.resolve("hello.txt");
        FilePayload payload = FilePayload.fromBytes("hello".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, false);
        service.writeFile(file.toString(), payload);

        List<FileNode> nodes = service.list(tempDir.toString());
        assertTrue(nodes.stream().anyMatch(n -> "hello.txt".equals(n.getName())));

        FilePayload read = service.readFile(file.toString());
        assertEquals("hello", new String(read.getBytes(), StandardCharsets.UTF_8));

        assertTrue(service.delete(file.toString()));
        assertFalse(Files.exists(file));
    }

    @Test
    void createDirectoryWorks() throws FileServiceException {
        FileService service = new VfsLocalFileService(tempDir);
        Path dir = tempDir.resolve("child");

        assertTrue(service.createDirectory(dir.toString()));
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void listReturnsPathsReadableByReadFile() throws Exception {
        FileService service = new VfsLocalFileService(tempDir);

        Path file = tempDir.resolve("roundtrip.txt");
        FilePayload payload = FilePayload.fromBytes("data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, false);
        service.writeFile(file.toString(), payload);

        List<FileNode> nodes = service.list(tempDir.toString());
        FileNode node = nodes.stream()
                .filter(n -> "roundtrip.txt".equals(n.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FileNode not found"));

        FilePayload read = service.readFile(node.getPath());
        assertEquals("data", new String(read.getBytes(), StandardCharsets.UTF_8));
    }
}
