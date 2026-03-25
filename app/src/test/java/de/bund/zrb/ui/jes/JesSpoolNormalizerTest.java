package de.bund.zrb.ui.jes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JobDetailTab#normalizeJesSpoolJcl(String)} —
 * the three-state normalizer that converts JES JESJCL spool output
 * into clean, resubmittable JCL.
 * <p>
 * All identifiers are fictional and do not represent real systems.
 */
class JesSpoolNormalizerTest {

    // ═══════════════════════════════════════════════════════════════════
    //  Basic prefix stripping
    // ═══════════════════════════════════════════════════════════════════

    private static final String MINI_SPOOL =
            "        1 //TESTJOB  JOB (ACCT),'PGMR',CLASS=A,                      J1234567\n" +
            "          //             MSGCLASS=S,NOTIFY=USR01\n" +
            "        2 //STEP1    EXEC PGM=IEFBR14\n" +
            "        3 //DD1      DD DSN=MY.DATA.SET,DISP=SHR\n";

    private static final String MINI_EXPECTED =
            "//TESTJOB  JOB (ACCT),'PGMR',CLASS=A,\n" +
            "//             MSGCLASS=S,NOTIFY=USR01\n" +
            "//STEP1    EXEC PGM=IEFBR14\n" +
            "//DD1      DD DSN=MY.DATA.SET,DISP=SHR";

