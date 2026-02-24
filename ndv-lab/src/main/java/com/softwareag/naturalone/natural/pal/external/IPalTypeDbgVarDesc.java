package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgVarDesc extends IPalType {
   IPalTypeDbgVarDesc getInstance(IPalTypeDbgSyt var1);

   IPalIndices getIndices();

   IPalIndices[] getAllIndices();

   void setConvid(int var1);

   void setFlags(int var1);

   void setLength(int var1);

   void setOcxFormat(int var1);

   void setRange(int var1);

   void setRedef(boolean var1);

   void setStartOffset(int var1);

   void setId(int var1);

   void setQualifier(String var1);

   void setVariable(String var1);

   void setFormat(int var1);

   void setIndices(IPalIndices var1);
}
