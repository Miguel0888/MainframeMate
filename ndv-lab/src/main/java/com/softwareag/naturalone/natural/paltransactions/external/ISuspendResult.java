package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.PalTypeDbgNatStack;
import com.softwareag.naturalone.natural.pal.PalTypeDbgStackFrame;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgSpy;
import com.softwareag.naturalone.natural.pal.external.IPalTypeDbgStatus;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;
import com.softwareag.naturalone.natural.pal.external.IPalTypeStream;

public interface ISuspendResult {
   IPalTypeNotify getNotify();

   void setNotify(IPalTypeNotify var1);

   byte getDecimalCharacter();

   IPalTypeDbgSpy getSpy();

   PalTypeDbgStackFrame[] getStackFrames();

   PalTypeDbgNatStack[] getNatStackEntries();

   void setStackFrames(PalTypeDbgStackFrame[] var1);

   IPalTypeDbgStatus getStatus();

   PalResultException getException();

   void setException(PalResultException var1);

   IPalTypeStream getScreen();

   void setScreen(IPalTypeStream var1);
}
