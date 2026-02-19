package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatPathTool.
 */
class StatPathToolTest {

    @TempDir
    Path tempDir;

    private StatPathTool tool;

    @BeforeEach
    void setUp() {
        tool = new StatPathTool(null); // MainframeContext not needed for basic tests
    }

    // Helper method for Java 8 compatibility
    private void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
    }

    @Test
    void getSpec_returnsValidToolSpec() {
        ToolSpec spec = tool.getSpec();

        assertNotNull(spec);
        assertEquals("stat_path", spec.getName());
        assertNotNull(spec.getDescription());
        assertNotNull(spec.getInputSchema());
        assertTrue(spec.getInputSchema().getRequired().contains("path"));
    }

    @Test
    void execute_withMissingPath_returnsError() {
        JsonObject input = new JsonObject();

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("path"));
    }

    @Test
    void execute_nonExistentPath_returnsSuccessWithExistsFalse() {
        JsonObject input = new JsonObject();
        input.addProperty("path", tempDir.resolve("does-not-exist.txt").toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertFalse(json.get("exists").getAsBoolean());
        assertEquals("unknown", json.get("kind").getAsString());
    }

    @Test
    void execute_existingFile_returnsFileMetadata() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        writeString(testFile, "Hello World");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("exists").getAsBoolean());
        assertEquals("file", json.get("kind").getAsString());
        assertTrue(json.get("local").getAsBoolean());
        assertTrue(json.has("sizeBytes"));
        assertEquals(11, json.get("sizeBytes").getAsLong()); // "Hello World" = 11 bytes
        assertTrue(json.has("lastModified"));
        assertTrue(json.get("readable").getAsBoolean());
    }

    @Test
    void execute_existingDirectory_returnsDirectoryMetadata() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        writeString(subDir.resolve("file1.txt"), "content1");
        writeString(subDir.resolve("file2.txt"), "content2");

        JsonObject input = new JsonObject();
        input.addProperty("path", subDir.toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("exists").getAsBoolean());
        assertEquals("directory", json.get("kind").getAsString());
        assertTrue(json.get("local").getAsBoolean());
        assertTrue(json.has("entryCountHint"));
        assertEquals(2, json.get("entryCountHint").getAsInt());
        assertTrue(json.has("lastModified"));
        assertTrue(json.get("readable").getAsBoolean());
    }

    @Test
    void execute_textFile_detectsMimeType() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        writeString(testFile, "This is plain text content.");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.has("mimeType"));
        assertEquals("text/plain", json.get("mimeType").getAsString());
        assertFalse(json.get("isBinary").getAsBoolean());
    }

    @Test
    void execute_jsonFile_detectsMimeType() throws IOException {
        Path testFile = tempDir.resolve("test.json");
        writeString(testFile, "{\"key\": \"value\"}");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.has("mimeType"));
        assertEquals("application/json", json.get("mimeType").getAsString());
    }

    @Test
    void execute_binaryFile_detectsBinary() throws IOException {
        Path testFile = tempDir.resolve("test.bin");
        // Create binary content with NUL bytes
        byte[] binaryContent = new byte[]{0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE};
        writeBytes(testFile, binaryContent);

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("isBinary").getAsBoolean());
    }

    @Test
    void execute_utf8FileWithBOM_detectsEncoding() throws IOException {
        Path testFile = tempDir.resolve("test-utf8-bom.txt");
        // UTF-8 BOM + text
        byte[] content = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'e', 'l', 'l', 'o'};
        writeBytes(testFile, content);

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectTextEncoding", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.has("encodingHint"));
        assertEquals("UTF-8", json.get("encodingHint").getAsString());
    }

    @Test
    void execute_utf16LEFileWithBOM_detectsEncoding() throws IOException {
        Path testFile = tempDir.resolve("test-utf16le.txt");
        // UTF-16LE BOM + text
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xFE, 'H', 0, 'i', 0};
        writeBytes(testFile, content);

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectTextEncoding", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.has("encodingHint"));
        assertEquals("UTF-16LE", json.get("encodingHint").getAsString());
    }

    @Test
    void execute_asciiFile_detectsEncoding() throws IOException {
        Path testFile = tempDir.resolve("test-ascii.txt");
        writeString(testFile, "Pure ASCII content 123");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectTextEncoding", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.has("encodingHint"));
        // ASCII is detected as ASCII (subset of UTF-8)
        assertEquals("ASCII", json.get("encodingHint").getAsString());
    }

    @Test
    void execute_withDetectMimeFalse_skipsMimeDetection() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        writeString(testFile, "Content");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectMime", false);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertFalse(json.has("mimeType"));
        assertFalse(json.has("isBinary"));
    }

    @Test
    void execute_responseNeverContainsContent() throws IOException {
        Path testFile = tempDir.resolve("secret.txt");
        writeString(testFile, "This is secret content that should not appear in response!");

        JsonObject input = new JsonObject();
        input.addProperty("path", testFile.toString());
        input.addProperty("detectMime", true);
        input.addProperty("detectTextEncoding", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        // A4: Response should NEVER contain file content
        assertFalse(json.has("content"));
        String jsonString = json.toString();
        assertFalse(jsonString.contains("secret content"));
    }

    @Test
    void execute_emptyDirectory_returnsZeroEntryCount() throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);

        JsonObject input = new JsonObject();
        input.addProperty("path", emptyDir.toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("directory", json.get("kind").getAsString());
        assertEquals(0, json.get("entryCountHint").getAsInt());
    }

    @Test
    void execute_largeFile_respectsMaxProbeBytes() throws IOException {
        Path largeFile = tempDir.resolve("large.txt");
        // Create file larger than default maxProbeBytes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        writeString(largeFile, sb.toString());

        JsonObject input = new JsonObject();
        input.addProperty("path", largeFile.toString());
        input.addProperty("detectMime", true);
        input.addProperty("maxProbeBytes", 100); // Small probe

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("sizeBytes").getAsLong() > 100);
        // Should still detect MIME with limited probe
        assertTrue(json.has("mimeType"));
    }

    @Test
    void execute_javaFile_detectsCorrectMime() throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        writeString(javaFile, "public class Test { }");

        JsonObject input = new JsonObject();
        input.addProperty("path", javaFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("text/x-java-source", json.get("mimeType").getAsString());
        assertFalse(json.get("isBinary").getAsBoolean());
    }

    @Test
    void execute_xmlFile_detectsCorrectMime() throws IOException {
        Path xmlFile = tempDir.resolve("data.xml");
        writeString(xmlFile, "<?xml version=\"1.0\"?><root/>");

        JsonObject input = new JsonObject();
        input.addProperty("path", xmlFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("application/xml", json.get("mimeType").getAsString());
    }

    @Test
    void execute_emptyFile_handlesGracefully() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        writeString(emptyFile, "");

        JsonObject input = new JsonObject();
        input.addProperty("path", emptyFile.toString());
        input.addProperty("detectMime", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("file", json.get("kind").getAsString());
        assertEquals(0, json.get("sizeBytes").getAsLong());
    }

    @Test
    void execute_pathWithSpecialCharacters_handlesCorrectly() throws IOException {
        Path specialDir = tempDir.resolve("dir with spaces");
        Files.createDirectory(specialDir);
        Path specialFile = specialDir.resolve("file (1).txt");
        writeString(specialFile, "content");

        JsonObject input = new JsonObject();
        input.addProperty("path", specialFile.toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("exists").getAsBoolean());
        assertEquals("file", json.get("kind").getAsString());
    }
}

