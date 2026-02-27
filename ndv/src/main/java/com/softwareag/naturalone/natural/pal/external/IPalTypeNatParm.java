package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;
import com.softwareag.naturalone.natural.pal.PalTypeNatParm;

public interface IPalTypeNatParm extends IPalType {
   int P_COPT_CC = 1;
   int P_COPT_DU = 2;
   int P_COPT_DUF = 4;
   int P_COPT_DBSHORT = 64;
   int P_COPT_EMBIG = 128;
   int P_COPT_EMLITTLE = 256;
   int P_COPT_DUSNAP = 262144;
   int P_COPT_DUABEND = 524288;

   void setLimit(PalTypeNatParm.Limit var1);

   void setReport(PalTypeNatParm.Report var1);

   void setFldApp(PalTypeNatParm.FldApp var1);

   void setCharAssign(PalTypeNatParm.CharAssign var1);

   void setErr(PalTypeNatParm.Err var1);

   void setCompOpt(PalTypeNatParm.CompOpt var1);

   void setRpc(PalTypeNatParm.Rpc var1);

   void setBuffSize(PalTypeNatParm.BuffSize var1);

   void setRegional(PalTypeNatParm.Regional var1);

   PalTypeNatParm.Limit getLimit();

   PalTypeNatParm.BuffSize getBuffSize();

   PalTypeNatParm.CharAssign getCharAssign();

   PalTypeNatParm.CompOpt getCompOpt();

   PalTypeNatParm.Err getErr();

   PalTypeNatParm.FldApp getFldApp();

   void setRecordIndex(int var1);

   int getRecordIndex();

   PalTypeNatParm.Regional getRegional();

   PalTypeNatParm.Report getReport();

   PalTypeNatParm.Rpc getRpc();
}
