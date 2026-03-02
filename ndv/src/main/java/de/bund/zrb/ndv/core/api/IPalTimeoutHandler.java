package de.bund.zrb.ndv.core.api;

public interface IPalTimeoutHandler {
   boolean continueOperation();

   void addResultListener(IPalTimeoutResultListener var1);
}
