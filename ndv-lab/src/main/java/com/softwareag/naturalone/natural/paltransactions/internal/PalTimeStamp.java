package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.external.PalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PalTimeStamp {
   private static final Pattern COMPACT_PATTERN;
   private static final Pattern DISPLAY_PATTERN;
   private int flags;
   private int year;
   private int month;
   private int day;
   private int hour;
   private int minute;
   private int second;
   private int tenth;
   private String user;
   public static final int FLAG_CHECK = 1;
   public static final int FLAG_GET = 2;
   public static final int FLAG_NOOPERATION = 4;

   static {
      COMPACT_PATTERN = Pattern.compile("([0-9]{4})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})([0-9]{2})?([0-9])?( .+)?", 66);
      DISPLAY_PATTERN = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2}) ([0-9]{2}):([0-9]{2})(:[0-9]{2})?(.[0-9])?", 66);
   }

   private static PalTimeStamp convertCompactTimeString(int var0, String var1) {
      try {
         Matcher var2 = COMPACT_PATTERN.matcher(var1);
         if (!var2.matches()) {
            return null;
         } else {
            int var3 = Integer.valueOf(var2.group(1));
            int var4 = Integer.valueOf(var2.group(2));
            int var5 = Integer.valueOf(var2.group(3));
            int var6 = Integer.valueOf(var2.group(4));
            int var7 = Integer.valueOf(var2.group(5));
            int var8 = -1;
            int var9 = -1;
            String var10 = "";
            if (var2.group(6) != null) {
               var8 = Integer.valueOf(var2.group(6));
            }

            if (var2.group(7) != null) {
               var9 = Integer.valueOf(var2.group(7));
            }

            if (var2.group(8) != null) {
               var10 = var2.group(8).substring(1);
            }

            return new PalTimeStamp(var0, var3, var4, var5, var6, var7, var8, var9, var10);
         }
      } catch (Exception var11) {
         return null;
      }
   }

   private static PalTimeStamp convertDisplayTimeString(int var0, String var1) {
      try {
         Matcher var2 = DISPLAY_PATTERN.matcher(var1);
         if (!var2.matches()) {
            return null;
         } else {
            int var3 = Integer.valueOf(var2.group(1));
            int var4 = Integer.valueOf(var2.group(2));
            int var5 = Integer.valueOf(var2.group(3));
            int var6 = Integer.valueOf(var2.group(4));
            int var7 = Integer.valueOf(var2.group(5));
            int var8 = -1;
            int var9 = -1;
            if (var2.group(6) != null) {
               var8 = Integer.valueOf(var2.group(6).substring(1));
            }

            if (var2.group(7) != null) {
               var9 = Integer.valueOf(var2.group(7).substring(1));
            }

            return new PalTimeStamp(var0, var3, var4, var5, var6, var7, var8, var9, "");
         }
      } catch (Exception var10) {
         return null;
      }
   }

   private PalTimeStamp(int var1) {
      this.flags = 0;
      this.year = 0;
      this.month = 0;
      this.day = 0;
      this.hour = 0;
      this.minute = 0;
      this.second = 0;
      this.tenth = 0;
      this.user = "";
      this.setFlags(var1);
   }

   private PalTimeStamp(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, String var9) {
      this(var1);
      this.setYear(var2);
      this.setMonth(var3);
      this.setDay(var4);
      this.setHour(var5);
      this.setMinute(var6);
      this.setSecond(var7);
      this.setTenth(var8);
      this.setUser(var9);
   }

   private void setYear(int var1) {
      try {
         this.year = var1 < 100 ? (var1 < 70 ? var1 + 2000 : var1 + 1900) : var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setMonth(int var1) {
      try {
         this.month = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setDay(int var1) {
      try {
         this.day = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setHour(int var1) {
      try {
         this.hour = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setMinute(int var1) {
      try {
         this.minute = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setSecond(int var1) {
      try {
         this.second = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setTenth(int var1) {
      try {
         this.tenth = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   private void setUser(String var1) {
      try {
         this.user = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   public static PalTimeStamp get() {
      return get(0);
   }

   public static PalTimeStamp get(int var0) {
      try {
         return new PalTimeStamp(var0);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public static PalTimeStamp get(int var0, int var1, int var2, int var3, int var4, int var5, int var6, int var7, String var8) {
      try {
         return new PalTimeStamp(var0, var1, var2, var3, var4, var5, var6, var7, var8);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var9) {
         return null;
      }
   }

   public static PalTimeStamp get(String var0) {
      try {
         return get(0, var0);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public static PalTimeStamp get(int var0, String var1) {
      if (var1.startsWith("timecheck:")) {
         var1 = var1.substring("timecheck:".length());
      }

      PalTimeStamp var2 = convertCompactTimeString(var0, var1);
      if (var2 == null) {
         var2 = convertDisplayTimeString(var0, var1);
      }

      return var2;
   }

   public static PalTimeStamp get(String var0, String var1) {
      try {
         return get(0, (String)var0, var1);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
         return null;
      }
   }

   public static PalTimeStamp get(int var0, String var1, String var2) {
      if (var1.startsWith("timecheck:")) {
         var1 = var1.substring("timecheck:".length());
      }

      PalTimeStamp var3 = convertCompactTimeString(var0, var1);
      if (var3 == null) {
         var3 = convertDisplayTimeString(var0, var1);
      }

      if (var3 != null) {
         var3.setUser(var2);
      }

      return var3;
   }

   public static PalTimeStamp get(PalDate var0, String var1) {
      try {
         return get(0, var0.getYear(), var0.getMonth(), var0.getDay(), var0.getHour(), var0.getMinute(), -1, -1, var1);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
         return null;
      }
   }

   public static PalTimeStamp get(int var0, PalDate var1, String var2) {
      try {
         return get(var0, var1.getYear(), var1.getMonth(), var1.getDay(), var1.getHour(), var1.getMinute(), -1, -1, var2);
      } catch (PalTimeStamp$ArrayOutOfBoundsException var3) {
         return null;
      }
   }

   public void setFlags(int var1) {
      try {
         this.flags = var1;
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   public int getFlags() {
      return this.flags;
   }

   public int getYear() {
      return this.year;
   }

   public int getMonth() {
      return this.month;
   }

   public int getDay() {
      return this.day;
   }

   public int getHour() {
      return this.hour;
   }

   public int getMinute() {
      return this.minute;
   }

   public int getSecond() {
      return this.second;
   }

   public int getTenth() {
      return this.tenth;
   }

   public String getUser() {
      return this.user;
   }

   public String getCompactString() {
      try {
         if (this.tenth < 0) {
            return this.second < 0 ? String.format("%04d%02d%02d%02d%02d", this.year, this.month, this.day, this.hour, this.minute) : String.format("%04d%02d%02d%02d%02d%02d", this.year, this.month, this.day, this.hour, this.minute, this.second);
         } else {
            return String.format("%04d%02d%02d%02d%02d%02d%1d", this.year, this.month, this.day, this.hour, this.minute, this.second, this.tenth);
         }
      } catch (PalTimeStamp$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public String getDisplayString() {
      try {
         if (this.tenth < 0) {
            return this.second < 0 ? String.format("%04d-%02d-%02d %02d:%02d", this.year, this.month, this.day, this.hour, this.minute) : String.format("%04d-%02d-%02d %02d:%02d:%02d", this.year, this.month, this.day, this.hour, this.minute, this.second);
         } else {
            return String.format("%04d-%02d-%02d %02d:%02d:%02d.%1d", this.year, this.month, this.day, this.hour, this.minute, this.second, this.tenth);
         }
      } catch (PalTimeStamp$ArrayOutOfBoundsException var1) {
         return null;
      }
   }

   public boolean equals(PalTimeStamp var1) {
      try {
         return var1.getCompactString().equals(this.getCompactString());
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
         return false;
      }
   }

   public void copy(PalTimeStamp var1) {
      try {
         this.setFlags(var1.getFlags());
         this.setYear(var1.getYear());
         this.setMonth(var1.getMonth());
         this.setDay(var1.getDay());
         this.setHour(var1.getHour());
         this.setMinute(var1.getMinute());
         this.setSecond(var1.getSecond());
         this.setTenth(var1.getTenth());
         this.setUser(var1.getUser());
      } catch (PalTimeStamp$ArrayOutOfBoundsException var2) {
      }
   }

   public boolean isEmpty() {
      return this.getMonth() == 0 || this.getDay() == 0;
   }

   public String toString() {
      if (this.flags == 0 && this.isEmpty()) {
         return "<invalid>";
      } else {
         StringBuilder var1 = new StringBuilder();
         if ((this.getFlags() & 1) != 0) {
            var1.append("CHECK|");
         }

         if ((this.getFlags() & 2) != 0) {
            var1.append("GET|");
         }

         if ((this.getFlags() & 4) != 0) {
            var1.append("NOOPERATION|");
         }

         if (var1.length() > 0) {
            var1.setCharAt(var1.length() - 1, ':');
         }

         if (this.isEmpty()) {
            var1.append("<empty>");
            return var1.toString();
         } else {
            var1.append(this.getCompactString());
            if (this.getUser().length() == 0) {
               return var1.toString();
            } else {
               var1.append(" " + this.getUser());
               return var1.toString();
            }
         }
      }
   }
}
