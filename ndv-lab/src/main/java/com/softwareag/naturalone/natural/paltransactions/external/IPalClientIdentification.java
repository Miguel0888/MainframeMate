package com.softwareag.naturalone.natural.paltransactions.external;

public interface IPalClientIdentification {
   int WEB_IO_VERSION = 67240193;
   /** @deprecated */
   @Deprecated
   int NFN_VERSION = 821;
   int NATONE_VERSION = 838;
   /** @deprecated */
   @Deprecated
   int PALCLIENTID_NFN = 32;
   int PALCLIENTID_ONE = 32;

   int getNdvClientVersion();

   int getNdvClientId();

   int getWebIOVersion();
}
