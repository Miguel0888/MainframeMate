package de.bund.zrb.ndv.core.api;

public interface ILimit {
   int getFlags();

   int getMaximumCPUTime();

   int getPageDataSet();

   int getProcessingLoopLimit();

   void setProcessingLoopLimit(int var1);
}
