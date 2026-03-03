package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeMonitorInfo extends IPalType {
   String getSessionId();

   void setSessionId(String var1);

   String getEventFilter();

   void setEventFilter(String var1);
}