    @Test
    void normalizeMinimalSpool() {
        String result = JobDetailTab.normalizeJesSpoolJcl(MINI_SPOOL);
        assertEquals(MINI_EXPECTED, result);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  XX / X/ / IEFC line removal
    // ═══════════════════════════════════════════════════════════════════

    private static final String SPOOL_WITH_PROC =
            "        1 //MYJOB    JOB (ACCT),'PGMR',CLASS=A\n" +
            "        2 //STEP1    EXEC MYPROC\n" +
            "        3 XXSTEP1    EXEC PGM=IEFBR14,REGION=0M\n" +
            "          IEFC653I SUBSTITUTION JCL - PGM=IEFBR14,REGION=0M\n" +
            "        4 //STEPLIB  DD DSN=MY.LOAD,DISP=SHR\n" +
            "          X/STEPLIB  DD DSN=PROC.DEFAULT.LOAD,DISP=SHR\n" +
            "        5 XXSYSOUT   DD SYSOUT=*\n";

    private static final String PROC_EXPECTED =
            "//MYJOB    JOB (ACCT),'PGMR',CLASS=A\n" +
            "//STEP1    EXEC MYPROC\n" +
            "//STEPLIB  DD DSN=MY.LOAD,DISP=SHR";

    @Test
    void normalizeRemovesXxAndXslashAndIefcLines() {
        String result = JobDetailTab.normalizeJesSpoolJcl(SPOOL_WITH_PROC);
        assertEquals(PROC_EXPECTED, result);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BUG FIX: multi-line IEFC653I continuation must be fully discarded
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void normalizeDiscardsMultiLineIefcContinuation() {
        String spool =
                "        1 //MYJOB    JOB (ACCT),'PGMR'\n" +
                "        2 //STEP1    EXEC MYPROC1,\n" +
                "          //         ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON MYLIB;XPGM)'\n" +
                "        3 XXMYPROC1  PROC PGMNAME=MYPROG01\n" +
                "        4 XXSTEP1    EXEC PGM=&PGMNAME,REGION=0M,\n" +
                "          XX PARM=('DBID=1,',\n" +
                "          XX     '&ZPARM')\n" +
                // IEFC message wrapping onto two lines:
                "          IEFC653I SUBSTITUTION JCL - PGM=MYPROG01,REGION=0M,PARM=('DBID=1,','MADIO=0,STACK=(LOGON \n" +
                "          MYLIB;XPGM)')\n" +
                "        5 //STEPLIB  DD\n";

        String result = JobDetailTab.normalizeJesSpoolJcl(spool);

        // Must NOT contain the IEFC continuation fragment
        assertFalse(result.contains("MYLIB;XPGM')"), "IEFC continuation must be discarded");

        // Must contain the real JCL
        assertTrue(result.contains("//MYJOB    JOB"));
        assertTrue(result.contains("ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON MYLIB;XPGM)'"));
        assertTrue(result.contains("//STEPLIB  DD"));

        // No XX or IEFC lines
        assertFalse(result.contains("XXMYPROC1"));
        assertFalse(result.contains("IEFC653I"));
    }

    @Test
    void normalizeDiscardsSpaceContinuation() {
        // IEFC message with continuation "50))" on next line
        String spool =
                "        1 //MYJOB    JOB (ACCT),'PGMR'\n" +
                "        2 //STEP1    EXEC PGM=SORT\n" +
                "        3 XXDDSORTIN DD DISP=(,DELETE),DSN=&&SORT,UNIT=(SYSDA,,DEFER),\n" +
                "          XX            DCB=RECFM=FB,SPACE=(CYL,(&SRTSPCE,&SRTSPCE))\n" +
                "          IEFC653I SUBSTITUTION JCL - DISP=(,DELETE),DSN=&&SORT,SPACE=(CYL,(50,\n" +
                "          50))\n" +
                "        4 //SYSOUT   DD SYSOUT=*\n";

        String result = JobDetailTab.normalizeJesSpoolJcl(spool);

        assertFalse(result.contains("50))"), "IEFC continuation '50))' must be discarded");
        assertTrue(result.contains("//SYSOUT   DD SYSOUT=*"));
        assertFalse(result.contains("IEFC653I"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BUG FIX: instream data after DD * must be preserved
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void normalizePreservesInstreamData() {
        String spool =
                "        1 //MYJOB    JOB (ACCT),'PGMR'\n" +
                "        2 //STEP1    EXEC PGM=DSNDEL\n" +
                "        3 //SYSIN    DD *\n" +
                "          DSN=MY.FIRST.DATASET\n" +
                "          DSN=MY.SECOND.DATASET\n" +
                "          /*\n" +
                "        4 //STEP2    EXEC PGM=IEFBR14\n";

        String result = JobDetailTab.normalizeJesSpoolJcl(spool);

        assertTrue(result.contains("//SYSIN    DD *"), "DD * must be present");
        assertTrue(result.contains("DSN=MY.FIRST.DATASET"), "Instream data must be preserved");
        assertTrue(result.contains("DSN=MY.SECOND.DATASET"), "Instream data must be preserved");
        assertTrue(result.contains("/*"), "Instream data delimiter must be present");
        assertTrue(result.contains("//STEP2    EXEC PGM=IEFBR14"), "Next step must be present");
    }

    @Test
    void normalizeHandlesOmittedInstreamData() {
        // JES2 typically omits instream data in JESJCL; the next // line follows directly
        String spool =
                "        1 //MYJOB    JOB (ACCT),'PGMR'\n" +
                "        2 //SYSIN    DD *\n" +
                "          //*\n" +
                "        3 //STEP2    EXEC PGM=IDCAMS\n";

        String result = JobDetailTab.normalizeJesSpoolJcl(spool);

        assertTrue(result.contains("//SYSIN    DD *"));
        assertTrue(result.contains("//*"));
        assertTrue(result.contains("//STEP2    EXEC PGM=IDCAMS"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Detection: non-spool content is returned unchanged
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Entire Operations JCL has a very long JOB card header (90+ comment lines
     * with symbol table info before the second numbered statement).
     * The normalizer must still detect and normalize such spool output.
     */
    @Test
    void normalizeLongJobHeaderSpool() {
        StringBuilder spool = new StringBuilder();
        spool.append("        1 //MYJOB    JOB (60760,F29,1,9999),                                      J0310702\n");
        spool.append("          //         'MYNETWORK-47358',                                                   \n");
        spool.append("          //           CLASS=B,                                                           \n");
        spool.append("          //          MSGCLASS=R,                                                         \n");
        spool.append("          //          REGION=0M,                                                          \n");
        spool.append("          //          NOTIFY=USR01,USER=BATCH01                                           \n");
        // 90 symbol dump comment lines (like Entire Operations generates)
        for (int i = 0; i < 90; i++) {
            spool.append("          //* Symbol: SYM").append(i).append("                                                            \n");
        }
        spool.append("        2 //OUTFEHL1 OUTPUT DEST=U0011,CLASS=C                                            \n");
        spool.append("        3 //STEP1    EXEC PGM=WAIT4END,PARM='MYJOB'                                       \n");
        spool.append("        4 //STEP2    EXEC MYPROC                                                          \n");
        spool.append("        5 XXSTEP2    EXEC PGM=MYPGM,REGION=0M                                             \n");
        spool.append("          IEFC653I SUBSTITUTION JCL - PGM=MYPGM,REGION=0M                                 \n");
        spool.append("        6 XXSTEPLIB  DD DSN=MY.LOAD,DISP=SHR                                              \n");
        spool.append("          IEFC653I SUBSTITUTION JCL - DSN=MY.LOAD,DISP=SHR                                \n");
        spool.append("        7 //SYSOUT   DD SYSOUT=*                                                          \n");

        String result = JobDetailTab.normalizeJesSpoolJcl(spool.toString());

        // Must be normalized (not returned unchanged)
        assertFalse(result.contains("IEFC653I"), "IEFC messages must be stripped");
        assertFalse(result.contains("XX"), "XX proc expansion lines must be stripped");
        assertTrue(result.contains("//MYJOB    JOB (60760,F29,1,9999),"), "JOB card must be present");
        assertFalse(result.contains("J0310702"), "JES job number must be stripped from JOB card");
        assertTrue(result.contains("//OUTFEHL1 OUTPUT DEST=U0011,CLASS=C"), "OUTPUT card must be present");
        assertTrue(result.contains("//STEP1    EXEC PGM=WAIT4END"), "EXEC step must be present");
        assertTrue(result.contains("//STEP2    EXEC MYPROC"), "PROC call must be present");
        assertTrue(result.contains("//SYSOUT   DD SYSOUT=*"), "DD after proc must be present");

        // Every output line must start with // or /*
        for (String line : result.split("\\n")) {
            if (!line.trim().isEmpty()) {
                assertTrue(line.startsWith("//") || line.startsWith("/*"),
                        "Line must start with // or /*: " + line);
            }
        }
    }

    @Test
    void nonSpoolContentReturnedUnchanged() {
        String plain = "This is just plain text.\nNo JCL here.\nLine three.";
        String result = JobDetailTab.normalizeJesSpoolJcl(plain);
        assertEquals(plain, result);
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertNull(JobDetailTab.normalizeJesSpoolJcl(null));
        assertEquals("", JobDetailTab.normalizeJesSpoolJcl(""));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Detection heuristic
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void looksLikeJesSpool_detectsTypicalFormat() {
        String[] lines = {
                "        1 //TESTJOB  JOB (ACCT),'PGMR'",
                "          //             MSGCLASS=S",
                "        2 //STEP1    EXEC PGM=IEFBR14",
                "        3 //DD1      DD DSN=MY.DATA,DISP=SHR"
        };
        assertTrue(JobDetailTab.looksLikeJesSpool(lines));
    }

    @Test
    void looksLikeJesSpool_rejectsPlainText() {
        String[] lines = {
                "Hello World",
                "This is plain text",
                "No JCL here at all"
        };
        assertFalse(JobDetailTab.looksLikeJesSpool(lines));
    }

    @Test
    void looksLikeJesSpool_rejectsCleanJcl() {
        String[] lines = {
                "//TESTJOB  JOB (ACCT),'PGMR'",
                "//             MSGCLASS=S",
                "//STEP1    EXEC PGM=IEFBR14",
                "//DD1      DD DSN=MY.DATA,DISP=SHR"
        };
        assertFalse(JobDetailTab.looksLikeJesSpool(lines));
    }

    /**
     * Entire Operations (Software AG) prepends 90+ comment lines to JOB cards.
     * The second numbered statement only appears well past line 50.
     * Detection must scan deep enough to find it.
     */
    @Test
    void looksLikeJesSpool_detectsLongJobHeader() {
        // Build a spool with stmt 1 at line 0, then 95 comment/continuation lines,
        // then stmt 2 and 3 — mimics the ABHOLA pattern from Entire Operations.
        List<String> lineList = new ArrayList<>();
        lineList.add("        1 //MYJOB    JOB (60760,F29,1,9999),                                      J0310702");
        lineList.add("          //         'MYJOB-47358',                                                       ");
        // Add 90 comment lines (like Entire Operations symbol table dump)
        for (int i = 0; i < 90; i++) {
            lineList.add("          //* Symbol line " + i + "                                                           ");
        }
        lineList.add("        2 //OUTFEHL1 OUTPUT DEST=U0011,CLASS=C,                                           ");
        lineList.add("        3 //STEP1    EXEC PGM=WAIT4END,PARM='MYJOB'                                       ");

        String[] lines = lineList.toArray(new String[0]);
        assertTrue(JobDetailTab.looksLikeJesSpool(lines),
                "Must detect spool format even when numbered JCL lines are spread across 100+ lines");
    }

    /**
     * Even a single numbered JCL line + PROC expansion (XX) and IEFC messages
     * should be detected as spool format.
     */
    @Test
    void looksLikeJesSpool_detectsWithSecondaryEvidence() {
        // Only 1 numbered JCL line but XX and IEFC provide additional evidence
        List<String> lineList = new ArrayList<>();
        lineList.add("        1 //MYJOB    JOB (ACCT),'PGMR',CLASS=A                                           ");
        // Many comments before the PROC expansion
        for (int i = 0; i < 60; i++) {
            lineList.add("          //* Comment line " + i + "                                                         ");
        }
        lineList.add("        2 //STEP1    EXEC MYPROC                                                          ");
        lineList.add("        3 XXSTEP1    EXEC PGM=MYPGM,REGION=0M                                             ");
        lineList.add("          IEFC653I SUBSTITUTION JCL - PGM=MYPGM,REGION=0M                                 ");
        lineList.add("        4 //STEPLIB  DD DSN=MY.LOAD,DISP=SHR                                              ");

        String[] lines = lineList.toArray(new String[0]);
        assertTrue(JobDetailTab.looksLikeJesSpool(lines));
    }

    /**
     * A single numbered JCL line combined with XX/IEFC evidence should
     * be detected early, even if only 1 numbered line has been seen so far.
     */
    @Test
    void looksLikeJesSpool_singleNumberedLineWithXxAndIefc() {
        String[] lines = {
                "        1 //MYJOB    JOB (ACCT),'PGMR'",
                "          //         CLASS=A",
                "        2 //STEP1    EXEC MYPROC",
                "        3 XXSTEP1    EXEC PGM=IEFBR14,REGION=0M",
                "          IEFC653I SUBSTITUTION JCL - PGM=IEFBR14,REGION=0M",
        };
        assertTrue(JobDetailTab.looksLikeJesSpool(lines),
                "1 numbered + XX + IEFC = strong spool evidence");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DD * / DD DATA detection
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void isInstreamDataDd_detects() {
        assertTrue(JobDetailTab.isInstreamDataDd("//SYSIN DD *"));
        assertTrue(JobDetailTab.isInstreamDataDd("//SYSIN    DD  *"));
        assertTrue(JobDetailTab.isInstreamDataDd("//SYSIN DD *,DLM=XX"));
        assertTrue(JobDetailTab.isInstreamDataDd("//SYSIN DD DATA"));
        assertFalse(JobDetailTab.isInstreamDataDd("//SYSOUT DD SYSOUT=*"));
        assertFalse(JobDetailTab.isInstreamDataDd("//STEPLIB DD DSN=MY.LOAD,DISP=SHR"));
        assertFalse(JobDetailTab.isInstreamDataDd("//STEPLIB DD"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Complex real-world-style spool excerpt (all identifiers fictional)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Realistic multi-step JCL spool with PROC expansion, variable substitution,
     * multi-line IEFC messages, and DD overrides.
     * All job names, DSNs, user IDs and program names are fictional.
     */
    private static final String COMPLEX_SPOOL =
            "        1 //FIN042LD JOB (12345,A01,1,999),BATCHLDR,CLASS=A,                      J1234567\n" +
            "          //             MSGCLASS=S,NOTIFY=FIN042,REGION=5000K                            \n" +
            "          //* -------------------------------------------------------------------         \n" +
            "        2 //RPTOUT   OUTPUT CLASS=S,PAGEDEF=H128,FORMDEF=H128,PRMODE=PAGE,DEST=R0099      \n" +
            "        3 //CLEANUP  EXEC PGM=DSNDEL                                                      \n" +
            "        4 //SYSIN    DD *                                                                 \n" +
            "          //*                                                                             \n" +
            "        5 //SORT1     EXEC  SORTD                                                         \n" +
            "        6 XXSORT     EXEC  PGM=ICEMAN                                                     \n" +
            "        7 //SYSOUT      DD  SYSOUT=*                                                      \n" +
            "          X/SYSOUT   DD  SYSOUT=*                                                         \n" +
            "        8 //APPSTEP  EXEC MYPROC1,                                                        \n" +
            "          //         ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON MYLIB-M;XBAT0P)'                 \n" +
            "        9 XXMYPROC1  PROC PGMNAME=MYPROG01                                                \n" +
            "       10 XXSTEP1    EXEC PGM=&PGMNAME,REGION=0M,                                         \n" +
            "          XX PARM=('DBID=1,',                                                             \n" +
            "          XX     '&ZPARM')                                                                \n" +
            "          IEFC653I SUBSTITUTION JCL - PGM=MYPROG01,REGION=0M,PARM=('DBID=1,','MADIO=0,MAXCL=0,STACK=(LOGON \n" +
            "          MYLIB-M;XBAT0P)')                                                               \n" +
            "       11 //STEPLIB  DD                                                                   \n" +
            "          X/STEPLIB  DD DSN=&NINDX..LOAD,DISP=SHR                                         \n" +
            "          IEFC653I SUBSTITUTION JCL - DSN=VENDOR.NAT.PROD.LOAD,DISP=SHR                   \n" +
            "       12 XXSORTLIB  DD DSN=&SRTLIB,DISP=SHR                                              \n" +
            "          IEFC653I SUBSTITUTION JCL - DSN=SYS1.SORTLIB,DISP=SHR                           \n" +
            "       13 XXDDSORTIN DD DISP=(,DELETE,DELETE),DSN=&&SORT,UNIT=(SYSDA,,DEFER),             \n" +
            "          XX            DCB=RECFM=FB,SPACE=(CYL,(&SRTSPCE,&SRTSPCE))                      \n" +
            "          IEFC653I SUBSTITUTION JCL - DISP=(,DELETE,DELETE),DSN=&&SORT,SPACE=(CYL,(50,     \n" +
            "          50))                                                                            \n" +
            "       14 //CMPRINT  DD SYSOUT=*                                                          \n" +
            "          X/CMPRINT  DD SYSOUT=&SYSOUT                                                    \n" +
            "          IEFC653I SUBSTITUTION JCL - SYSOUT=*                                            \n" +
            "       15 //SYSIN   DD *                                                                  \n";

    @Test
    void normalizeComplexSpool() {
        String result = JobDetailTab.normalizeJesSpoolJcl(COMPLEX_SPOOL);

        // ── JOB card: JES job number must be stripped ──
        assertFalse(result.contains("J1234567"), "JES job number must be removed from JOB card");
        assertTrue(result.contains("//FIN042LD JOB (12345,A01,1,999),BATCHLDR,CLASS=A,"),
                "JOB card present without trailing job number");

        // ── User JCL must be present ──
        assertTrue(result.contains("//CLEANUP  EXEC PGM=DSNDEL"), "EXEC present");
        assertTrue(result.contains("//SORT1     EXEC  SORTD"), "PROC call present");
        assertTrue(result.contains("//APPSTEP  EXEC MYPROC1,"), "PROC call present");
        assertTrue(result.contains("ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON MYLIB-M;XBAT0P)'"),
                "ZPARM continuation present");
        assertTrue(result.contains("//STEPLIB  DD"), "STEPLIB override present");
        assertTrue(result.contains("//CMPRINT  DD SYSOUT=*"), "CMPRINT override present");

        // ── PROC expansion, overrides, JES messages must be gone ──
        assertFalse(result.contains("XXSORT"), "No XX PROC expansion");
        assertFalse(result.contains("XXMYPROC1"), "No XX PROC lines");
        assertFalse(result.contains("X/SYSOUT"), "No X/ overridden lines");
        assertFalse(result.contains("X/CMPRINT"), "No X/ overridden lines");
        assertFalse(result.contains("IEFC653I"), "No IEFC messages");

        // ── Critical: multi-line IEFC fragments must not leak ──
        assertFalse(result.contains("MYLIB-M;XBAT0P)')"),
                "IEFC653I continuation fragment must NOT leak into output");
        assertFalse(result.contains("50))"),
                "IEFC653I continuation '50))' must NOT leak into output");

        // ── Every output line must start with // or /* ──
        for (String line : result.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                assertTrue(
                        trimmed.startsWith("//") || trimmed.startsWith("/*"),
                        "Line should start with // or /*: '" + trimmed + "'"
                );
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JOB card JES job number stripping
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void stripJobNumber_removesJesNumber() {
        assertEquals("//MYJOB  JOB (ACCT),'PGMR',CLASS=A,",
                JobDetailTab.stripJobNumber("//MYJOB  JOB (ACCT),'PGMR',CLASS=A,                      J1234567"));
    }

    @Test
    void stripJobNumber_leavesNonJobCards() {
        String ddLine = "//STEPLIB DD DSN=MY.LOAD,DISP=SHR";
        assertEquals(ddLine, JobDetailTab.stripJobNumber(ddLine));
    }

    @Test
    void stripJobNumber_leavesJobCardWithoutNumber() {
        String jobLine = "//MYJOB  JOB (ACCT),'PGMR',CLASS=A,";
        assertEquals(jobLine, JobDetailTab.stripJobNumber(jobLine));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Instream data: preserved when present, graceful when JES2 omits it
    // ═══════════════════════════════════════════════════════════════════

    /**
     * JES2 typically does NOT include instream data in JESJCL spool output.
     * The data is stored in separate spool files. When JES2 omits it, the
     * normalizer simply sees the next // line and returns to NORMAL state.
     * This is correct behaviour — the normalizer cannot reconstruct data
     * that is not present in the spool input.
     */
    @Test
    void normalizeHandlesJes2OmittedInstreamData() {
        // Typical JES2 JESJCL: after DD * the instream data is missing,
        // the next line is a JCL comment or the next statement.
        String spool =
                "        1 //MYJOB    JOB (ACCT),'PGMR'\n" +
                "        2 //STEP1    EXEC PGM=DSNDEL\n" +
                "        3 //SYSIN    DD *\n" +
                "          //*\n" +
                "        4 //STEP2    EXEC PGM=IDCAMS\n" +
                "        5 //SYSIN    DD  *\n" +
                "          //*\n" +
                "        6 //SORT1    EXEC SORTD\n";

        String result = JobDetailTab.normalizeJesSpoolJcl(spool);

        // The //* lines are JCL comments (start with //) that end the
        // instream area. They must be kept in the output.
        assertTrue(result.contains("//SYSIN    DD *"));
        assertTrue(result.contains("//*"));
        assertTrue(result.contains("//STEP2    EXEC PGM=IDCAMS"));
        assertTrue(result.contains("//SORT1    EXEC SORTD"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  sanitizeSpoolContent — control character removal
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void sanitizeRemovesNullBytes() {
        String input = "Hello\0World\0!";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertEquals("HelloWorld!", result);
    }

    @Test
    void sanitizeConvertsFormFeedToNewline() {
        String input = "Page1\fPage2\fPage3";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertEquals("Page1\nPage2\nPage3", result);
    }

    @Test
    void sanitizeStripsControlCharacters() {
        // SOH (0x01), STX (0x02), BEL (0x07), ESC (0x1B)
        String input = "A\u0001B\u0002C\u0007D\u001BE";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertEquals("ABCDE", result);
    }

    @Test
    void sanitizePreservesStandardWhitespace() {
        String input = "Line1\nLine2\r\nLine3\tTabbed";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertEquals(input, result);
    }

    @Test
    void sanitizePassesThroughCleanContent() {
        String input = "//MYJOB JOB (ACCT),'PGMR',CLASS=A\n//STEP1 EXEC PGM=IEFBR14";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertEquals(input, result);
    }

    @Test
    void sanitizeHandlesNullAndEmpty() {
        assertNull(JobDetailTab.sanitizeSpoolContent(null));
        assertEquals("", JobDetailTab.sanitizeSpoolContent(""));
    }

    @Test
    void sanitizeHandlesTypicalJesmsglgWithFormFeeds() {
        // Simulates JESMSGLG with ASA carriage control translated to form feeds
        String input = "\f J E S 2  J O B  L O G\n" +
                " 14.23.38 JOB12345  IRR010I  USERID USR01\n" +
                "\f14.23.38 JOB12345  $HASP373 TESTJOB  STARTED\n";
        String result = JobDetailTab.sanitizeSpoolContent(input);
        assertFalse(result.contains("\f"), "Form feeds must be removed");
        assertTrue(result.contains("J E S 2  J O B  L O G"), "Content after form feed must be preserved");
        assertTrue(result.contains("$HASP373"), "All lines must be preserved");
    }
}
