package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeMonitorInfo extends IPalType {
   String getSessionId();

   void setSessionId(String var1);

   String getEventFilter();

   void setEventFilter(String var1);
}
