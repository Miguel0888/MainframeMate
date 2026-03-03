package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.impl.type.IPalType;

public interface IPalTypeResultEx extends IPalType {
   String getShortText();

   int getRow();

   int getColumn();

   int getLengthSymbol();

   String getName();

   String getLibrary();

   int getKind();

   int getType();

   int getDatabaseId();

   int getFileNumber();

   String getSystemText();
}
