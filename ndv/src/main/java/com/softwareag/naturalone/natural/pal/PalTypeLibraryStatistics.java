package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeLibraryStatistics;
import com.softwareag.naturalone.natural.pal.external.PalDate;

public class PalTypeLibraryStatistics extends PalType implements IPalTypeLibraryStatistics {
   private static final long serialVersionUID = 1L;
   private String library = "";
   private int numberSources;
   private int sizeSources;
   private int numberGPs;
   private int sizeGPs;
   private int numberResources;
   private int sizeResources;
   private int numberErrorMessages;
   private int sizeErrorMessages;
   private int numberObjs;
   private int numberBytes;
   private int numberNatTypes;
   private int[] natTypes = null;
   private int[] numberObjects = null;
   private int[] sizeObjects = null;
   private PalDate modDate;
   private int flags;
   private int natTypeFlags;

   public PalTypeLibraryStatistics() {
      super.type = 4;
   }

   public void serialize() {
   }

   public void restore() {
      this.library = this.stringFromBuffer();
      this.numberSources = this.intFromBuffer();
      this.sizeSources = this.intFromBuffer();
      this.numberGPs = this.intFromBuffer();
      this.sizeGPs = this.intFromBuffer();
      this.numberResources = this.intFromBuffer();
      this.sizeResources = this.intFromBuffer();
      this.numberErrorMessages = this.intFromBuffer();
      this.sizeErrorMessages = this.intFromBuffer();
      this.numberBytes = this.intFromBuffer();
      this.numberObjs = this.intFromBuffer();
      this.numberNatTypes = this.intFromBuffer();
      this.natTypes = new int[this.numberNatTypes];
      this.numberObjects = new int[this.numberNatTypes];
      this.sizeObjects = new int[this.numberNatTypes];

      for(int var1 = 0; var1 < this.numberNatTypes; ++var1) {
         this.natTypes[var1] = this.intFromBuffer();
         this.numberObjects[var1] = this.intFromBuffer();
         this.sizeObjects[var1] = this.intFromBuffer();
         this.natTypeFlags |= this.natTypes[var1];
      }

      this.modDate = new PalDate(this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer(), this.intFromBuffer());
      if (super.recordTail < super.recordLength) {
         this.flags = this.intFromBuffer();
      }

   }

   public String[] getTypes() {
      String[] var1 = new String[this.numberNatTypes];
      int var2 = 0;
      if (this.hasProgram()) {
         var1[var2++] = "Program";
      }

      if (this.hasSubProgram()) {
         var1[var2++] = "Subprogram";
      }

      if (this.hasSubRoutine()) {
         var1[var2++] = "Subroutine";
      }

      if (this.hasCopyCode()) {
         var1[var2++] = "Copycode";
      }

      if (this.hasText()) {
         var1[var2++] = "Text";
      }

      if (this.hasMap()) {
         var1[var2++] = "Map";
      }

      if (this.hasLda()) {
         var1[var2++] = "Local Data Area";
      }

      if (this.hasGda()) {
         var1[var2++] = "Global Data Area";
      }

      if (this.hasPda()) {
         var1[var2++] = "Parameter Data Area";
      }

      if (this.hasDdm()) {
         var1[var2++] = "DDM";
      }

      if (this.hasDialog()) {
         var1[var2++] = "Dialog";
      }

      if (this.hasAdapter()) {
         var1[var2++] = "Adapter";
      }

      if (this.hasFunction()) {
         var1[var2++] = "Function";
      }

      if (this.hasAdaptView()) {
         var1[var2++] = "Adapt View";
      }

      if (this.hasNaturalCommandProcessor()) {
         var1[var2++] = "Command Processor";
      }

      if (this.hasHelpRoutine()) {
         var1[var2++] = "Helproutine";
      }

      if (this.hasClass()) {
         var1[var2++] = "Class";
      }

      return var1;
   }

