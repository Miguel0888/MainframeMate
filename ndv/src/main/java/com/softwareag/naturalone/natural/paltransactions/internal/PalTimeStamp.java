package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.PalDate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PalTimeStamp {

    public static final int FLAG_CHECK = 1;
    public static final int FLAG_GET = 2;
    public static final int FLAG_NOOPERATION = 4;

    private static final Pattern COMPACT_PATTERN = Pattern.compile(
            "([0-9]{4})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})?([0-9])?( .+)?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DISPLAY_PATTERN = Pattern.compile(
            "([0-9]{4})-([0-9]{2})-([0-9]{2}) ([0-9]{2}):([0-9]{2})(:[0-9]{2})?(.[0-9])?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private int flags;
    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;
    private int tenth;
    private String user;

    private PalTimeStamp() {
    }

    private static int normalizeYear(int year) {
        if (year < 70) {
            return year + 2000;
        } else if (year < 100) {
            return year + 1900;
        }
        return year;
    }

    // ── Factory methods ────────────────────────────────────────────────

    public static PalTimeStamp get() {
        PalTimeStamp ts = new PalTimeStamp();
        ts.flags = 0;
        ts.year = 0;
        ts.month = 0;
        ts.day = 0;
        ts.hour = 0;
        ts.minute = 0;
        ts.second = 0;
        ts.tenth = 0;
        ts.user = "";
        return ts;
    }

    public static PalTimeStamp get(int flags) {
        PalTimeStamp ts = get();
        ts.flags = flags;
        return ts;
    }

    public static PalTimeStamp get(int flags, int year, int month, int day,
                                   int hour, int minute, int second, int tenth, String user) {
        PalTimeStamp ts = new PalTimeStamp();
        ts.flags = flags;
        ts.year = normalizeYear(year);
        ts.month = month;
        ts.day = day;
        ts.hour = hour;
        ts.minute = minute;
        ts.second = second;
        ts.tenth = tenth;
        ts.user = user;
        return ts;
    }

    public static PalTimeStamp get(String text) {
        return get(0, text);
    }

    public static PalTimeStamp get(int flags, String text) {
        try {
            if (text == null || text.isEmpty()) {
                return null;
            }
            if (text.startsWith("timecheck:")) {
                text = text.substring("timecheck:".length());
            }

            // Versuch 1: Kompaktformat
            Matcher m = COMPACT_PATTERN.matcher(text);
            if (m.matches()) {
                PalTimeStamp ts = new PalTimeStamp();
                ts.flags = flags;
                ts.year = normalizeYear(Integer.parseInt(m.group(1)));
                ts.month = Integer.parseInt(m.group(2));
                ts.day = Integer.parseInt(m.group(3));
                ts.hour = Integer.parseInt(m.group(4));
                ts.minute = Integer.parseInt(m.group(5));
                ts.second = m.group(6) != null ? Integer.parseInt(m.group(6)) : -1;
                ts.tenth = m.group(7) != null ? Integer.parseInt(m.group(7)) : -1;
                ts.user = m.group(8) != null ? m.group(8).substring(1) : "";
                return ts;
            }

            // Versuch 2: Anzeigeformat
            Matcher dm = DISPLAY_PATTERN.matcher(text);
            if (dm.matches()) {
                PalTimeStamp ts = new PalTimeStamp();
                ts.flags = flags;
                ts.year = normalizeYear(Integer.parseInt(dm.group(1)));
                ts.month = Integer.parseInt(dm.group(2));
                ts.day = Integer.parseInt(dm.group(3));
                ts.hour = Integer.parseInt(dm.group(4));
                ts.minute = Integer.parseInt(dm.group(5));
                ts.second = dm.group(6) != null ? Integer.parseInt(dm.group(6).substring(1)) : -1;
                ts.tenth = dm.group(7) != null ? Integer.parseInt(dm.group(7).substring(1)) : -1;
                ts.user = "";
                return ts;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static PalTimeStamp get(String text, String user) {
        PalTimeStamp ts = get(0, text);
        if (ts != null) {
            ts.user = user;
        }
        return ts;
    }

    public static PalTimeStamp get(int flags, String text, String user) {
        PalTimeStamp ts = get(flags, text);
        if (ts != null) {
            ts.user = user;
        }
        return ts;
    }

    public static PalTimeStamp get(PalDate datum, String user) {
        return get(0, datum, user);
    }

    public static PalTimeStamp get(int flags, PalDate datum, String user) {
        PalTimeStamp ts = new PalTimeStamp();
        ts.flags = flags;
        ts.year = normalizeYear(datum.getYear());
        ts.month = datum.getMonth();
        ts.day = datum.getDay();
        ts.hour = datum.getHour();
        ts.minute = datum.getMinute();
        ts.second = -1;
        ts.tenth = -1;
        ts.user = user;
        return ts;
    }

    // ── Getters ────────────────────────────────────────────────────────

    public int getFlags() { return flags; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public int getDay() { return day; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public int getSecond() { return second; }
    public int getTenth() { return tenth; }
    public String getUser() { return user; }

    // ── Setter ─────────────────────────────────────────────────────────

    public void setFlags(int flags) {
        this.flags = flags;
    }

    // ── Formatting ─────────────────────────────────────────────────────

    public String getCompactString() {
        if (tenth >= 0) {
            return String.format("%04d%02d%02d%02d%02d%02d%d", year, month, day, hour, minute, second, tenth);
        } else if (second >= 0) {
            return String.format("%04d%02d%02d%02d%02d%02d", year, month, day, hour, minute, second);
        } else {
            return String.format("%04d%02d%02d%02d%02d", year, month, day, hour, minute);
        }
    }

    public String getDisplayString() {
        if (tenth >= 0) {
            return String.format("%04d-%02d-%02d %02d:%02d:%02d.%d", year, month, day, hour, minute, second, tenth);
        } else if (second >= 0) {
            return String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
        } else {
            return String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
        }
    }

    // ── Comparison & Copy ──────────────────────────────────────────────

    public boolean equals(PalTimeStamp other) {
        return getCompactString().equals(other.getCompactString());
    }

    public void copy(PalTimeStamp source) {
        this.flags = source.flags;
        this.year = normalizeYear(source.year);
        this.month = source.month;
        this.day = source.day;
        this.hour = source.hour;
        this.minute = source.minute;
        this.second = source.second;
        this.tenth = source.tenth;
        this.user = source.user;
    }

    // ── Empty check ────────────────────────────────────────────────────

    public boolean isEmpty() {
        return month == 0 || day == 0;
    }

    // ── toString ───────────────────────────────────────────────────────

    @Override
    public String toString() {
        if (flags == 0 && isEmpty()) {
            return "<invalid>";
        }

        StringBuilder sb = new StringBuilder();

        if (flags != 0) {
            if ((flags & FLAG_CHECK) != 0) sb.append("CHECK|");
            if ((flags & FLAG_GET) != 0) sb.append("GET|");
            if ((flags & FLAG_NOOPERATION) != 0) sb.append("NOOPERATION|");
            // Replace last '|' with ':'
            sb.setCharAt(sb.length() - 1, ':');
        }

        if (isEmpty()) {
            sb.append("<empty>");
        } else {
            sb.append(getCompactString());
            if (user != null && !user.isEmpty()) {
                sb.append(" ").append(user);
            }
        }

        return sb.toString();
    }
}
