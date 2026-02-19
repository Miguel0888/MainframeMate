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
 * Unit tests for GrepSearchTool.
 */
class GrepSearchToolTest {

    @TempDir
    Path tempDir;

    private GrepSearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new GrepSearchTool(null);
    }

    // Helper methods for Java 8 compatibility
    private void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
    }

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
        assertEquals("grep_search", spec.getName());
        assertNotNull(spec.getDescription());
        assertNotNull(spec.getInputSchema());
        assertTrue(spec.getInputSchema().getRequired().contains("root"));
        assertTrue(spec.getInputSchema().getRequired().contains("pattern"));
    }

    @Test
    void execute_withMissingRoot_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("pattern", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("root"));
    }

    @Test
    void execute_withMissingPattern_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertTrue(json.get("message").getAsString().contains("pattern"));
    }

    @Test
    void execute_simpleTextSearch_findsMatches() throws IOException {
        writeString(tempDir.resolve("test.txt"), "Line 1\nHamburg Hafen is here\nLine 3");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "Hamburg Hafen");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
        assertEquals(1, json.get("fileHitCount").getAsInt());

        JsonArray hits = json.getAsJsonArray("hits");
        assertEquals(1, hits.size());

        JsonObject fileHit = hits.get(0).getAsJsonObject();
        assertTrue(fileHit.get("path").getAsString().endsWith("test.txt"));

        JsonArray matches = fileHit.getAsJsonArray("matches");
        assertEquals(1, matches.size());

        JsonObject match = matches.get(0).getAsJsonObject();
        assertEquals(2, match.get("lineNumber").getAsInt());
        assertTrue(match.get("line").getAsString().contains("Hamburg Hafen"));
    }

    @Test
    void execute_withMatchRanges_returnsCorrectPositions() throws IOException {
        writeString(tempDir.resolve("test.txt"), "The word test appears here");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject fileHit = hits.get(0).getAsJsonObject();
        JsonArray matches = fileHit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

        assertTrue(match.has("matchRanges"));
        JsonArray ranges = match.getAsJsonArray("matchRanges");
        assertEquals(1, ranges.size());

        JsonObject range = ranges.get(0).getAsJsonObject();
        assertEquals(9, range.get("start").getAsInt()); // "The word test" - test starts at 9
        assertEquals(13, range.get("end").getAsInt());
    }

    @Test
    void execute_regexSearch_findsPattern() throws IOException {
        writeString(tempDir.resolve("test.txt"), "Order OP01\nOrder OP02\nNo match");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "OP\\d{2}");
        input.addProperty("regex", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_caseSensitive_respectsCase() throws IOException {
        writeString(tempDir.resolve("test.txt"), "HAMBURG Hafen\nhamburg hafen");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "HAMBURG");
        input.addProperty("caseSensitive", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_caseInsensitiveByDefault() throws IOException {
        writeString(tempDir.resolve("test.txt"), "HAMBURG Hafen\nhamburg hafen");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "hamburg");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_wholeWord_matchesOnlyWholeWords() throws IOException {
        writeString(tempDir.resolve("test.txt"), "test testing tested\nonly test here");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "test");
        input.addProperty("wholeWord", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        // "testing" and "tested" should NOT match, only standalone "test"
        assertEquals(2, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_invertMatch_returnsNonMatchingLines() throws IOException {
        writeString(tempDir.resolve("test.txt"), "Line with match\nLine without\nAnother match");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("invertMatch", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject fileHit = hits.get(0).getAsJsonObject();
        JsonArray matches = fileHit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

        assertEquals("Line without", match.get("line").getAsString());
    }

    @Test
    void execute_filesMode_returnsOnlyPaths() throws IOException {
        writeString(tempDir.resolve("file1.txt"), "match here");
        writeString(tempDir.resolve("file2.txt"), "no match");
        writeString(tempDir.resolve("file3.txt"), "match here too");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match here");
        input.addProperty("outputMode", "FILES");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("FILES", json.get("outputMode").getAsString());
        assertEquals(2, json.get("fileHitCount").getAsInt());

        assertTrue(json.has("paths"));
        JsonArray paths = json.getAsJsonArray("paths");
        assertEquals(2, paths.size());

        // Should not have detailed hits
        assertFalse(json.has("hits"));
    }

    @Test
    void execute_countMode_returnsOnlyCounts() throws IOException {
        writeString(tempDir.resolve("file1.txt"), "match\nmatch\nmatch");
        writeString(tempDir.resolve("file2.txt"), "match");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("outputMode", "COUNT");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals("COUNT", json.get("outputMode").getAsString());
        assertEquals(4, json.get("totalMatches").getAsInt());

        assertTrue(json.has("counts"));
        JsonArray counts = json.getAsJsonArray("counts");
        assertEquals(2, counts.size());
    }

    @Test
    void execute_withContext_includesContextLines() throws IOException {
        String content = "Line 1\nLine 2\nMatch line\nLine 4\nLine 5";
        writeString(tempDir.resolve("test.txt"), content);

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "Match");
        input.addProperty("includeContext", true);
        input.addProperty("contextLines", 2);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject fileHit = hits.get(0).getAsJsonObject();
        JsonArray matches = fileHit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

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
    void execute_recursiveSearch() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        writeString(tempDir.resolve("root.txt"), "match in root");
        writeString(subDir.resolve("nested.txt"), "match in subdir");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("recursive", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_nonRecursiveSearch() throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        writeString(tempDir.resolve("root.txt"), "match in root");
        writeString(subDir.resolve("nested.txt"), "match in subdir");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("recursive", false);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_fileNamePattern_filtersFiles() throws IOException {
        writeString(tempDir.resolve("test.txt"), "match here");
        writeString(tempDir.resolve("test.json"), "match here too");
        writeString(tempDir.resolve("test.xml"), "match here also");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("fileNamePattern", "*.txt;*.json");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_maxHitsLimit_stopsAfterLimit() throws IOException {
        // Create file with many matches
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append("match line ").append(i).append("\n");
        }
        writeString(tempDir.resolve("test.txt"), content.toString());

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("maxHits", 10);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(10, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_maxMatchesPerFile_limitsPerFile() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("match ").append(i).append("\n");
        }
        writeString(tempDir.resolve("test.txt"), content.toString());

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("maxMatchesPerFile", 5);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(5, json.get("hitCount").getAsInt());
    }

    @Test
    void execute_skipsLargeFiles() throws IOException {
        writeString(tempDir.resolve("small.txt"), "match");
        writeString(tempDir.resolve("large.txt"), "match" + repeat("x", 1000));

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("maxFileSizeBytes", 500);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("hitCount").getAsInt());
        assertEquals(1, json.get("skippedTooLarge").getAsInt());
    }

    @Test
    void execute_skipsBinaryFiles() throws IOException {
        writeString(tempDir.resolve("text.txt"), "match here");
        writeBytes(tempDir.resolve("binary.bin"), new byte[]{0x00, 0x01, 'm', 'a', 't', 'c', 'h'});

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("fileHitCount").getAsInt());
        assertEquals(1, json.get("skippedBinary").getAsInt());
    }

    @Test
    void execute_includeBinary_searchesBinaryFiles() throws IOException {
        writeBytes(tempDir.resolve("binary.bin"), new byte[]{'m', 'a', 't', 'c', 'h', 0x00, 0x01});

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");
        input.addProperty("includeBinary", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(1, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_singleFile_searchesOnlyThatFile() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        writeString(testFile, "match line 1\nno match\nmatch line 2");

        JsonObject input = new JsonObject();
        input.addProperty("root", testFile.toString());
        input.addProperty("pattern", "match");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(2, json.get("hitCount").getAsInt());
        assertEquals(1, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_multipleMatchesInSameLine_reportsAllRanges() throws IOException {
        writeString(tempDir.resolve("test.txt"), "test test test");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject fileHit = hits.get(0).getAsJsonObject();
        JsonArray matches = fileHit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

        JsonArray ranges = match.getAsJsonArray("matchRanges");
        assertEquals(3, ranges.size()); // Three occurrences of "test"
    }

    @Test
    void execute_invalidRegex_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "[invalid(regex");
        input.addProperty("regex", true);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertEquals("PatternSyntaxError", json.get("errorType").getAsString());
    }

    @Test
    void execute_nonExistentPath_returnsError() {
        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.resolve("nonexistent").toString());
        input.addProperty("pattern", "test");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
    }

    @Test
    void execute_noMatches_returnsEmptyResult() throws IOException {
        writeString(tempDir.resolve("test.txt"), "nothing to find here");

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "xyz123");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());
        assertEquals(0, json.get("hitCount").getAsInt());
        assertEquals(0, json.get("fileHitCount").getAsInt());
    }

    @Test
    void execute_truncatesLongLines() throws IOException {
        String longLine = repeat("x", 600) + "match" + repeat("x", 600);
        writeString(tempDir.resolve("test.txt"), longLine);

        JsonObject input = new JsonObject();
        input.addProperty("root", tempDir.toString());
        input.addProperty("pattern", "match");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("success", json.get("status").getAsString());

        JsonArray hits = json.getAsJsonArray("hits");
        JsonObject fileHit = hits.get(0).getAsJsonObject();
        JsonArray matches = fileHit.getAsJsonArray("matches");
        JsonObject match = matches.get(0).getAsJsonObject();

        String line = match.get("line").getAsString();
        assertTrue(line.endsWith("..."));
        assertTrue(line.length() <= 510); // 500 + "..."
    }
}

