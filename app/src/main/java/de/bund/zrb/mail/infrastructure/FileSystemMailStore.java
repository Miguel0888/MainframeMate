package de.bund.zrb.mail.infrastructure;

import de.bund.zrb.mail.model.MailboxRef;
import de.bund.zrb.mail.port.MailStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a local directory for .ost and .pst files.
 */
public class FileSystemMailStore implements MailStore {

    @Override
    public List<MailboxRef> listMailboxes(String directoryPath) {
        List<MailboxRef> result = new ArrayList<>();
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) {
            return result;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".ost") || name.endsWith(".pst")) {
                    result.add(new MailboxRef(file.getAbsolutePath(), file.getName()));
                }
            }
        }
        return result;
    }
}
