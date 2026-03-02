package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeCP extends IPalType {
   String getCodePage();

   void setCodePage(String var1);
}
