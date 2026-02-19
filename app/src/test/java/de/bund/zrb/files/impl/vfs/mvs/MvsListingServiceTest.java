package de.bund.zrb.files.impl.vfs.mvs;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
