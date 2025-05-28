package de.bund.zrb.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.model.BookmarkEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BookmarkManager {

    private static final File BOOKMARK_FILE = new File(SettingsManager.getSettingsFolder(), "bookmarks.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<BookmarkEntry> loadBookmarks() {
        if (!BOOKMARK_FILE.exists()) return new ArrayList<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(BOOKMARK_FILE), StandardCharsets.UTF_8)) {
            BookmarkEntry[] rootEntries = GSON.fromJson(reader, BookmarkEntry[].class);
            return new ArrayList<>(Arrays.asList(rootEntries));
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveBookmarks(List<BookmarkEntry> bookmarks) {
        try {
            BOOKMARK_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(BOOKMARK_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(bookmarks, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addBookmark(BookmarkEntry newEntry) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        bookmarks.add(newEntry);
        saveBookmarks(bookmarks);
    }

    public static void removeBookmarkByPath(String path) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        if (removeRecursively(bookmarks, path)) {
            saveBookmarks(bookmarks);
        }
    }

    private static boolean removeRecursively(List<BookmarkEntry> entries, String path) {
        Iterator<BookmarkEntry> iterator = entries.iterator();
        boolean modified = false;
        while (iterator.hasNext()) {
            BookmarkEntry entry = iterator.next();
            if (path.equals(entry.path)) {
                iterator.remove();
                modified = true;
            } else if (entry.folder && entry.children != null) {
                modified |= removeRecursively(entry.children, path);
            }
        }
        return modified;
    }

    public static void renameBookmark(String path, String newLabel) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        if (renameRecursively(bookmarks, path, newLabel)) {
            saveBookmarks(bookmarks);
        }
    }

    private static boolean renameRecursively(List<BookmarkEntry> entries, String path, String newLabel) {
        for (BookmarkEntry entry : entries) {
            if (path.equals(entry.path)) {
                entry.label = newLabel;
                return true;
            } else if (entry.folder && entry.children != null) {
                if (renameRecursively(entry.children, path, newLabel)) {
                    return true;
                }
            }
        }
        return false;
    }

}
