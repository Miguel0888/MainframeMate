package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.external.PalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class PalDateTest {

    @Test
    void defaultConstructorAllZero() {
        PalDate d = new PalDate();
        assertEquals(0, d.getDay());
        assertEquals(0, d.getMonth());
        assertEquals(0, d.getYear());
        assertEquals(0, d.getHour());
        assertEquals(0, d.getMinute());
    }

    @ParameterizedTest(name = "PalDate({0},{1},{2},{3},{4})")
    @CsvSource({
            "1,  1,  2024, 0,  0",
            "31, 12, 2025, 23, 59",
            "15, 6,  1999, 12, 30",
            "0,  0,  0,    0,  0",
            "-1, -1, -1,   -1, -1",
            "29, 2,  2000, 0,  0",
            "28, 2,  2023, 14, 45",
            "1,  1,  9999, 23, 59",
            "0,  0,  0,    0,  1",
            "0,  0,  0,    1,  0",
            "0,  0,  1,    0,  0",
            "0,  1,  0,    0,  0",
            "1,  0,  0,    0,  0",
    })
    void parameterizedConstructor(int day, int month, int year, int hour, int minute) {
        PalDate d = new PalDate(day, month, year, hour, minute);
        assertEquals(day, d.getDay());
        assertEquals(month, d.getMonth());
        assertEquals(year, d.getYear());
        assertEquals(hour, d.getHour());
        assertEquals(minute, d.getMinute());
    }

    @Nested
    class EqualsContract {
        @Test void reflexive() { PalDate d = new PalDate(1,2,2024,10,30); assertEquals(d, d); }
        @Test void reflexiveDefault() { PalDate d = new PalDate(); assertTrue(d.equals(d)); }
        @Test void symmetric() { PalDate a = new PalDate(1,2,2024,10,30); PalDate b = new PalDate(1,2,2024,10,30); assertEquals(a,b); assertEquals(b,a); }
        @Test void transitive() { PalDate a = new PalDate(5,3,2000,8,0); PalDate b = new PalDate(5,3,2000,8,0); PalDate c = new PalDate(5,3,2000,8,0); assertEquals(a,b); assertEquals(b,c); assertEquals(a,c); }
        @Test void equalsNullReturnsFalse() { PalDate d = new PalDate(1,1,2024,0,0); assertFalse(d.equals(null)); }
        @Test void equalsNullDefault() { PalDate d = new PalDate(); assertFalse(d.equals(null)); }
        @Test void notEqualToNull() { PalDate d = new PalDate(1,1,2024,0,0); assertNotEquals(null, d); }
        @Test void notEqualToDifferentType() { PalDate d = new PalDate(1,1,2024,0,0); assertNotEquals("not a date", d); }
        @Test void notEqualToString() { PalDate d = new PalDate(1,1,2024,0,0); assertFalse(d.equals("01/01/2024 00:00")); }
        @Test void notEqualToInteger() { PalDate d = new PalDate(1,1,2024,0,0); assertFalse(d.equals(42)); }
        @Test void notEqualToObject() { PalDate d = new PalDate(1,1,2024,0,0); assertFalse(d.equals(new Object())); }

        @ParameterizedTest(name = "differs in field: {0}")
        @CsvSource({"day,2,1,2024,10,30","month,1,2,2024,10,30","year,1,1,2025,10,30","hour,1,1,2024,11,30","minute,1,1,2024,10,31"})
        void notEqualWhenFieldDiffers(String f, int d, int m, int y, int h, int min) { assertNotEquals(new PalDate(1,1,2024,10,30), new PalDate(d,m,y,h,min)); }

        @ParameterizedTest(name = "extreme: day={0}")
        @CsvSource({"99,1,2024,10,30","1,99,2024,10,30","1,1,9999,10,30","1,1,2024,99,30","1,1,2024,10,99"})
        void notEqualExtreme(int d, int m, int y, int h, int min) { PalDate base = new PalDate(1,1,2024,10,30); PalDate other = new PalDate(d,m,y,h,min); assertNotEquals(base, other); assertNotEquals(other, base); }

        @Test void equalAllSame() { assertTrue(new PalDate(15,6,2024,14,0).equals(new PalDate(15,6,2024,14,0))); }
        @Test void defaultEqual() { assertEquals(new PalDate(), new PalDate()); }
        @Test void negativeEqual() { assertEquals(new PalDate(-1,-2,-3,-4,-5), new PalDate(-1,-2,-3,-4,-5)); }
        @Test void zeroEqDefault() { assertEquals(new PalDate(), new PalDate(0,0,0,0,0)); }
    }

    @Nested
    class HashCodeContract {
        @Test void equalSameHash() { assertEquals(new PalDate(15,6,2024,14,0).hashCode(), new PalDate(15,6,2024,14,0).hashCode()); }
        @Test void defaultConsistent() { PalDate d = new PalDate(); assertEquals(d.hashCode(), d.hashCode()); }
        @Test void differentHash() { assertNotEquals(new PalDate(1,1,2024,0,0).hashCode(), new PalDate(2,1,2024,0,0).hashCode()); }
        @Test void multipleCallsConsistent() { PalDate d = new PalDate(5,3,2000,8,0); assertEquals(d.hashCode(), d.hashCode()); }
        @Test void negativeHash() { assertEquals(new PalDate(-1,-2,-3,-4,-5).hashCode(), new PalDate(-1,-2,-3,-4,-5).hashCode()); }
        @Test void defaultZeroHash() { assertEquals(new PalDate().hashCode(), new PalDate(0,0,0,0,0).hashCode()); }
        @Test void maxNoOverflow() { new PalDate(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE).hashCode(); }
    }

    @Nested
    class ToStringFormatting {
        @ParameterizedTest(name = "{5}")
        @CsvSource({"1,1,2024,0,0,01/01/2024 00:00","31,12,2025,23,59,31/12/2025 23:59","5,3,999,8,5,05/03/0999 08:05","0,0,0,0,0,00/00/0000 00:00","9,9,9,9,9,09/09/0009 09:09","10,10,10,10,10,10/10/0010 10:10","29,2,2000,0,0,29/02/2000 00:00","1,1,1,0,0,01/01/0001 00:00"})
        void formatsWithPadding(int d, int m, int y, int h, int min, String exp) { assertEquals(exp, new PalDate(d,m,y,h,min).toString()); }
        @Test void defaultToString() { assertEquals("00/00/0000 00:00", new PalDate().toString()); }
        @Test void notNull() { assertNotNull(new PalDate().toString()); }
        @Test void length16() { assertEquals(16, new PalDate(1,1,2024,0,0).toString().length()); }
    }

    @Nested
    class Serialization {
        @Test void implementsSerializable() { assertTrue(Serializable.class.isAssignableFrom(PalDate.class)); }

        @Test void roundtrip() throws Exception {
            PalDate orig = new PalDate(15,6,2024,14,30);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(orig); }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
                PalDate d = (PalDate) ois.readObject();
                assertEquals(orig, d); assertEquals(orig.getDay(), d.getDay()); assertEquals(orig.getMonth(), d.getMonth());
                assertEquals(orig.getYear(), d.getYear()); assertEquals(orig.getHour(), d.getHour()); assertEquals(orig.getMinute(), d.getMinute());
            }
        }

        @Test void roundtripDefault() throws Exception {
            PalDate orig = new PalDate();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(orig); }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { assertEquals(orig, (PalDate) ois.readObject()); }
        }
    }

    @Nested
    class Getters {
        @Test void all() { PalDate d = new PalDate(31,12,2025,23,59); assertAll(()->assertEquals(31,d.getDay()),()->assertEquals(12,d.getMonth()),()->assertEquals(2025,d.getYear()),()->assertEquals(23,d.getHour()),()->assertEquals(59,d.getMinute())); }
    }
}
