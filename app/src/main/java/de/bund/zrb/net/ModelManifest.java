package de.bund.zrb.net;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Describes a downloadable model: name, base URL, and the list of files.
 * <p>
 * Loaded from a {@code model-manifest.json} that lives alongside the model files
 * (in the repo or on the classpath).
 *
 * <pre>{@code
 * {
 *   "modelName": "Phi-3-mini-4k-instruct-onnx-directml-int4",
 *   "displayName": "Phi-3 Mini 4K Instruct (ONNX DirectML INT4)",
 *   "baseUrl": "https://huggingface.co/…/resolve/main/…/",
 *   "files": [
 *     { "name": "tokenizer.json", "size": 1937869 },
 *     { "name": "model.onnx.data", "size": 2131292928 }
 *   ]
 * }
 * }</pre>
 */
public final class ModelManifest {

    /** Used as subdirectory name under the model root (e.g. {@code ~/.mainframemate/model/<modelName>/}). */
    @SerializedName("modelName")
    private String modelName;

    /** Human-readable label shown in the UI. */
    @SerializedName("displayName")
    private String displayName;

    /** Remote base URL — file names are appended to this. */
    @SerializedName("baseUrl")
    private String baseUrl;

    /** Files that make up the model. */
    @SerializedName("files")
    private List<FileEntry> files;

    // ── getters ──────────────────────────────────────────────

    public String getModelName()   { return modelName; }
    public String getDisplayName() { return displayName; }
    public String getBaseUrl()     { return baseUrl; }

    public List<FileEntry> getFiles() {
        return files != null ? files : Collections.<FileEntry>emptyList();
    }

    // ── file descriptor ──────────────────────────────────────

    public static final class FileEntry {
        @SerializedName("name")
        private String name;

        @SerializedName("size")
        private long size = -1;

        public String getName()     { return name; }
        public long   getSize()     { return size; }
    }

    // ── factory methods ──────────────────────────────────────

    /**
     * Loads a manifest from a JSON file on disk.
     */
    public static ModelManifest fromFile(Path jsonFile) throws IOException {
        try (Reader r = new InputStreamReader(Files.newInputStream(jsonFile), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, ModelManifest.class);
        }
    }

    /**
     * Loads a manifest from a classpath resource.
     *
     * @param resourcePath e.g. {@code "/model-manifest.json"}
     */
    public static ModelManifest fromResource(String resourcePath) throws IOException {
        InputStream is = ModelManifest.class.getResourceAsStream(resourcePath);
        if (is == null) throw new IOException("Resource nicht gefunden: " + resourcePath);
        try (Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, ModelManifest.class);
        }
    }
}

