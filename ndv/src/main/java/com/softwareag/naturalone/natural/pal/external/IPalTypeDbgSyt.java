package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeDbgSyt extends IPalType {
   int OPT_NUM = 1;
   int OPT_PACK = 2;
   int OPT_INT = 3;
   int OPT_FLOAT = 4;
   int OPT_BIN = 5;
   int OPT_DATE = 6;
   int OPT_TIME = 7;
   int OPT_LOG = 8;
   int OPT_CV = 9;
   int OPT_ALPHA = 10;
   int OPT_GUI = 11;
   int OPT_OBJECT = 12;
   int OPT_UNICODE = 17;
   long serialVersionUID = 1L;
   int SYT_GROUP = 1;
   int SYT_VARRAY = 2;
   int SYT_XARRAY = 4;
   int SYT_READONLY = 8;
   int SYT_REDEF = 16;
   int SYT_REDEFBASE = 128;
   int SYT_LINEREF = 256;
   int SYT_NOSYMBOLIC = 1073741824;

   int getConvId();

   int getFlags();

   int getFormat();

   int getId();

   int getLength();

   int getLevel();

   int getLineReference();

   String getName();

   int getNumberOfElements();

   int getOcxFormat();

   int getPrecision();

   boolean isUnicode();

   boolean isGroup();

   boolean isNoSymbolic();

   boolean isVarray();

   boolean isDynamic();

   boolean isXarray();

   boolean isReadOnly();

   boolean isLineRef();

   boolean isRedef();

   boolean isRedefBase();

   IPalIndices getIndices();

   void setFlags(int var1);

   void setLevel(int var1);

   void setName(String var1);

   boolean equalsVariable(Object var1);

   boolean equalsLineReference(Object var1);

   boolean equalsLabel(Object var1);

   String getOutpFormatLength();

   void setIndices(IPalIndices var1);

   void setLineReference(int var1);
}
