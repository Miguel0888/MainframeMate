package com.softwareag.naturalone.natural.pal.external;

import java.io.Serializable;

/**
 * Value object for Natural date/time (day, month, year, hour, minute).
 */
public final class PalDate implements Serializable {
    private static final long serialVersionUID = 3714798978386450891L;

    private int day;
    private int month;
    private int year;
    private int hour;
    private int minute;

    public PalDate() {
    }

    public PalDate(int day, int month, int year, int hour, int minute) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.hour = hour;
        this.minute = minute;
    }

    public int getDay() { return day; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof PalDate)) return false;
        PalDate other = (PalDate) obj;
        return this.day == other.day
                && this.month == other.month
                && this.year == other.year
                && this.hour == other.hour
                && this.minute == other.minute;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + day;
        result = 31 * result + hour;
        result = 31 * result + minute;
        result = 31 * result + month;
        result = 31 * result + year;
        return result;
    }

    @Override
    public String toString() {
        return String.format("%02d/%02d/%04d %02d:%02d", day, month, year, hour, minute);
    }
}
