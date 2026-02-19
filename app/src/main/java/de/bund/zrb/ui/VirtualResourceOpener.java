package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.auth.InteractiveCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.login.LoginManager;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;

/**
 * Central stateless open logic: given a virtual reference, decide whether to open
 * a connection tab (directory) or a file tab (file), for LOCAL or FTP.
 *
 * Contract:
 * - Returns the opened tab (ConnectionTab or FileTab), or null on error.
 * - No Legacy manager usage; uses VirtualResource + FileService.
 */
public final class VirtualResourceOpener {

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final TabbedPaneManager tabManager;
    private final VirtualResourceResolver resolver = new VirtualResourceResolver();

    public VirtualResourceOpener(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    public FtpTab open(String rawPath,
                       @Nullable String sentenceType,
                       @Nullable String searchPattern,
                       @Nullable Boolean toCompare) {

        int attempts = 0;

        while (attempts < MAX_LOGIN_ATTEMPTS) {
            attempts++;

            VirtualResource resource;
            try {
                resource = resolver.resolve(rawPath);
            } catch (FileServiceException e) {
                if (isAuthError(e)) {
                    // Auth error during resolve - ask user to retry
                    LoginManager.RetryDecision decision = showLoginFailedDialog(e.getMessage(), attempts);
                    if (decision == LoginManager.RetryDecision.RETRY_WITH_NEW_PASSWORD) {
                        // Clear cached password and retry
                        clearCachedCredentials();
                        continue;
                    } else if (decision == LoginManager.RetryDecision.CANCEL) {
                        return null;
                    }
                    // RETRY - try again with same credentials
                    continue;
                }
                System.err.println("[VirtualResourceOpener] resolve failed: " + e.getMessage());
                showErrorDialog("Verbindungsfehler", e.getMessage());
                return null;
            }

            // Use interactive credentials provider that can show password dialog
            CredentialsProvider credentialsProvider = new InteractiveCredentialsProvider(
                    (host, user) -> LoginManager.getInstance().getPassword(host, user)
            );

            FileService fs = null;
            try {
                fs = new FileServiceFactory().create(resource, credentialsProvider);

                // Mark session as active after successful FTP connection
                if (!resource.isLocal() && resource.getFtpState() != null) {
                    LoginManager.getInstance().onLoginSuccess(
                            resource.getFtpState().getConnectionId().getHost(),
                            resource.getFtpState().getConnectionId().getUsername()
                    );
                }

                if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                    // IMPORTANT: For directory tabs, DO NOT close FileService here!
                    // The tab owns the FileService and will close it in onClose().
                    return openDirectory(resource, fs, searchPattern);
                } else {
                    // For file tabs, we can close after reading
                    try {
                        return openFile(resource, fs, sentenceType, searchPattern, toCompare);
                    } finally {
                        closeQuietly(fs);
                    }
                }
            } catch (Exception e) {
                closeQuietly(fs);

                if (isAuthError(e)) {
                    // Auth error - invalidate password and ask user
                    if (!resource.isLocal() && resource.getFtpState() != null) {
                        LoginManager.getInstance().onLoginFailed(
                                resource.getFtpState().getConnectionId().getHost(),
                                resource.getFtpState().getConnectionId().getUsername()
                        );
                    }

                    LoginManager.RetryDecision decision = showLoginFailedDialog(e.getMessage(), attempts);
                    if (decision == LoginManager.RetryDecision.RETRY_WITH_NEW_PASSWORD) {
                        clearCachedCredentials();
                        continue;
                    } else if (decision == LoginManager.RetryDecision.CANCEL) {
                        return null;
                    }
                    // RETRY - try again
                    continue;
                }

                System.err.println("[VirtualResourceOpener] open failed: " + e.getMessage());
                showErrorDialog("Verbindungsfehler", e.getMessage());
                return null;
            }
        }

        // Max attempts reached
        showErrorDialog("Login fehlgeschlagen",
                "Maximale Anzahl an Login-Versuchen erreicht.\nBitte prüfen Sie Ihre Zugangsdaten.");
        return null;
    }

    private boolean isAuthError(Exception e) {
        if (e instanceof FileServiceException) {
            FileServiceException fse = (FileServiceException) e;
            return fse.getErrorCode() == FileServiceErrorCode.AUTH_FAILED;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("auth") || msg.contains("login") || msg.contains("credentials") || msg.contains("password");
    }

    private LoginManager.RetryDecision showLoginFailedDialog(String errorMessage, int attempt) {
        // Use LoginManager's blocked login dialog or show our own
        if (LoginManager.getInstance().isLoginBlocked()) {
            LoginManager.BlockedLoginDecision decision = LoginManager.getInstance().showBlockedLoginDialog();
            switch (decision) {
                case RETRY:
                    return LoginManager.RetryDecision.RETRY;
                case RETRY_WITH_NEW_PASSWORD:
                    LoginManager.getInstance().resetLoginBlock();
                    return LoginManager.RetryDecision.RETRY_WITH_NEW_PASSWORD;
                default:
                    return LoginManager.RetryDecision.CANCEL;
            }
        }

        // Show simple retry dialog
        String message = "Login fehlgeschlagen!\n\n" +
                (errorMessage != null ? errorMessage : "Ungültiges Passwort") +
                "\n\nVersuch " + attempt + " von " + MAX_LOGIN_ATTEMPTS +
                "\n\nMöchten Sie es erneut versuchen?";

        Object[] options = {"Neues Passwort eingeben", "Abbrechen"};
        int result = JOptionPane.showOptionDialog(
                getParentFrame(),
                message,
                "Login fehlgeschlagen",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );

        if (result == 0) {
            return LoginManager.RetryDecision.RETRY_WITH_NEW_PASSWORD;
        }
        return LoginManager.RetryDecision.CANCEL;
    }

    private void showErrorDialog(String title, String message) {
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(getParentFrame(), message, title, JOptionPane.ERROR_MESSAGE)
        );
    }

    private Frame getParentFrame() {
        return tabManager != null ? tabManager.getParentFrame() : null;
    }

    private void clearCachedCredentials() {
        LoginManager.getInstance().clearCache();
    }

    private void closeQuietly(FileService fs) {
        if (fs != null) {
            try {
                fs.close();
            } catch (Exception ignored) {
            }
        }
    }

    private FtpTab openDirectory(VirtualResource resource, FileService fs, String searchPattern) {
        if (resource.isLocal()) {
            LocalConnectionTabImpl tab = new LocalConnectionTabImpl(tabManager);
            tabManager.addTab(tab);
            tab.loadDirectory(resource.getResolvedPath());
            return tab;
        }

        ConnectionTabImpl tab = new ConnectionTabImpl(resource, fs, tabManager, searchPattern);
        tabManager.addTab(tab);
        tab.loadDirectory(resource.getResolvedPath());
        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            tab.searchFor(searchPattern);
        }
        return tab;
    }

    private FtpTab openFile(VirtualResource resource, FileService fs,
                            String sentenceType, String searchPattern, Boolean toCompare) throws FileServiceException {
        FilePayload payload = fs.readFile(resource.getResolvedPath());
        Charset charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
        String content = new String(payload.getBytes(), charset);

        // Open file tab with VirtualResource (no Legacy manager)
        return tabManager.openFileTab(resource, content, sentenceType, searchPattern, toCompare);
    }
}
