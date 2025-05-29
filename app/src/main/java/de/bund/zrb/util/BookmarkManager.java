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

    public static void addBookmarkToFolder(String parentLabel, BookmarkEntry newEntry) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        if (addToFolderRecursively(bookmarks, parentLabel, newEntry)) {
            saveBookmarks(bookmarks);
        }
    }

    private static boolean addToFolderRecursively(List<BookmarkEntry> entries, String parentLabel, BookmarkEntry newEntry) {
        for (BookmarkEntry entry : entries) {
            if (entry.folder && parentLabel.equals(entry.label)) {
                if (entry.children == null) {
                    entry.children = new ArrayList<>();
                }
                entry.children.add(newEntry);
                return true;
            } else if (entry.folder && entry.children != null) {
                if (addToFolderRecursively(entry.children, parentLabel, newEntry)) {
                    return true;
                }
            }
        }
        return false;
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

    public static void removeFolderByLabel(String label) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        if (removeFolderRecursively(bookmarks, label)) {
            saveBookmarks(bookmarks);
        }
    }

    private static boolean removeFolderRecursively(List<BookmarkEntry> entries, String label) {
        Iterator<BookmarkEntry> iterator = entries.iterator();
        boolean modified = false;
        while (iterator.hasNext()) {
            BookmarkEntry entry = iterator.next();
            if (entry.folder && label.equals(entry.label)) {
                iterator.remove();
                modified = true;
            } else if (entry.folder && entry.children != null) {
                modified |= removeFolderRecursively(entry.children, label);
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

    public static void renameFolder(String oldLabel, String newLabel) {
        List<BookmarkEntry> bookmarks = loadBookmarks();
        if (renameFolderRecursively(bookmarks, oldLabel, newLabel)) {
            saveBookmarks(bookmarks);
        }
    }

    private static boolean renameFolderRecursively(List<BookmarkEntry> entries, String oldLabel, String newLabel) {
        for (BookmarkEntry entry : entries) {
            if (entry.folder && oldLabel.equals(entry.label)) {
                entry.label = newLabel;
                return true;
            } else if (entry.folder && entry.children != null) {
                if (renameFolderRecursively(entry.children, oldLabel, newLabel)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void moveBookmarkTo(String id, String targetFolderId) {
        if(id.equals(targetFolderId)) {
            return; // WICHTIG: Würde das Element ansonsten löschen!!!
        }
        List<BookmarkEntry> root = loadBookmarks();
        BookmarkEntry moved = removeAndReturn(root, id);
        if (moved == null) return;

        if (targetFolderId == null) {
            root.add(moved);
        } else {
            BookmarkEntry folder = findById(root, targetFolderId);
            if(folder == null) {     // Sonderfall: Ziel ist Root → füge in root-Liste ein
                root.add(moved);
            } else if (folder.folder) {
                if (folder.children == null) folder.children = new ArrayList<>();
                folder.children.add(moved);
            }
        }

        saveBookmarks(root);
    }

    public static void moveBookmarkTo(String id, String targetFolderId, int insertIndex) {
        if(id.equals(targetFolderId)) {
            return; // WICHTIG: Würde das Element ansonsten löschen!!!
        }
        List<BookmarkEntry> root = loadBookmarks();
        BookmarkEntry moved = removeAndReturn(root, id);
        if (moved == null) return;

        List<BookmarkEntry> targetList;
        if (targetFolderId == null) {
            targetList = root;
        } else {
            BookmarkEntry folder = findById(root, targetFolderId);
            if(folder == null) {     // Sonderfall: Ziel ist Root → füge in root-Liste ein
                targetList = root;
            } else if (!folder.folder) {
                return;
            } else {
                if (folder.children == null) {
                    folder.children = new ArrayList<>();
                }
                targetList = folder.children;
            }
        }

        if (insertIndex < 0 || insertIndex > targetList.size()) {
            insertIndex = targetList.size();
        }

        targetList.add(insertIndex, moved);
        saveBookmarks(root);
    }

    private static BookmarkEntry removeById(List<BookmarkEntry> entries, String id) {
        Iterator<BookmarkEntry> it = entries.iterator();
        while (it.hasNext()) {
            BookmarkEntry entry = it.next();
            if (entry == null) continue;

            if (id.equals(entry.id)) {
                it.remove();
                return entry;
            }

            if (entry.folder && entry.children != null) {
                BookmarkEntry found = removeById(entry.children, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static BookmarkEntry findById(List<BookmarkEntry> list, String id) {
        for (BookmarkEntry entry : list) {
            if (entry == null) continue;

            if (id.equals(entry.id)) {
                return entry;
            }

            if (entry.folder && entry.children != null) {
                BookmarkEntry found = findById(entry.children, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static BookmarkEntry removeAndReturn(List<BookmarkEntry> entries, String id) {
        Iterator<BookmarkEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            BookmarkEntry entry = iterator.next();
            if (entry == null) continue;

            if (id.equals(entry.id)) {
                iterator.remove();
                return entry;
            }

            if (entry.folder && entry.children != null) {
                BookmarkEntry removed = removeAndReturn(entry.children, id);
                if (removed != null) return removed;
            }
        }
        return null;
    }

}
