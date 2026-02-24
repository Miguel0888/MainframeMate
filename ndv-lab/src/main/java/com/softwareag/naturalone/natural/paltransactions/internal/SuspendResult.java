package com.softwareag.naturalone.natural.paltransactions.internal;

import com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;
import com.softwareag.naturalone.natural.paltransactions.external.ISuspendResult;
import com.softwareag.naturalone.natural.paltransactions.external.PalResultException;

public final class SuspendResult implements ISuspendResult {
   private PalResultException exception;
   private IPalTypeDbgStatus status;
   private PalTypeDbgStackFrame[] stackFrames;
   private PalTypeDbgNatStack[] natStackEntries;
   private IPalTypeDbgSpy spy;
   private IPalTypeStream screen;
   private byte decimalCharacter;
   private IPalTypeNotify notify;

   public SuspendResult(IPalTypeDbgStatus var1, PalTypeDbgStackFrame[] var2, IPalTypeDbgSpy var3, IPalTypeStream var4, IPalTypeNotify var5, byte var6, PalResultException var7, PalTypeDbgNatStack[] var8) {
      this.decimalCharacter = var6;
      this.spy = var3;
      this.status = var1;
      this.screen = var4;
      this.exception = var7;
      this.notify = var5;
      this.setStackFrames(var2);
      this.setNatStackEntries(var8);
   }

   public final IPalTypeNotify getNotify() {
      return this.notify;
   }

   public final void setNotify(IPalTypeNotify var1) {
      try {
         this.notify = var1;
      } catch (SuspendResult$ParseException var2) {
      }
   }

   public final byte getDecimalCharacter() {
      return this.decimalCharacter;
   }

   public final IPalTypeDbgSpy getSpy() {
      return this.spy;
   }

   public final PalTypeDbgStackFrame[] getStackFrames() {
      try {
         return this.stackFrames != null ? (PalTypeDbgStackFrame[])this.stackFrames.clone() : null;
      } catch (SuspendResult$ParseException var1) {
         return null;
      }
   }

   public void setStackFrames(PalTypeDbgStackFrame[] var1) {
      if (var1 != null) {
         this.stackFrames = (PalTypeDbgStackFrame[])var1.clone();
      }

   }

   public void setNatStackEntries(PalTypeDbgNatStack[] var1) {
      if (var1 != null) {
         this.natStackEntries = (PalTypeDbgNatStack[])var1.clone();
      }

   }

   public final IPalTypeDbgStatus getStatus() {
      return this.status;
   }

   public final PalResultException getException() {
      return this.exception;
   }

   public final void setException(PalResultException var1) {
      try {
         this.exception = var1;
      } catch (SuspendResult$ParseException var2) {
      }
   }

   public final IPalTypeStream getScreen() {
      return this.screen;
   }

   public final void setScreen(IPalTypeStream var1) {
      try {
         this.screen = var1;
      } catch (SuspendResult$ParseException var2) {
      }
   }

   public PalTypeDbgNatStack[] getNatStackEntries() {
      return this.natStackEntries != null ? (PalTypeDbgNatStack[])this.natStackEntries.clone() : null;
   }
}
