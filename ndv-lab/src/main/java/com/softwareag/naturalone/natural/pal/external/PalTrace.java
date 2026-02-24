package com.softwareag.naturalone.natural.pal.external;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public final class PalTrace {
   private static FileWriter traceFile;
   private static SimpleDateFormat dateFormat;

   static {
      try {
         traceFile = null;
         dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
      } catch (PalTrace$NullPointerException var0) {
      }
   }

   private PalTrace() {
   }

   public static synchronized void open(boolean var0) throws IOException {
      String var1 = System.getProperty("PALTRACE");
      if (traceFile != null) {
         throw new IllegalStateException("Trace file already opened");
      } else {
         if (var1 != null && var1.equals("ON")) {
            String var2 = System.getProperty("java.io.tmpdir");
            if (var2 != null) {
               String var3 = System.getProperty("PALTRACEFILE");
               if (var3 == null) {
                  var3 = "NatPal.trc";
               }

               traceFile = new FileWriter(var2 + System.getProperty("file.separator") + var3, var0);
            }
         }

      }
   }

   public static synchronized void close() throws IOException {
      if (traceFile != null) {
         traceFile.close();
         traceFile = null;
      }

   }

   public static synchronized void flush() throws IOException {
      if (traceFile != null) {
         traceFile.flush();
      }

   }

   public static synchronized void header(String var0) throws IOException {
      if (traceFile != null) {
         traceFile.write("\r\n");
         traceFile.write("\r\n");
         traceFile.write(getThreadId());
         traceFile.write("------ Transaction '" + var0 + "' ------");
         traceFile.write("\r\n");
         traceFile.flush();
      }

   }

   public static synchronized void buffer(byte[] var0, boolean var1, String var2) throws IOException {
      if (traceFile != null) {
         String var3 = dateFormat.format((new GregorianCalendar()).getTime());
         traceFile.write("\r\n");
         traceFile.write(getThreadId());
         traceFile.write(var3 + " (" + var2 + ")");
         traceFile.write("\r\n");
         if (var1) {
            traceFile.write(getThreadId());
            traceFile.write("<<===== Pal data from server <<======");
         } else {
            traceFile.write(getThreadId());
            traceFile.write("=====>> Pal data to server ======>>");
         }

         for(int var4 = 0; var4 < var0.length; ++var4) {
            if (var4 % 30 == 0) {
               traceFile.write("\r\n");
               traceFile.write(getThreadId());
               traceFile.write(getOffset(var4));
            }

            traceFile.write(var0[var4]);
         }

         traceFile.write("\r\n");
         traceFile.flush();
      }

   }

   public static synchronized void text(String var0) throws IOException {
      if (traceFile != null) {
         traceFile.write(var0);
         traceFile.flush();
      }

   }

   public static synchronized void type(String var0, boolean var1) throws IOException {
      if (traceFile != null) {
         traceFile.write("\r\n");
         traceFile.write(getThreadId());
         if (var1) {
            traceFile.write("<<<<< ");
         } else {
            traceFile.write(">>>>");
         }

         traceFile.write(var0);
         traceFile.flush();
      }

   }

   private static String getOffset(int var0) {
      String var1 = Integer.valueOf(var0).toString();
      StringBuffer var2 = new StringBuffer(10);
      switch (var1.length()) {
         case 1:
            var2.append("000");
            break;
         case 2:
            var2.append("00");
            break;
         case 3:
            var2.append("0");
      }

      var2.append(var1);
      var2.append(" ");
      return var2.toString();
   }

   private static String getThreadId() {
      try {
         return "[" + Long.valueOf(Thread.currentThread().getId()).toString() + "] ";
      } catch (PalTrace$NullPointerException var0) {
         return null;
      }
   }
}
