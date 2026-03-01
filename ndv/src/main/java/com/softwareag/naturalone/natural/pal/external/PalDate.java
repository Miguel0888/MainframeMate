package com.softwareag.naturalone.natural.pal.external;

import java.io.Serializable;

/**
 * Value object for Natural date/time (tag, monat, jahr, stunde, minute).
 */
public final class PalDate implements Serializable {
    private static final long serialVersionUID = 3714798978386450891L;

    private int tag;
    private int monat;
    private int jahr;
    private int stunde;
    private int minute;

    public PalDate() {
    }

    public PalDate(int day, int month, int year, int hour, int minute) {
        this.tag = day;
        this.monat = month;
        this.jahr = year;
        this.stunde = hour;
        this.minute = minute;
    }

    public int getDay() { return tag; }
    public int getMonth() { return monat; }
    public int getYear() { return jahr; }
    public int getHour() { return stunde; }
    public int getMinute() { return minute; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof PalDate)) return false;
        PalDate other = (PalDate) obj;
        return this.tag == other.tag
                && this.monat == other.monat
                && this.jahr == other.jahr
                && this.stunde == other.stunde
                && this.minute == other.minute;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + tag;
        result = 31 * result + stunde;
        result = 31 * result + minute;
        result = 31 * result + monat;
        result = 31 * result + jahr;
        return result;
    }

    @Override
    public String toString() {
        return String.format("%02d/%02d/%04d %02d:%02d", tag, monat, jahr, stunde, minute);
    }
}
