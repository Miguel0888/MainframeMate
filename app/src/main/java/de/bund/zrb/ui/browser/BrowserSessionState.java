package de.bund.zrb.ui.browser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * UI-only state for stateless-by-path browsing.
 *
 * Holds navigation state (currentPath + back/forward history) completely client-side.
 */
public class BrowserSessionState {

    private String currentPath;
    private final Deque<String> back = new ArrayDeque<>();
    private final Deque<String> forward = new ArrayDeque<>();

    public BrowserSessionState(String initialPath) {
        this.currentPath = normalize(initialPath);
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public boolean canGoBack() {
        return !back.isEmpty();
    }

    public boolean canGoForward() {
        return !forward.isEmpty();
    }

    public void goTo(String nextPath) {
        nextPath = normalize(nextPath);
        if (Objects.equals(this.currentPath, nextPath)) {
            return;
        }
        back.push(this.currentPath);
        forward.clear();
        this.currentPath = nextPath;
    }

    public String back() {
        if (back.isEmpty()) {
            return currentPath;
        }
        forward.push(currentPath);
        currentPath = back.pop();
        return currentPath;
    }

    public String forward() {
        if (forward.isEmpty()) {
            return currentPath;
        }
        back.push(currentPath);
        currentPath = forward.pop();
        return currentPath;
    }

    private static String normalize(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        return path.trim();
    }
}

