package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeConnect extends IPalType {
   String getPassword();

   String getUser();
}
