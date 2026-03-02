package de.bund.zrb.ndv.core.api;

import de.bund.zrb.ndv.core.type.IPalType;

public interface IPalTypeDbmsInfo extends IPalType {
   int ADABAS = 1;
   int SQL = 2;
   int XML = 3;
   int ADABAS2 = 4;

   int getDbid();

   int getType();

   String getParameter();

   boolean isTypeSql();

   boolean isTypeAdabas();

   boolean isTypeXml();

   boolean isTypeAdabas2();
}