   public boolean hasAdapter() {
      try {
         return (this.natTypeFlags & 2097152) == 2097152;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasGda() {
      try {
         return (this.natTypeFlags & 1) == 1;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasLda() {
      try {
         return (this.natTypeFlags & 2) == 2;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasPda() {
      try {
         return (this.natTypeFlags & 4) == 4;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasDdm() {
      try {
         return (this.natTypeFlags & 8) == 8;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasProgram() {
      try {
         return (this.natTypeFlags & 16) == 16;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasSubProgram() {
      try {
         return (this.natTypeFlags & 32) == 32;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasSubRoutine() {
      try {
         return (this.natTypeFlags & 256) == 256;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasMap() {
      try {
         return (this.natTypeFlags & 64) == 64;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasCopyCode() {
      try {
         return (this.natTypeFlags & 128) == 128;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasHelpRoutine() {
      try {
         return (this.natTypeFlags & 512) == 512;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasClass() {
      try {
         return (this.natTypeFlags & 1024) == 1024;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasDialog() {
      try {
         return (this.natTypeFlags & 2048) == 2048;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasText() {
      try {
         return (this.natTypeFlags & 4096) == 4096;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasNaturalCommandProcessor() {
      try {
         return (this.natTypeFlags & 8192) == 8192;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasAdaptView() {
      try {
         return (this.natTypeFlags & 16384) == 16384;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasErrorMessage() {
      try {
         return (this.natTypeFlags & 32768) == 32768;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasResource() {
      try {
         return (this.natTypeFlags & 65536) == 65536;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public boolean hasFunction() {
      try {
         return (this.natTypeFlags & 524288) == 524288;
      } catch (PalTypeLibraryStatistics$ParseException var1) {
         return false;
      }
   }

   public final int getFlags() {
      return this.flags;
   }

   public final String getLibrary() {
      return this.library;
   }

   public final PalDate getModDate() {
      return this.modDate;
   }

   public final int getNatTypeFlags() {
      return this.natTypeFlags;
   }

   public final int[] getNatTypes() {
      int[] var1 = null;
      if (this.natTypes != null) {
         var1 = new int[this.natTypes.length];
         System.arraycopy(this.natTypes, 0, var1, 0, this.natTypes.length);
      }

      return var1;
   }

   public final int getNumberErrorMessages() {
      return this.numberErrorMessages;
   }

   public final int getNumberGPs() {
      return this.numberGPs;
   }

   public final int getNumberNatTypes() {
      return this.numberNatTypes;
   }

   public final int[] getNumberObjects() {
      int[] var1 = null;
      if (this.numberObjects != null) {
         var1 = new int[this.numberObjects.length];
         System.arraycopy(this.numberObjects, 0, var1, 0, this.numberObjects.length);
      }

      return var1;
   }

   public final int getNumberResources() {
      return this.numberResources;
   }

   public final int getNumberSources() {
      return this.numberSources;
   }

   public final int getSizeErrorMessages() {
      return this.sizeErrorMessages;
   }

   public final int getSizeGPs() {
      return this.sizeGPs;
   }

   public final int[] getSizeObjects() {
      int[] var1 = null;
      if (this.sizeObjects != null) {
         var1 = new int[this.sizeObjects.length];
         System.arraycopy(this.sizeObjects, 0, var1, 0, this.sizeObjects.length);
      }

      return var1;
   }

   public final int getSizeResources() {
      return this.sizeResources;
   }

   public final int getSizeSources() {
      return this.sizeSources;
   }

   public boolean equals(Object var1) {
      try {
         if (var1 == this) {
            return true;
         } else if (!(var1 instanceof PalTypeLibraryStatistics)) {
            return false;
         } else {
            PalTypeLibraryStatistics var2 = (PalTypeLibraryStatistics)var1;
            return this.library.equals(var2.library) && this.numberSources == var2.numberSources && this.sizeSources == var2.sizeSources && this.numberGPs == var2.numberGPs && this.sizeGPs == var2.sizeGPs && this.numberResources == var2.numberResources && this.sizeResources == var2.sizeResources && this.numberErrorMessages == var2.numberErrorMessages && this.sizeErrorMessages == var2.sizeErrorMessages && this.numberObjs == var2.numberObjs && this.numberBytes == var2.numberBytes && this.natTypeFlags == var2.natTypeFlags;
         }
      } catch (PalTypeLibraryStatistics$ParseException var3) {
         return false;
      }
   }

   public int hashCode() {
      try {
         int var1 = 17;
         var1 = 37 * var1 + this.numberObjs;
         var1 = 37 * var1 + this.numberGPs;
         var1 = 37 * var1 + this.numberSources;
         var1 = 37 * var1 + this.library.hashCode();
         return var1;
      } catch (PalTypeLibraryStatistics$ParseException var2) {
         return 0;
      }
   }

   public String toString() {
      String var1 = "";
      if (this.hasAdapter()) {
         var1 = var1 + "Adapter,";
      }

      if (this.hasGda()) {
         var1 = var1 + "Gda,";
      }

      if (this.hasLda()) {
         var1 = var1 + "Lda,";
      }

      if (this.hasPda()) {
         var1 = var1 + "Pda,";
      }

      if (this.hasDdm()) {
         var1 = var1 + "Ddm,";
      }

      if (this.hasProgram()) {
         var1 = var1 + "Program,";
      }

      if (this.hasSubProgram()) {
         var1 = var1 + "Subprogram,";
      }

      if (this.hasSubRoutine()) {
         var1 = var1 + "Subroutine,";
      }

      if (this.hasMap()) {
         var1 = var1 + "Map,";
      }

      if (this.hasCopyCode()) {
         var1 = var1 + "CopyCode,";
      }

      if (this.hasHelpRoutine()) {
         var1 = var1 + "HelpRoutine,";
      }

      if (this.hasClass()) {
         var1 = var1 + "Class,";
      }

      if (this.hasDialog()) {
         var1 = var1 + "Dialog,";
      }

      if (this.hasText()) {
         var1 = var1 + "Text,";
      }

      if (this.hasNaturalCommandProcessor()) {
         var1 = var1 + "CommandProcessor,";
      }

      if (this.hasAdaptView()) {
         var1 = var1 + "AdaptView,";
      }

      if (this.hasErrorMessage()) {
         var1 = var1 + "Error Message,";
      }

      if (this.hasResource()) {
         var1 = var1 + "Resource,";
      }

      if (this.hasFunction()) {
         var1 = var1 + "Function,";
      }

      return var1;
   }
}
