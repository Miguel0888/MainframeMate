package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.type.IPalType;

public interface IPalTypeMonitorInfo extends IPalType {
   String getSessionId();

   void setSessionId(String var1);

   String getEventFilter();

   void setEventFilter(String var1);
}
