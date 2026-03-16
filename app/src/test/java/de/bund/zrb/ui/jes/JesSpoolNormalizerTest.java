package de.bund.zrb.ui.jes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JobDetailTab#normalizeJesSpoolJcl(String)} —
 * the normalizer that converts JES JESJCL spool output into clean,
 * resubmittable JCL.
 */
class JesSpoolNormalizerTest {

    // ── Minimal JES spool snippet for quick unit tests ──────────────

    private static final String MINI_SPOOL =
            "        1 //TESTJOB  JOB (ACCT),'PGMR',CLASS=A,                      J0753875\n" +
            "          //             MSGCLASS=S,NOTIFY=USR01\n" +
            "        2 //STEP1    EXEC PGM=IEFBR14\n" +
            "        3 //DD1      DD DSN=MY.DATA.SET,DISP=SHR\n";

    private static final String MINI_EXPECTED =
            "//TESTJOB  JOB (ACCT),'PGMR',CLASS=A,                      J0753875\n" +
            "//             MSGCLASS=S,NOTIFY=USR01\n" +
            "//STEP1    EXEC PGM=IEFBR14\n" +
            "//DD1      DD DSN=MY.DATA.SET,DISP=SHR";

    @Test
    void normalizeMinimalSpool() {
        String result = JobDetailTab.normalizeJesSpoolJcl(MINI_SPOOL);
        assertEquals(MINI_EXPECTED, result);
    }

    // ── XX / X/ / IEFC line removal ──────────────────────────────────

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

    // ── Detection: non-spool content is returned unchanged ──────────

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

    // ── Detection heuristic ─────────────────────────────────────────

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

    // ── Real-world JESJCL excerpt ───────────────────────────────────

    private static final String REAL_SPOOL_EXCERPT =
            "        1 //KKR097XP JOB (60768,F14,1,999),ZDALADEP,CLASS=A,                      J0753875\n" +
            "          //             MSGCLASS=S,NOTIFY=KKR097,REGION=5000K                            \n" +
            "          //* -------------------------------------------------------------------         \n" +
            "          //*   FUNKTION       LADEN VON DATEIEN IN DIE DB ZABAK                          \n" +
            "          //* -------------------------------------------------------------------         \n" +
            "          //*                                                                             \n" +
            "        2 //AUS OUTPUT CLASS=S,PAGEDEF=H128,FORMDEF=H128,PRMODE=PAGE,DEST=U1043           \n" +
            "          //*                                                                             \n" +
            "        3 //OUTFEHL1 OUTPUT DEST=U0011,CLASS=C,                                           \n" +
            "          //         USERDATA=('TO:ANGELO.FERNANDEZIGLESIAS@ZRB.BUND.DE',                 \n" +
            "          //        'SUBJECT:ZABAK FEHLERMELDUNG ')                                       \n" +
            "          //*                                                                             \n" +
            "        4 //LOESCH   EXEC PGM=DSNDEL                                                      \n" +
            "        5 //SYSIN    DD *                                                                 \n" +
            "          //*                                                                             \n" +
            "        6 //REPRO1   EXEC PGM=IDCAMS                                                      \n" +
            "        7 //SYSPRINT DD  SYSOUT=*                                                         \n" +
            "        8 //DDEIN    DD  DUMMY                                                            \n" +
            "        9 //DDAUS    DD  DSN=KKR097.ABAKUS.JMJ.O4SEQ,                                     \n" +
            "          //             DISP=(,CATLG),                                                   \n" +
            "          //             UNIT=SYSDA,                                                      \n" +
            "          //             SPACE=(CYL,(10,10),RLSE),                                        \n" +
            "          //             LRECL=100,RECFM=FB,DSORG=PS                                      \n" +
            "       10 //SYSIN    DD  *                                                                \n" +
            "          //*                                                                             \n" +
            "       11 //SORT1     EXEC  SORTD                                                         \n" +
            "       12 XXSORT     EXEC  PGM=ICEMAN                                                     \n" +
            "       13 XXSORTLIB  DD  DSN=SYS1.SORTLIB,DISP=SHR                                        \n" +
            "       14 XXSORTDIAG DD  DUMMY                                                            \n" +
            "       15 XXIAMINFO  DD  SYSOUT=*                                                         \n" +
            "       16 //SYSOUT      DD  SYSOUT=*                                                      \n" +
            "          X/SYSOUT   DD  SYSOUT=*                                                         \n" +
            "       17 //SORTIN  DD  DSN=APABB.ZWICL.JMJ.O4SEQ,DISP=SHR                                \n" +
            "       18 //SORTOUT DD  DSN=KKR097.ABAKUS.JMJ.O4SEQ,DISP=SHR                              \n" +
            "       19 //SYSIN  DD  *                                                                  \n" +
            "          //*                                                                             \n" +
            "       20 //NATURAL1 EXEC NAT1,                                                           \n" +
            "          //         ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON ABAK-M;ZDALXX0P)'                \n" +
            "       21 XXNAT1     PROC NATPGM=NAT923BP,        NATURAL PROGRAM NAME                    \n" +
            "          IEFC653I SUBSTITUTION JCL - PGM=NAT923BP,REGION=0M,PARM=('DBID=1,','MADIO=0')   \n";

    @Test
    void normalizeRealWorldExcerpt() {
        String result = JobDetailTab.normalizeJesSpoolJcl(REAL_SPOOL_EXCERPT);

        // Must contain the user's JCL lines
        assertTrue(result.contains("//KKR097XP JOB"), "JOB card present");
        assertTrue(result.contains("//LOESCH   EXEC PGM=DSNDEL"), "EXEC present");
        assertTrue(result.contains("//SORT1     EXEC  SORTD"), "PROC call present");
        assertTrue(result.contains("//NATURAL1 EXEC NAT1,"), "NAT1 PROC call present");
        assertTrue(result.contains("//SYSOUT      DD  SYSOUT=*"), "User override present");
        assertTrue(result.contains("//SORTIN  DD"), "SORTIN DD present");
        assertTrue(result.contains("//SORTOUT DD"), "SORTOUT DD present");

        // Must NOT contain PROC expansion, overrides, or JES messages
        assertFalse(result.contains("XXSORT"), "No XX PROC expansion");
        assertFalse(result.contains("XXSORTLIB"), "No XX lines");
        assertFalse(result.contains("XXNAT1"), "No XX PROC lines");
        assertFalse(result.contains("X/SYSOUT"), "No X/ overridden lines");
        assertFalse(result.contains("IEFC653I"), "No IEFC messages");

        // Lines must start with // or be instream data (no 10-char prefix)
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
}

