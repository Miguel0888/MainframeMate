package de.bund.zrb.files.impl.vfs.mvs;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MvsListingServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void hlqListingCollapsesToImmediateQualifierAndDeduplicates() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.hlq("KKR097");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {
                "KKR097.JCLKURS.CNTL",
                "KKR097.TSO.CNTL",
                "KKR097.JCLKURS.MACLIB",
                "KKR097"
        };

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(2, resources.size());
        assertEquals("JCLKURS", resources.get(0).getDisplayName());
        assertEquals("'KKR097.JCLKURS'", resources.get(0).getLocation().getLogicalPath());
        assertEquals("TSO", resources.get(1).getDisplayName());
        assertEquals("'KKR097.TSO'", resources.get(1).getLocation().getLogicalPath());
    }

    @SuppressWarnings("unchecked")
    @Test
    void datasetListingTreatsQualifiedChildrenAsSubDatasetsNotMembers() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.dataset("KKR097.JCLKURS");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {
                "KKR097.JCLKURS.CNTL",
                "KKR097.JCLKURS.SOURCE"
        };

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(2, resources.size());
        assertEquals(MvsLocationType.DATASET, resources.get(0).getType());
        assertEquals("'KKR097.JCLKURS.CNTL'", resources.get(0).getLocation().getLogicalPath());
        assertEquals(MvsLocationType.DATASET, resources.get(1).getType());
        assertEquals("'KKR097.JCLKURS.SOURCE'", resources.get(1).getLocation().getLogicalPath());
    }


    @Test
    void datasetQueryCandidatesPreferMemberPattern() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation dataset = MvsLocation.dataset("KKR097.TSO.CNTL");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildQueryCandidates", MvsLocation.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) method.invoke(service, dataset, dataset.getQueryPath());

        assertEquals("'KKR097.TSO.CNTL(*)'", candidates.get(0));
    }


    @SuppressWarnings("unchecked")
    @Test
    void datasetListingParsesFullyQualifiedMemberAsMemberNode() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.dataset("KKR097.ZABAK.CNTL");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {"KKR097.ZABAK.CNTL(#NATJCL)"};

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(1, resources.size());
        assertEquals(MvsLocationType.MEMBER, resources.get(0).getType());
        assertEquals("#NATJCL", resources.get(0).getDisplayName());
        assertEquals("'KKR097.ZABAK.CNTL(#NATJCL)'", resources.get(0).getLocation().getLogicalPath());
    }

    // === Wildcard Bug-Reproducer Tests ===

    /**
     * Bug: 'APAB*' → server returns 'APABB' → click produced 'APAB*.APABB'
     * Expected: clicking 'APABB' should give HLQ 'APABB', not 'APAB*.APABB'.
     */
    @SuppressWarnings("unchecked")
    @Test
    void hlqWildcardDoesNotConcatenateWildcardWithResult() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.hlq("APAB*");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {"APABB"};

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(1, resources.size());
        MvsVirtualResource res = resources.get(0);
        assertEquals("APABB", res.getDisplayName());
        assertEquals("'APABB'", res.getLocation().getLogicalPath());
        // Must be HLQ so the user can browse into it
        assertEquals(MvsLocationType.HLQ, res.getType());
        // Must NOT contain the wildcard pattern
        assertFalse(res.getLocation().getLogicalPath().contains("*"),
                "Child path must not contain wildcard from parent");
    }

    /**
     * Bug: 'KKR07.ZABA*' → server returns 'KKR07.ZABAK' → click produced 'KKR07.ZABA*.KKR097'
     * Expected: child should be 'KKR07.ZABAK', not 'KKR07.ZABA*.KKR097'.
     */
    @SuppressWarnings("unchecked")
    @Test
    void qualifiedWildcardDoesNotConcatenateWildcardWithResult() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.qualifierContext("KKR07.ZABA*");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {"KKR07.ZABAK"};

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(1, resources.size());
        MvsVirtualResource res = resources.get(0);
        assertEquals("'KKR07.ZABAK'", res.getLocation().getLogicalPath());
        assertFalse(res.getLocation().getLogicalPath().contains("*"),
                "Child path must not contain wildcard from parent");
    }

    /**
     * Wildcard results with multiple qualifiers should be grouped properly.
     * 'APAB*' → server returns 'APABB.DATA.SET', 'APABB.SRC' →
     * should group into 'APABB' (not 'APAB*.APABB').
     */
    @SuppressWarnings("unchecked")
    @Test
    void hlqWildcardGroupsMultiQualifierResults() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.hlq("APAB*");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {"APABB.DATA.SET", "APABB.SRC", "APABC.WORK"};

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        // APABB.DATA.SET and APABB.SRC should be grouped as qualifier 'APABB',
        // APABC.WORK as qualifier 'APABC'
        assertEquals(2, resources.size());
        assertEquals("'APABB'", resources.get(0).getLocation().getLogicalPath());
        assertEquals("'APABC'", resources.get(1).getLocation().getLogicalPath());
    }

    /**
     * 'KKR07.ZABA*' → server returns 'KKR07.ZABAK.CNTL', 'KKR07.ZABAK.SOURCE' →
     * should group into 'KKR07.ZABAK'.
     */
    @SuppressWarnings("unchecked")
    @Test
    void qualifiedWildcardGroupsSubQualifierResults() throws Exception {
        MvsListingService service = new MvsListingService(new FTPClient());
        MvsLocation parent = MvsLocation.qualifierContext("KKR07.ZABA*");

        Method method = MvsListingService.class.getDeclaredMethod(
                "buildResourcesFromNames", String[].class, MvsLocation.class, AtomicBoolean.class);
        method.setAccessible(true);

        String[] names = {"KKR07.ZABAK.CNTL", "KKR07.ZABAK.SOURCE"};

        List<MvsVirtualResource> resources = (List<MvsVirtualResource>) method.invoke(
                service, names, parent, new AtomicBoolean(false));

        assertEquals(1, resources.size());
        assertEquals("'KKR07.ZABAK'", resources.get(0).getLocation().getLogicalPath());
        assertFalse(resources.get(0).getLocation().getLogicalPath().contains("*"));
    }

}
