package com.softwareag.naturalone.natural.pal.external;

public interface IPalTimeoutHandler {
   boolean continueOperation();

   void addResultListener(IPalTimeoutResultListener var1);
}
