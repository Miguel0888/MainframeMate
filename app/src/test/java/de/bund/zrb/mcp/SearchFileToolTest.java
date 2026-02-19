package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
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
 * Unit tests for SearchFileTool.
 */
class SearchFileToolTest {

    @TempDir
    Path tempDir;

    private SearchFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchFileTool(null); // MainframeContext not needed for basic tests
    }

    // Helper method for Java 8 compatibility
    private void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    // Helper method for repeating a string (Java 8 compatible)
    private String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    @Test
    void getSpec_returnsValidToolSpec() {
        ToolSpec spec = tool.getSpec();

        assertNotNull(spec);
        assertEquals("search_file", spec.getName());
        assertNotNull(spec.getDescription());
        assertNotNull(spec.getInputSchema());
        assertTrue(spec.getInputSchema().getRequired().contains("root"));
        assertTrue(spec.getInputSchema().getRequired().contains("mode"));
    }

    @Test
    void execute_withMissingRoot_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("root"));
    }

    @Test
    void execute_withMissingMode_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("query", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("mode"));
    }

    @Test
    void execute_contentModeWithMissingQuery_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("query"));
    }

    @Test
    void execute_nameMode_findsFilesByPattern() throws IOException {
        // Create test files
        writeString(tempDir.resolve("test1.txt"), "content1");
        writeString(tempDir.resolve("test2.txt"), "content2");
        writeString(tempDir.resolve("other.json"), "{}");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "NAME");
        input.addProperty("fileNamePattern", "*.txt");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("NAME", json.get("mode").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());

        JsonArray hits = json.getAsJsonArray("hits");
        assertEquals(2, hits.size());
    }

    @Test
    void execute_contentMode_findsTextInFiles() throws IOException {
        // Create test files
        writeString(tempDir.resolve("file1.txt"), "This contains Hamburg Hafen in the text.");
        writeString(tempDir.resolve("file2.txt"), "This does not match.");
        writeString(tempDir.resolve("file3.txt"), "Another file with Hamburg Hafen here.");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "Hamburg Hafen");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("CONTENT", json.get("mode").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_contentMode_withContextLines() throws IOException {
        // Create test file with multiple lines
        String content = "Line 1\nLine 2\nLine 3 with TARGET text\nLine 4\nLine 5";
        writeString(tempDir.resolve("test.txt"), content);

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "TARGET");
        input.addProperty("includeContext", true);
        input.addProperty("contextLines", 2);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject hit = hits.get(0).getAsJsonObject();
        JsonArray matches = hit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

        assertEquals(3, match.get("lineNumber").getAsInt());
        assertTrue(match.has("contextBefore"));
        assertTrue(match.has("contextAfter"));

        JsonArray before = match.getAsJsonArray("contextBefore");
        assertEquals(2, before.size());
        assertEquals("Line 1", before.get(0).getAsString());
        assertEquals("Line 2", before.get(1).getAsString());

        JsonArray after = match.getAsJsonArray("contextAfter");
        assertEquals(2, after.size());
        assertEquals("Line 4", after.get(0).getAsString());
        assertEquals("Line 5", after.get(1).getAsString());
    }

    @Test
    void execute_contentMode_caseInsensitiveByDefault() throws IOException {
        writeString(tempDir.resolve("test.txt"), "HAMBURG hafen");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "hamburg HAFEN");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_contentMode_caseSensitiveWhenEnabled() throws IOException {
        writeString(tempDir.resolve("test.txt"), "HAMBURG hafen");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "hamburg");
        input.addProperty("caseSensitive", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(0, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_contentMode_regexSearch() throws IOException {
        writeString(tempDir.resolve("test.txt"), "Order number: 12345");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "\\d{5}");
        input.addProperty("regex", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_skipsFilesExceedingMaxSize() throws IOException {
        // Create a small file and a "large" file
        writeString(tempDir.resolve("small.txt"), "match text");
        writeString(tempDir.resolve("large.txt"), "match text" + repeat("x", 1000));

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "match");
        input.addProperty("maxFileSizeBytes", 500); // Very small limit

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
        assertEquals(1, json.get("skippedTooLarge").getAsInt());
    }

    @Test
    void execute_recursiveSearch() throws IOException {
        // Create nested directories
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        writeString(tempDir.resolve("root.txt"), "match in root");
        writeString(subDir.resolve("nested.txt"), "match in subdir");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "match");
        input.addProperty("recursive", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_nonRecursiveSearch() throws IOException {
        // Create nested directories
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        writeString(tempDir.resolve("root.txt"), "match in root");
        writeString(subDir.resolve("nested.txt"), "match in subdir");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "match");
        input.addProperty("recursive", false);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_maxHitsLimitIsRespected() throws IOException {
        // Create multiple files
        for (int i = 0; i < 10; i++) {
            writeString(tempDir.resolve("file" + i + ".txt"), "match content");
        }

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "match");
        input.addProperty("maxHits", 3);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(3, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_maxMatchesPerFileLimitIsRespected() throws IOException {
        // Create file with multiple matches
        String content = "match1\nmatch2\nmatch3\nmatch4\nmatch5\nmatch6\nmatch7";
        writeString(tempDir.resolve("test.txt"), content);

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "match");
        input.addProperty("maxMatchesPerFile", 3);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject hit = hits.get(0).getAsJsonObject();
        JsonArray matches = hit.getAsJsonArray("matches");

        assertEquals(3, matches.size());
    }

    @Test
    void execute_multipleFilePatterns() throws IOException {
        writeString(tempDir.resolve("test.txt"), "content");
        writeString(tempDir.resolve("test.json"), "{}");
        writeString(tempDir.resolve("test.xml"), "<root/>");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "NAME");
        input.addProperty("fileNamePattern", "*.txt;*.json");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_singleFileSearch() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        writeString(testFile, "Line with search term\nAnother line");

        JsonObject input = new JsonObject();
        input.addProperty("root", testFile.toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "search term");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_nonExistentPath_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.resolve("nonexistent").toString());
        input.addProperty("mode", "CONTENT");
        input.addProperty("query", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
    }

    @Test
    void execute_bothMode_findsNameAndContentMatches() throws IOException {
        // File that matches both name pattern query and content query
        writeString(tempDir.resolve("report.txt"), "This is a report file");
        // File that matches only name
        writeString(tempDir.resolve("data.txt"), "No match here");
        // File that matches only content
        writeString(tempDir.resolve("other.log"), "This is a report file");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("mode", "BOTH");
        input.addProperty("query", "report");
        input.addProperty("fileNamePattern", "*.txt;*.log");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        // Should find: report.txt (name + content), other.log (content only)
        assertTrue(json.get("hitCount").getAsInt() >= 2);
    }
}

