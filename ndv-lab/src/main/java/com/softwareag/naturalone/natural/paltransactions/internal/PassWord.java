package com.softwareag.naturalone.natural.paltransactions.internal;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public final class PassWord {
   private static final int ASCII_HOST = 1;
   private static final int EBCDIC_HOST = 2;
   private static final int BCD_L_MASK = 15;
   private static final int BCD_U_MASK = 240;
   private static final int BCD_OK = 0;
   private static final int BCD_OVERFLOW = 1305;
   private static final int BCD_DIVISION_BY_ZERO = 1302;
   private static final int BCD_Ok = 0;
   private static final int BCD_Overflow = 1305;
   private static final int BCD_P_MINUS = 13;
   private static final int BCD_P_PLUS = 12;
   private static final int MAX_NP_DIGITS = 31;
   private static final int MAX_FRAC = 8;
   private static final int MAX_N = 31;
   private static final int MAX_P = 39;
   private static final int MAX_P_BYTES = 20;
   private static int host;

   private PassWord() {
   }

   public static String encode(String var0, String var1, String var2, String var3) {
      char[] var4 = new char[17];
      char[] var5 = new char[17];
      char[] var6 = new char[17];
      char[] var7 = new char[17];
      char[] var8 = new char[74];
      byte[] var9 = new byte[9];
      byte[] var10 = new byte[9];
      byte[] var11 = new byte[9];
      byte[] var12 = new byte[9];
      byte[] var13 = new byte[9];
      byte[] var14 = new byte[9];
      String var15 = (new SimpleDateFormat("HH0mm0ss")).format((new GregorianCalendar()).getTime());
      determinePlatform();
      if (var0.length() > 8) {
         throw new IllegalArgumentException("the user id " + var0 + " exceeds 8 bytes");
      } else if (var1.length() > 8) {
         throw new IllegalArgumentException("the password " + var1 + " exceeds 8 bytes");
      } else if (var2.length() > 8) {
         throw new IllegalArgumentException("the library " + var2 + " exceeds 8 bytes");
      } else {
         for(int var16 = 0; var16 < 74; ++var16) {
            var8[var16] = ' ';
         }

         var10[0] = 0;
         var11[0] = 0;
         var13[0] = 0;
         var14[0] = 0;
         byte[] var24 = var0.getBytes();
         System.arraycopy(var24, 0, var10, 0, var24.length);
         var24 = var1.getBytes();
         System.arraycopy(var24, 0, var11, 0, var24.length);
         var24 = var15.getBytes();
         System.arraycopy(var24, 0, var12, 0, var24.length);
         var24 = var2.getBytes();
         System.arraycopy(var24, 0, var13, 0, var24.length);
         var24 = var3.getBytes();
         System.arraycopy(var24, 0, var14, 0, var24.length);
         System.arraycopy(var10, 0, var9, 0, 8);
         nscConvert(var9, var12);
         var4 = convert_hex_char(var9);
         System.arraycopy(var11, 0, var9, 0, 8);
         var9 = nscConvert(var9, var12);
         var5 = convert_hex_char(var9);
         System.arraycopy(var13, 0, var9, 0, 8);
         var9 = nscConvert(var9, var12);
         var6 = convert_hex_char(var9);
         if (var14.length > 0) {
            System.arraycopy(var14, 0, var9, 0, 8);
            nscConvert(var9, var12);
            var7 = convert_hex_char(var9);
         }

         if (host == 1) {
            var8[0] = 'C';
         }

         if (host == 2) {
            var8[0] = 'E';
         }

         char[] var17 = new char[9];

         for(int var18 = 0; var18 < 8; ++var18) {
            var17[var18] = (char)var12[var18];
         }

         System.arraycopy(var17, 0, var8, 1, 8);
         System.arraycopy(var4, 0, var8, 9, 16);
         System.arraycopy(var5, 0, var8, 25, 16);
         System.arraycopy(var6, 0, var8, 41, 16);
         if (var14.length == 0) {
            var8[57] = 255;
         } else {
            System.arraycopy(var7, 0, var8, 57, 16);
            var8[73] = 255;
         }

         return (new String(var8)).toUpperCase();
      }
   }

   private static void determinePlatform() {
      byte var0 = 32;
      host = 0;
      if (var0 == 32) {
         host = 1;
      }

      if (var0 == 64) {
         host = 2;
      }

   }

   private static byte[] nscConvert(byte[] var0, byte[] var1) {
      try {
         byte[] var2 = new byte[9];
         byte[] var3 = new byte[9];
         byte[] var4 = new byte[]{32, 32, 32, 32, 32, 32, 32, 32, 32};
         char[] var5 = new char[]{' ', ' ', ' ', ' ', ' ', ' '};
         boolean var9 = false;
         byte var10 = 7;
         byte var11 = 20;
         long var13 = 0L;
         long var15 = 0L;
         long var17 = 2147483647L;
         byte[] var19 = new byte[20];
         byte[] var20 = new byte[20];
         byte[] var21 = new byte[20];
         byte[] var22 = new byte[20];
         byte[] var23 = new byte[20];
         byte[] var24 = new byte[20];
         byte[] var25 = new byte[20];
         byte[] var26 = new byte[20];
         System.arraycopy(var0, 0, var3, 0, var10 + 1);
         System.arraycopy(var1, 0, var2, 0, var10 + 1);
         var5[0] = (char)var2[4];
         var5[1] = (char)var2[1];
         var5[2] = (char)var2[3];
         var5[3] = (char)var2[6];
         var5[4] = (char)var2[7];
         var5[5] = (char)var2[0];
         String var6 = new String(var5);
         var13 = (long)Integer.parseInt(var6);
         b_cvd(var19, 20, 1000L);
         b_cvd(var26, 20, 455470314L);
         b_cvd(var23, 20, var13);
         b_cvd(var20, 20, var17);

         for(int var29 = 0; var29 <= var10; ++var29) {
            System.arraycopy(var26, 0, var21, 0, 20);
            b_mp(var21, var11, var23, var11);
            System.arraycopy(var21, 0, var22, 0, 20);
            b_dp(var22, var11, var20, var11);
            b_mp(var22, var11, var20, var11);
            System.arraycopy(var21, 0, var23, 0, 20);
            b_sp(var23, var11, var22, var11);
            System.arraycopy(var23, 0, var24, 0, 20);
            b_dp(var24, var11, var19, var11);
            b_mp(var24, var11, var19, var11);
            System.arraycopy(var23, 0, var25, 0, 20);
            b_sp(var25, var11, var24, var11);
            var15 = b_cvb(var15, var25, var11);
            long var7 = (long)var3[var29];
            var7 += var15;
            var4[var29] = (byte)((int)var7);
         }

         for(int var12 = 0; var12 <= var10; ++var12) {
            var0[var12] = var4[var12];
         }

         return var0;
      } catch (PassWord$ParseException var27) {
         return null;
      }
   }

   private static int b_mp(byte[] var0, int var1, byte[] var2, int var3) {
      boolean var16 = false;
      boolean var17 = false;
      int var8 = 1 - var1 % 2;
      int var7 = var1 + var8;
      int var10 = 1 - var3 % 2;
      int var9 = var3 + var10;
      boolean var15 = false;

      int var4;
      for(var4 = var8; var4 < var7 && GetBCD(var0, var4) == 0; ++var4) {
      }

      int var11 = var1 - var4;

      for(var4 = var10; var4 < var9 && GetBCD(var2, var4) == 0; ++var4) {
      }

      var11 += var3 - var4;
      if (var11 > var1 + 1) {
         var15 = true;
      }

      for(int var19 = var8; var19 < var7; ++var19) {
         var11 = 0;
         int var5 = var19;

         for(int var6 = var9 - 1; var5 < var7 && var6 >= var10; --var6) {
            int var12 = GetBCD(var0, var5);
            var12 *= GetBCD(var2, var6);
            var11 += var12;
            ++var5;
         }

         int var13 = var11 / 10;
         var0 = SetBCD(var0, var19, (long)(var11 % 10));
         if (var13 > 0) {
            for(int var20 = var19 - 1; var20 >= var8 && var13 != 0; --var20) {
               var11 = GetBCD(var0, var20) + var13;
               var13 = var11 / 10;
               var0 = SetBCD(var0, var20, (long)(var11 % 10));
            }

            if (var13 != 0) {
               var15 = true;
            }
         }
      }

      if (var15) {
         return 1305;
      } else {
         int var14 = b_zero(var0, var1);
         if (var14 != 0 || Is_P_Positive(GetBCD(var0, var7))) {
            var16 = true;
         }

         if (b_zero(var2, var3) != 0 || Is_P_Positive(GetBCD(var2, var9))) {
            var17 = true;
         }

         if (var14 == 0 && var16 != var17) {
            Set_P_Negative(var0, var7);
         } else {
            Set_P_Positive(var0, var7);
         }

         return 0;
      }
   }

   private static int b_cvd(byte[] var0, int var1, long var2) {
      try {
         short var4 = 0;
         int var6 = 1 - var1 % 2;
         int var5 = var1 + var6;
         long var7 = var2;
         var0 = Set_P_Positive(var0, var5);

         while(var5 > var6) {
            --var5;
            var0 = SetBCD(var0, var5, var7 % 10L);
            var7 /= 10L;
            if (var7 == 0L) {
               break;
            }
         }

         while(var5 > 0) {
            --var5;
            var0 = SetBCD(var0, var5, 0L);
         }

         if (var7 != 0L) {
            var4 = 1305;
         }

         return var4;
      } catch (PassWord$ParseException var9) {
         return 0;
      }
   }

   private static int b_zero(byte[] var0, int var1) {
      try {
         int var3 = 0;
         if (var1 <= 0) {
            return 0;
         } else {
            for(int var2 = var1 / 2; var2 > 0; --var2) {
               if (var0[var3++] != 0) {
                  return 0;
               }
            }

            return (var0[0] & 240) != 0 ? 0 : 1;
         }
      } catch (PassWord$ParseException var4) {
         return 0;
      }
   }

   private static boolean Is_P_Positive(int var0) {
      boolean var1 = false;
      if (!Is_P_Negative(var0)) {
         var1 = true;
      }

      return var1;
   }

   private static byte[] Set_P_Negative(byte[] var0, int var1) {
      try {
         var0[var1 >> 1] = (byte)(13 | var0[var1 >> 1] & 240);
         return var0;
      } catch (PassWord$ParseException var2) {
         return null;
      }
   }

   private static byte[] Set_P_Positive(byte[] var0, int var1) {
      try {
         var0[var1 >> 1] = (byte)(12 | var0[var1 >> 1] & 240);
         return var0;
      } catch (PassWord$ParseException var2) {
         return null;
      }
   }

   private static boolean Is_P_Negative(int var0) {
      boolean var1 = false;
      if ((var0 & 15) == 13 || (var0 & 15) == 11) {
         var1 = true;
      }

      return var1;
   }

   private static byte[] SetBCD(byte[] var0, int var1, long var2) {
      try {
         var0[var1 >> 1] = var1 % 2 != 0 ? (byte)((int)((long)(var0[var1 >> 1] & 240) | var2 & 15L)) : (byte)((int)(var2 << 4 & 240L | (long)(var0[var1 >> 1] & 15)));
         return var0;
      } catch (PassWord$ParseException var4) {
         return null;
      }
   }

   private static int GetBCD(byte[] var0, int var1) {
      try {
         return (var1 % 2 != 0 ? var0[var1 >> 1] : var0[var1 >> 1] >> 4) & 15;
      } catch (PassWord$ParseException var2) {
         return 0;
      }
   }

   private static int b_dp(byte[] var0, int var1, byte[] var2, int var3) {
      int var17 = 0;
      byte[] var20 = new byte[21];
      boolean var21 = false;
      boolean var22 = false;
      int var9 = 1 - var1 % 2;
      int var8 = var1 + var9;
      int var11 = 1 - var3 % 2;
      int var10 = var3 + var11;

      int var4;
      for(var4 = var11; var4 < var10; ++var4) {
         var17 = GetBCD(var2, var4);
         if (var17 != 0) {
            break;
         }
      }

      int var7 = var10 - var4;
      if (var7 == 0) {
         return 1302;
      } else {
         int var16 = 10 * var17;
         ++var4;
         if (var4 < var10) {
            var16 += GetBCD(var2, var4);
         }

         for(int var24 = var9 - 1; var24 < var8 - var7; ++var24) {
            int var15 = 0;
            if (var24 >= var9) {
               var15 = 10 * GetBCD(var0, var24);
            }

            if (var24 < var8 - 1) {
               var15 += GetBCD(var0, var24 + 1);
            }

            int var14 = 10 * var15;
            if (var24 < var8 - 2) {
               var14 += GetBCD(var0, var24 + 2);
            }

            int var13 = var15 / var17;
            if (var13 > 9) {
               var13 = 9;
            }

            for(int var34 = var14 - var13 * var16; var34 < 0; --var13) {
               var34 += var16;
            }

            int var18 = 0;
            if (var13 > 0) {
               int var5 = var24 + var7;

               for(int var6 = var10 - 1; var5 >= var9 && var5 >= var24 && var6 >= var11; --var6) {
                  int var12 = 100 + GetBCD(var0, var5) + var18;
                  var12 -= var13 * GetBCD(var2, var6);
                  var18 = var12 / 10 - 10;
                  var0 = SetBCD(var0, var5, (long)(var12 % 10));
                  --var5;
               }

               while(var5 >= var9 && var18 < 0) {
                  int var30 = 10 + GetBCD(var0, var5) + var18;
                  var18 = var30 / 10 - 1;
                  var0 = SetBCD(var0, var5, (long)(var30 % 10));
                  --var5;
               }
            }

            if (var18 < 0) {
               --var13;
               var18 = 0;
               int var26 = var24 + var7;

               for(int var28 = var10 - 1; var26 >= var9 && var26 >= var24 && var28 >= var11; --var28) {
                  int var31 = GetBCD(var0, var26) + var18;
                  var31 += GetBCD(var2, var28);
                  if (var31 > 9) {
                     var31 -= 10;
                     var18 = 1;
                  } else {
                     var18 = 0;
                  }

                  var0 = SetBCD(var0, var26, (long)var31);
                  --var26;
               }

               while(var26 >= var9 && var18 != 0) {
                  int var33 = GetBCD(var0, var26) + var18;
                  var18 = var33 / 10;
                  var0 = SetBCD(var0, var26, (long)(var33 % 10));
                  --var26;
               }
            }

            var20 = SetBCD(var20, var24 + 1, (long)var13);
         }

         var4 = var8 - 1;

         for(int var27 = var4 - var7; var27 >= var9 - 1; --var27) {
            var0 = SetBCD(var0, var4, (long)GetBCD(var20, var27 + 1));
            --var4;
         }

         while(var4 >= var9) {
            var0 = SetBCD(var0, var4, 0L);
            --var4;
         }

         int var19 = b_zero(var0, var1);
         if (var19 != 0 || Is_P_Positive(GetBCD(var0, var8))) {
            var21 = true;
         }

         if (b_zero(var2, var3) != 0 || Is_P_Positive(GetBCD(var2, var10))) {
            var22 = true;
         }

         if (var19 == 0 && var21 != var22) {
            Set_P_Negative(var0, var8);
         } else {
            Set_P_Positive(var0, var8);
         }

         return 0;
      }
   }

   private static int b_sp(byte[] var0, int var1, byte[] var2, int var3) {
      byte var9 = 0;
      int var4 = var1 < var3 ? var3 : var1;
      int var7 = 1 - var1 % 2;
      int var5 = var1 + var7;
      int var8 = 1 - var3 % 2;
      int var6 = var3 + var8;
      boolean var11 = false;
      if (Is_P_Negative(GetBCD(var0, var5)) != Is_P_Negative(GetBCD(var2, var6))) {
         var9 = 0;

         label133:
         while(true) {
            --var4;
            if (var4 < 0) {
               int var18;
               for(; var5 > var7 && var9 != 0; var0 = SetBCD(var0, var5, (long)var18)) {
                  --var5;
                  var18 = GetBCD(var0, var5) + var9;
                  if (var18 > 9) {
                     var18 -= 10;
                     var9 = 1;
                  } else {
                     var9 = 0;
                  }
               }

               while(var6 > var8) {
                  --var6;
                  if (GetBCD(var2, var6) != 0) {
                     var11 = true;
                     break label133;
                  }
               }
               break;
            }

            --var5;
            --var6;
            int var12 = GetBCD(var0, var5) + var9;
            var12 += GetBCD(var2, var6);
            if (var12 > 9) {
               var12 -= 10;
               var9 = 1;
            } else {
               var9 = 0;
            }

            var0 = SetBCD(var0, var5, (long)var12);
         }
      } else {
         byte var10 = 0;

         label120:
         while(true) {
            --var4;
            if (var4 < 0) {
               int var20;
               for(; var5 > var7 && var10 != 0; var0 = SetBCD(var0, var5, (long)var20)) {
                  --var5;
                  var20 = GetBCD(var0, var5) + var10;
                  if (var20 < 0) {
                     var20 += 10;
                     var10 = 1;
                  } else {
                     var10 = 0;
                  }
               }

               if (var10 != 0) {
                  var10 = 0;
                  var7 = 1 - var1 % 2;
                  var5 = var1 + var7;
                  if (Is_P_Positive(GetBCD(var0, var5))) {
                     var0 = Set_P_Negative(var0, var5);
                  } else {
                     var0 = Set_P_Positive(var0, var5);
                  }

                  int var14;
                  for(; var5 > var7; var0 = SetBCD(var0, var5, (long)var14)) {
                     --var5;
                     var14 = -GetBCD(var0, var5) - var10;
                     if (var14 < 0) {
                        var14 += 10;
                        var10 = 1;
                     } else {
                        var10 = 0;
                     }
                  }
               }

               while(var6 > var8) {
                  --var6;
                  if (GetBCD(var2, var6) != 0) {
                     var11 = true;
                     break label120;
                  }
               }
               break;
            }

            --var5;
            --var6;
            int var13 = GetBCD(var0, var5) - var10;
            var13 -= GetBCD(var2, var6);
            if (var13 < 0) {
               var13 += 10;
               var10 = 1;
            } else {
               var10 = 0;
            }

            var0 = SetBCD(var0, var5, (long)var13);
         }
      }

      if (var9 != 0) {
         var11 = true;
      }

      if (b_zero(var0, var1) != 0) {
         Set_P_Positive(var0, var1 + var7);
      }

      return var11 ? 1305 : 0;
   }

   private static long b_cvb(long var0, byte[] var2, int var3) {
      try {
         long var8 = 0L;
         int var7 = 1 - var3 % 2;
         int var6 = var3 + var7;

         long var4;
         for(var4 = 0L; var7 < var6; ++var7) {
            var4 *= 10L;
            var4 += (long)GetBCD(var2, var7);
            if (var4 < 0L) {
               return 1305L;
            }
         }

         if (Is_P_Negative(GetBCD(var2, var6))) {
            var4 = -var4;
         }

         return var4;
      } catch (PassWord$ParseException var10) {
         return 0L;
      }
   }

   private static char[] convert_hex_char(byte[] var0) {
      byte var2 = 8;
      char[] var3 = new char[17];
      int var4 = 0;
      int var5 = 0;
      Object var6 = null;

      for(int var1 = 0; var1 < var2; ++var1) {
         var5 = var0[var1] & 255;
         byte[] var10 = Integer.toHexString(var5).getBytes();
         if (var10.length == 1) {
            var3[var4++] = '0';
            var3[var4++] = (char)var10[0];
         } else {
            var3[var4++] = (char)var10[0];
            var3[var4++] = (char)var10[1];
         }
      }

      return var3;
   }
}
