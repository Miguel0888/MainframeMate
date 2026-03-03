package de.bund.zrb.ndv.core.api;

public interface ICompOpt {
   int getFlags();

   int getSourceLinelength();

   int getMaxprec();

   void setFlags(int var1);

   void resetFlags(int var1);

   void setMaxprec(int var1);
}
