package de.zrb.bund.newApi.ui;

/**
 * Interface for tabs that support back/forward navigation.
 * Implementing tabs expose their internal navigation so that
 * global toolbar buttons and keyboard shortcuts can trigger it.
 */
public interface Navigable {

    /** Navigate to the previous location in history. */
    void navigateBack();

    /** Navigate to the next location in history. */
    void navigateForward();

    /** @return {@code true} if there is a previous location in the internal history. */
    boolean canNavigateBack();

    /** @return {@code true} if there is a next location in the internal history. */
    boolean canNavigateForward();
}

