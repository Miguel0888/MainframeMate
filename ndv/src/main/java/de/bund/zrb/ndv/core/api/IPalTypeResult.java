package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeResult extends IPalType {
   int getNaturalResult();

   int getSystemResult();
}
