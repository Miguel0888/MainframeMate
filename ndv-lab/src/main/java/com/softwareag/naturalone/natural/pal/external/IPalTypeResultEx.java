package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

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
