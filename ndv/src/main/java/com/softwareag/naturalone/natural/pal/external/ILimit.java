package com.softwareag.naturalone.natural.pal.external;

public interface ILimit {
   int getFlags();

   int getMaximumCPUTime();

   int getPageDataSet();

   int getProcessingLoopLimit();

   void setProcessingLoopLimit(int var1);
}
