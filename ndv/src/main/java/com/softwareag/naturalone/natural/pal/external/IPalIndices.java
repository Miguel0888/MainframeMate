package com.softwareag.naturalone.natural.pal.external;

public interface IPalIndices {
   int INIT = 0;
   int ARRAY = 1;
   int ARRAY_ELEMENT = 2;
   int ARRAY_CHUNK = 3;
   int NOTMATERIALIZED_LOW1 = 1;
   int NOTMATERIALIZED_UPP1 = 2;
   int NOTMATERIALIZED_LOW2 = 4;
   int NOTMATERIALIZED_UPP2 = 8;
   int NOTMATERIALIZED_LOW3 = 16;
   int NOTMATERIALIZED_UPP3 = 32;

   boolean replace(IPalIndices var1);

   int getFlags();

   void setFlags(int var1);

   int[] getLower();

   void setLower(int[] var1);

   int getNumberDimensions();

   void setNumberDimensions(int var1);

   int getOccurences();

   void setOccurences(int var1);

   int[] getUpper();

   void setUpper(int[] var1);

   boolean isArray();

   boolean isArrayElement();

   boolean isArrayChunk();

   boolean isMaterialized();

   int getIndexType();

   void setIndexType(int var1);

   String toString();

   int getFirstVisbleDimension();

   void setFirstVisbleDimension(int var1);

   void setExpandDimension(int var1);

   int getExpandDimension();

   boolean equals(Object var1);

   boolean contains(Object var1);

   int hashCode();
}
