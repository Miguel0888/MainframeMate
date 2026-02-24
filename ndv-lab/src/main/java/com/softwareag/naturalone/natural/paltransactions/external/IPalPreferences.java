package com.softwareag.naturalone.natural.paltransactions.external;

public interface IPalPreferences {
   int getTimeOut();

   boolean replaceLineNoRefsWithLabels();

   boolean createLabelsInNewLine();

   String getLabelFormat();

   boolean checkTimeStamp();
}
