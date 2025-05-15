package org.example.ftp;

import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FtpPath {

    public enum AccessType {
        DIRECTORY, PDS_LISTING, PDS_MEMBER
    }

    private final List<FtpPathElement> elements;

    public FtpPath(String fullPath) {
        this.elements = parse(fullPath);
    }

    private List<FtpPathElement> parse(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) return Collections.emptyList();

        List<FtpPathElement> result = new ArrayList<>();
        for (String part : fullPath.split("/")) {
            if (isBlank(part)) continue;
            FtpPathParser parser = new FtpPathParser(part);
            result.add(new FtpPathElement(parser.getDataset(), parser.isPdsSyntaxUsed(), parser.getMember()));
        }
        return result;
    }

    public FTPFile[] list(FtpService service) throws IOException {
        AccessType accessType = getAccessType();
        String dataset = getTargetDataset();

        if (!service.isMvsMode()) {
            // PC-Modus: benutze normalen Pfad
            String fullPath = toPlainPath();
            return service.getClient().listFiles(fullPath);
        }

        switch (accessType) {
            case PDS_LISTING:
                return service.listPdsMembers(dataset);
            case DIRECTORY:
                return service.listMvsDirectory(dataset);
            case PDS_MEMBER:
                throw new IOException("Kann PDS-Member nicht als Verzeichnis öffnen: " +
                        dataset + "(" + getTargetMember() + ")");
            default:
                throw new IOException("Unbekannter Zugriffstyp: " + accessType);
        }
    }

    public AccessType getAccessType() {
        for (FtpPathElement e : elements) {
            if (e.isPdsMember()) return AccessType.PDS_MEMBER;
            if (e.isPdsListing()) return AccessType.PDS_LISTING;
        }
        return AccessType.DIRECTORY;
    }

    public String getTargetDataset() {
        for (FtpPathElement e : elements) {
            if (e.isPdsSyntaxUsed()) return e.getName();
        }
        // Kein PDS → baue einfachen Pfad
        return toPlainPath();
    }

    public String getTargetMember() {
        for (FtpPathElement e : elements) {
            if (e.isPdsMember()) return e.getMember();
        }
        return null;
    }

    public FtpPathElement getLastElement() {
        return elements.isEmpty() ? null : elements.get(elements.size() - 1);
    }

    public List<FtpPathElement> getElements() {
        return elements;
    }

    public String toPlainPath() {
        return String.join("/", elements.stream()
                .map(FtpPathElement::getDisplayName)
                .collect(Collectors.toList()));
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}
