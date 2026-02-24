package com.softwareag.naturalone.natural.pal;

import java.util.Arrays;

public final class Base64Coder {
   private static final String NCBASE64_ENCODE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
   private static final int BASE64_E_SUCCESS = 0;
   private static final int BASE64_E_INVCHAR = 11;
   private static final byte BASE64_F_INSERTLINEBREAK = 1;
   public static final byte BASE64_F_IGNOREINVCHAR = 2;
   public static final byte BASE64_F_NOPADDING = 4;
   public static final byte BASE64_F_RFC2045 = 3;
   public static final byte BASE64_F_RFC3548 = 0;
   public static final byte BASE64_F_NATRPC = 4;
   private static final int BASE64_LINELEN_OUT = 76;
   private static final int BASE64_LINELEN_IN = 57;

   private static int base64Lena3548(int var0) {
      try {
         return (var0 + 2) / 3 * 4;
      } catch (Base64Coder$Exception var1) {
         return 0;
      }
   }

   private static int base64Lenb3548(int var0) {
      try {
         return (var0 + 3) / 4 * 3;
      } catch (Base64Coder$Exception var1) {
         return 0;
      }
   }

   private static int base64Lena2045(int var0) {
      try {
         return base64Lena3548(var0) + base64Lena3548(var0) / 76 * 2;
      } catch (Base64Coder$Exception var1) {
         return 0;
      }
   }

   private static int base64Lenb2045(int var0) {
      return base64Lenb3548(var0);
   }

   private Base64Coder() {
   }

   static char[] encode(int[] var0, byte var1) {
      int var2 = 0;
      int var3 = 0;
      boolean var4 = false;
      int var5 = 0;
      int var6 = 0;
      int[] var7 = new int[3];
      byte var8 = 0;
      byte var9 = 0;
      char[] var10 = new char["ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length()];
      if (var0 == null) {
         throw new IllegalArgumentException("binary must not be null");
      } else if (var0.length == 0) {
         throw new IllegalArgumentException("binary must not be empty");
      } else {
         int var11 = var0.length;
         if ((var1 & 1) == 1) {
            var6 = base64Lena2045(var11);
         } else {
            var6 = base64Lena3548(var11);
         }

         char[] var12 = new char[var6];
         int[] var13 = var0;
         System.arraycopy("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toCharArray(), 0, var10, 0, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length());

         while(var2 < var11) {
            var5 = var11 - var2;
            Arrays.fill(var7, 0);
            System.arraycopy(var13, var2, var7, 0, var5 > var7.length ? var7.length : var5);

            for(int var15 = 0; var15 < 4; ++var15) {
               var8 = 0;
               var9 = (byte)0;
               switch (var15) {
                  case 0:
                     var8 = (byte)(var7[0] >> 2);
                     var8 = (byte)(var8 & 63);
                     break;
                  case 1:
                     var8 = (byte)(var7[0] << 4);
                     var9 = (byte)(var7[1] >> 4);
                     var8 = (byte)(var8 | var9);
                     var8 = (byte)(var8 & 63);
                     break;
                  case 2:
                     var8 = (byte)(var7[1] << 2);
                     var9 = (byte)(var7[2] >> 6);
                     var8 = (byte)(var8 | var9);
                     var8 = (byte)(var8 & 63);
                     if (var5 == 1) {
                        var8 = (byte)("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length() - 1);
                     }
                     break;
                  case 3:
                     var8 = (byte)var7[2];
                     var8 = (byte)(var8 & 63);
                     if (var5 == 1 || var5 == 2) {
                        var8 = (byte)("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length() - 1);
                     }
               }

               if ((var1 & 4) != 4 || var8 != "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length() - 1) {
                  var12[var3] = var10[var8];
                  ++var3;
               }
            }

            var2 += 3;
            if ((var1 & 1) == 1 && var5 > 0 && var2 / 57 * 57 == var2) {
               var12[var3] = '\r';
               ++var3;
               var12[var3] = '\n';
               ++var3;
            }
         }

         return var12;
      }
   }

   public static byte[] decode(char[] var0, byte var1) {
      try {
         byte var2 = 0;
         int var3 = 0;
         int var4 = 0;
         byte[] var5 = new byte[3];
         byte var6 = 0;
         byte var7 = 0;
         int var8 = 0;
         byte var9 = 0;
         char[] var10 = new char["ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length()];
         Object var11 = null;
         if (var0 == null) {
            throw new IllegalArgumentException("base64 must not be null");
         } else if (var0.length == 0) {
            throw new IllegalArgumentException("base64 must not be empty");
         } else {
            int var12 = var0.length;
            if ((var1 & 1) == 1) {
               var8 = base64Lenb2045(var12);
            } else {
               var8 = base64Lenb3548(var12);
            }

            byte[] var20 = new byte[var8];
            char[] var13 = var0;
            Arrays.fill(var5, (byte)0);
            var6 = 0;
            var7 = 0;
            System.arraycopy("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toCharArray(), 0, var10, 0, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length());

            while(var3 < var12) {
               var9 = 0;
               var9 = (byte)"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".indexOf(var13[var3]);
               if (var9 == -1) {
                  if ((var1 & 2) != 2) {
                     var2 = 11;
                     break;
                  }

                  ++var3;
               } else {
                  if (var9 == "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length() - 1) {
                     break;
                  }

                  switch (var7) {
                     case 0:
                        var5[0] = (byte)(var9 << 2);
                        var6 = 1;
                        var7 = 1;
                        break;
                     case 1:
                        var5[0] |= (byte)(var9 >> 4);
                        var5[1] = (byte)(var9 << 4);
                        var6 = 1;
                        var7 = 2;
                        break;
                     case 2:
                        var5[1] = (byte)(var5[1] | var9 >> 2);
                        var5[2] = (byte)(var9 << 6);
                        var6 = 2;
                        var7 = 3;
                        break;
                     case 3:
                        var5[2] |= var9;
                        var6 = 3;
                        var7 = 0;
                  }

                  ++var3;
                  if (var3 == var12 || var7 == 0) {
                     if (var6 > 0) {
                        System.arraycopy(var5, 0, var20, var4, var6);
                        var4 += var6;
                     }

                     Arrays.fill(var5, (byte)0);
                     var6 = 0;
                     var7 = 0;
                  }
               }
            }

            if ((var2 == 11 || var9 == "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".length() - 1) && var6 > 0) {
               System.arraycopy(var5, 0, var20, var4, var6);
               var4 += var6;
            }

            byte[] var14 = new byte[var4];
            System.arraycopy(var20, 0, var14, 0, var4);
            return var14;
         }
      } catch (Base64Coder$Exception var15) {
         return null;
      }
   }
}
