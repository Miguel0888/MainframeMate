package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IBuffSize;
import com.softwareag.naturalone.natural.pal.external.ICharAssign;
import com.softwareag.naturalone.natural.pal.external.ICompOpt;
import com.softwareag.naturalone.natural.pal.external.IErr;
import com.softwareag.naturalone.natural.pal.external.IFldApp;
import com.softwareag.naturalone.natural.pal.external.ILimit;
import com.softwareag.naturalone.natural.pal.external.IPalTypeNatParm;
import com.softwareag.naturalone.natural.pal.external.IRegional;
import com.softwareag.naturalone.natural.pal.external.IReport;
import com.softwareag.naturalone.natural.pal.external.IRpc;
import java.io.Serializable;

public interface INatParm extends Serializable {
   IReport getReport();

   ICharAssign getCharAssign();

   IFldApp getFldApp();

   ICompOpt getCompOpt();

   ILimit getLimit();

   IRegional getRegional();

   IRpc getRpc();

   IBuffSize getBuffSize();

   IErr getErr();

   IPalTypeNatParm[] get(int var1);
}
