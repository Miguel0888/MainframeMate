package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IPalTypeSourceCP extends IPalType {
   void convert(String var1) throws UnsupportedEncodingException, IOException;
}
