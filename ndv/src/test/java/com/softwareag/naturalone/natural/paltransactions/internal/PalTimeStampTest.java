package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.PalDate;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PalTimeStampTest {

    // ── Factory: get() / get(int) ──────────────────────────────────────

    @Nested
    class FactoryNoArgsTest {

        @Test
        void defaultGet() {
            PalTimeStamp ts = PalTimeStamp.get();
            assertNotNull(ts);
            assertEquals(0, ts.getFlags());
            assertEquals(0, ts.getYear());
            assertEquals(0, ts.getMonth());
            assertEquals(0, ts.getDay());
            assertTrue(ts.isEmpty());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 4, 3, 5, 6, 7})
        void getWithFlags(int flags) {
            PalTimeStamp ts = PalTimeStamp.get(flags);
            assertNotNull(ts);
            assertEquals(flags, ts.getFlags());
        }
    }

    // ── Factory: get(int, int, ...) ────────────────────────────────────

    @Nested
    class FactoryFullArgsTest {

        @Test
        void allFieldsSet() {
            PalTimeStamp ts = PalTimeStamp.get(1, 2025, 2, 22, 14, 30, 45, 7, "ADMIN");
            assertNotNull(ts);
            assertEquals(1, ts.getFlags());
            assertEquals(2025, ts.getYear());
            assertEquals(2, ts.getMonth());
            assertEquals(22, ts.getDay());
            assertEquals(14, ts.getHour());
            assertEquals(30, ts.getMinute());
            assertEquals(45, ts.getSecond());
            assertEquals(7, ts.getTenth());
            assertEquals("ADMIN", ts.getUser());
        }

        @Test
        void yearNormalization_below70() {
            PalTimeStamp ts = PalTimeStamp.get(0, 25, 1, 1, 0, 0, 0, 0, "");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
        }

        @Test
        void yearNormalization_70to99() {
            PalTimeStamp ts = PalTimeStamp.get(0, 99, 1, 1, 0, 0, 0, 0, "");
            assertNotNull(ts);
            assertEquals(1999, ts.getYear());
        }

        @Test
        void yearNormalization_70() {
            PalTimeStamp ts = PalTimeStamp.get(0, 70, 1, 1, 0, 0, 0, 0, "");
            assertNotNull(ts);
            assertEquals(1970, ts.getYear());
        }

        @Test
        void yearNormalization_fullYear() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 1, 1, 0, 0, 0, 0, "");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
        }

        @Test
        void negativeSecondAndTenth() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 6, 15, 12, 30, -1, -1, "");
            assertNotNull(ts);
            assertEquals(-1, ts.getSecond());
            assertEquals(-1, ts.getTenth());
        }
    }

    // ── Factory: get(String) – compact format ──────────────────────────

    @Nested
    class FactoryFromStringTest {

        @ParameterizedTest
        @CsvSource({
                "'202502221430', 2025, 2, 22, 14, 30",
                "'20251231235959', 2025, 12, 31, 23, 59",
                "'202001010000', 2020, 1, 1, 0, 0"
        })
        void compactFormat(String input, int year, int month, int day, int hour, int minute) {
            PalTimeStamp ts = PalTimeStamp.get(input);
            assertNotNull(ts);
            assertEquals(year, ts.getYear());
            assertEquals(month, ts.getMonth());
            assertEquals(day, ts.getDay());
            assertEquals(hour, ts.getHour());
            assertEquals(minute, ts.getMinute());
        }

        @Test
        void compactFormatWithSeconds() {
            PalTimeStamp ts = PalTimeStamp.get("20250222143045");
            assertNotNull(ts);
            assertEquals(45, ts.getSecond());
        }

        @Test
        void compactFormatWithTenth() {
            PalTimeStamp ts = PalTimeStamp.get("202502221430457");
            assertNotNull(ts);
            assertEquals(45, ts.getSecond());
            assertEquals(7, ts.getTenth());
        }

        @Test
        void compactFormatWithUser() {
            PalTimeStamp ts = PalTimeStamp.get("202502221430 ADMIN");
            assertNotNull(ts);
            assertEquals("ADMIN", ts.getUser());
        }

        @Test
        void displayFormat() {
            PalTimeStamp ts = PalTimeStamp.get("2025-02-22 14:30");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
            assertEquals(2, ts.getMonth());
            assertEquals(22, ts.getDay());
            assertEquals(14, ts.getHour());
            assertEquals(30, ts.getMinute());
        }

        @Test
        void displayFormatWithSeconds() {
            PalTimeStamp ts = PalTimeStamp.get("2025-02-22 14:30:45");
            assertNotNull(ts);
            assertEquals(45, ts.getSecond());
        }

        @Test
        void displayFormatWithTenth() {
            PalTimeStamp ts = PalTimeStamp.get("2025-02-22 14:30:45.7");
            assertNotNull(ts);
            assertEquals(45, ts.getSecond());
            assertEquals(7, ts.getTenth());
        }

        @Test
        void invalidStringReturnsNull() {
            PalTimeStamp ts = PalTimeStamp.get("not-a-timestamp");
            assertNull(ts);
        }

        @Test
        void emptyStringReturnsNull() {
            PalTimeStamp ts = PalTimeStamp.get("");
            assertNull(ts);
        }

        @Test
        void timecheckPrefix() {
            PalTimeStamp ts = PalTimeStamp.get("timecheck:202502221430");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
        }
    }

    // ── Factory: get(int, String) ──────────────────────────────────────

    @Nested
    class FactoryFlagsAndStringTest {

        @Test
        void flagsPreserved() {
            PalTimeStamp ts = PalTimeStamp.get(3, "202502221430");
            assertNotNull(ts);
            assertEquals(3, ts.getFlags());
        }

        @Test
        void timecheckPrefixStripped() {
            PalTimeStamp ts = PalTimeStamp.get(1, "timecheck:202502221430");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
        }

        @Test
        void invalidReturnsNull() {
            PalTimeStamp ts = PalTimeStamp.get(0, "invalid");
            assertNull(ts);
        }
    }

    // ── Factory: get(String, String) – with user ───────────────────────

    @Nested
    class FactoryStringWithUserTest {

        @Test
        void userSet() {
            PalTimeStamp ts = PalTimeStamp.get("202502221430", "TESTUSER");
            assertNotNull(ts);
            assertEquals("TESTUSER", ts.getUser());
        }

        @Test
        void timecheckWithUser() {
            PalTimeStamp ts = PalTimeStamp.get("timecheck:202502221430", "U1");
            assertNotNull(ts);
            assertEquals("U1", ts.getUser());
        }

        @Test
        void invalidReturnsNull() {
            PalTimeStamp ts = PalTimeStamp.get("bad", "USER");
            assertNull(ts);
        }
    }

    // ── Factory: get(int, String, String) ──────────────────────────────

    @Nested
    class FactoryFlagsStringUserTest {

        @Test
        void allSet() {
            PalTimeStamp ts = PalTimeStamp.get(2, "202502221430", "ADM");
            assertNotNull(ts);
            assertEquals(2, ts.getFlags());
            assertEquals("ADM", ts.getUser());
        }

        @Test
        void timecheckStripped() {
            PalTimeStamp ts = PalTimeStamp.get(1, "timecheck:202502221430", "X");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
            assertEquals("X", ts.getUser());
        }
    }

    // ── Factory: get(PalDate, String) ──────────────────────────────────

    @Nested
    class FactoryPalDateTest {

        @Test
        void fromPalDate() {
            PalDate pd = new PalDate(22, 2, 2025, 14, 30);
            PalTimeStamp ts = PalTimeStamp.get(pd, "USER1");
            assertNotNull(ts);
            assertEquals(2025, ts.getYear());
            assertEquals(2, ts.getMonth());
            assertEquals(22, ts.getDay());
            assertEquals(14, ts.getHour());
            assertEquals(30, ts.getMinute());
            assertEquals(-1, ts.getSecond());
            assertEquals(-1, ts.getTenth());
            assertEquals("USER1", ts.getUser());
        }

        @Test
        void fromPalDateWithFlags() {
            PalDate pd = new PalDate(1, 1, 2020, 0, 0);
            PalTimeStamp ts = PalTimeStamp.get(3, pd, "ADM");
            assertNotNull(ts);
            assertEquals(3, ts.getFlags());
            assertEquals("ADM", ts.getUser());
        }
    }

    // ── getCompactString ───────────────────────────────────────────────

    @Nested
    class CompactStringTest {

        @Test
        void withoutSecondsOrTenth() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, -1, -1, "");
            assertEquals("202502221430", ts.getCompactString());
        }

        @Test
        void withSeconds() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 45, -1, "");
            assertEquals("20250222143045", ts.getCompactString());
        }

        @Test
        void withSecondsAndTenth() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 45, 7, "");
            assertEquals("202502221430457", ts.getCompactString());
        }
    }

    // ── getDisplayString ───────────────────────────────────────────────

    @Nested
    class DisplayStringTest {

        @Test
        void withoutSecondsOrTenth() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, -1, -1, "");
            assertEquals("2025-02-22 14:30", ts.getDisplayString());
        }

        @Test
        void withSeconds() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 45, -1, "");
            assertEquals("2025-02-22 14:30:45", ts.getDisplayString());
        }

        @Test
        void withSecondsAndTenth() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 45, 7, "");
            assertEquals("2025-02-22 14:30:45.7", ts.getDisplayString());
        }
    }

    // ── Roundtrip: compact string → parse → compact string ─────────

    @Nested
    class RoundtripTest {

        @ParameterizedTest
        @CsvSource({
                "'202502221430'",
                "'20250222143045'",
                "'202502221430457'"
        })
        void compactRoundtrip(String compact) {
            PalTimeStamp ts = PalTimeStamp.get(compact);
            assertNotNull(ts);
            assertEquals(compact, ts.getCompactString());
        }
    }

    // ── equals ─────────────────────────────────────────────────────────

    @Nested
    class EqualsTest {

        @Test
        void equalTimestamps() {
            PalTimeStamp a = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 0, 0, "");
            PalTimeStamp b = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 0, 0, "");
            assertTrue(a.equals(b));
        }

        @Test
        void differentTimestamps() {
            PalTimeStamp a = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, 0, 0, "");
            PalTimeStamp b = PalTimeStamp.get(0, 2025, 2, 22, 14, 31, 0, 0, "");
            assertFalse(a.equals(b));
        }

        @Test
        void equalsDifferentFlags_sameTime() {
            PalTimeStamp a = PalTimeStamp.get(1, 2025, 2, 22, 14, 30, 0, 0, "");
            PalTimeStamp b = PalTimeStamp.get(2, 2025, 2, 22, 14, 30, 0, 0, "");
            // equals compares compact string (time only), not flags
            assertTrue(a.equals(b));
        }
    }

    // ── copy ───────────────────────────────────────────────────────────

    @Nested
    class CopyTest {

        @Test
        void copiesAllFields() {
            PalTimeStamp src = PalTimeStamp.get(3, 2025, 6, 15, 12, 0, 30, 5, "USR");
            PalTimeStamp dst = PalTimeStamp.get();
            dst.copy(src);
            assertEquals(src.getFlags(), dst.getFlags());
            assertEquals(src.getYear(), dst.getYear());
            assertEquals(src.getMonth(), dst.getMonth());
            assertEquals(src.getDay(), dst.getDay());
            assertEquals(src.getHour(), dst.getHour());
            assertEquals(src.getMinute(), dst.getMinute());
            assertEquals(src.getSecond(), dst.getSecond());
            assertEquals(src.getTenth(), dst.getTenth());
            assertEquals(src.getUser(), dst.getUser());
        }
    }

    // ── isEmpty ────────────────────────────────────────────────────────

    @Nested
    class IsEmptyTest {

        @Test
        void emptyWhenMonthZero() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 0, 15, 12, 0, 0, 0, "");
            assertTrue(ts.isEmpty());
        }

        @Test
        void emptyWhenDayZero() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 6, 0, 12, 0, 0, 0, "");
            assertTrue(ts.isEmpty());
        }

        @Test
        void notEmptyWhenBothSet() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 6, 15, 12, 0, 0, 0, "");
            assertFalse(ts.isEmpty());
        }

        @Test
        void defaultIsEmpty() {
            PalTimeStamp ts = PalTimeStamp.get();
            assertTrue(ts.isEmpty());
        }
    }

    // ── toString ───────────────────────────────────────────────────────

    @Nested
    class ToStringTest {

        @Test
        void emptyWithNoFlags() {
            PalTimeStamp ts = PalTimeStamp.get();
            assertEquals("<invalid>", ts.toString());
        }

        @Test
        void emptyWithCheckFlag() {
            PalTimeStamp ts = PalTimeStamp.get(1);
            String s = ts.toString();
            assertTrue(s.contains("CHECK"));
            assertTrue(s.contains("<empty>"));
        }

        @Test
        void emptyWithGetFlag() {
            PalTimeStamp ts = PalTimeStamp.get(2);
            assertTrue(ts.toString().contains("GET"));
        }

        @Test
        void emptyWithNoopFlag() {
            PalTimeStamp ts = PalTimeStamp.get(4);
            assertTrue(ts.toString().contains("NOOPERATION"));
        }

        @Test
        void nonEmptyWithoutFlags() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, -1, -1, "");
            String s = ts.toString();
            assertTrue(s.contains("202502221430"));
        }

        @Test
        void nonEmptyWithUser() {
            PalTimeStamp ts = PalTimeStamp.get(0, 2025, 2, 22, 14, 30, -1, -1, "ADMIN");
            String s = ts.toString();
            assertTrue(s.contains("ADMIN"));
        }

        @Test
        void combinedFlags() {
            PalTimeStamp ts = PalTimeStamp.get(3); // CHECK | GET
            String s = ts.toString();
            assertTrue(s.contains("CHECK"));
            assertTrue(s.contains("GET"));
        }

        @Test
        void nonEmptyWithFlags() {
            PalTimeStamp ts = PalTimeStamp.get(1, 2025, 2, 22, 14, 30, -1, -1, "");
            String s = ts.toString();
            assertTrue(s.contains("CHECK"));
            assertTrue(s.contains("202502221430"));
        }
    }

    // ── setFlags ───────────────────────────────────────────────────────

    @Nested
    class SetFlagsTest {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 4, 7, -1})
        void setAndGet(int flags) {
            PalTimeStamp ts = PalTimeStamp.get();
            ts.setFlags(flags);
            assertEquals(flags, ts.getFlags());
        }
    }
}

