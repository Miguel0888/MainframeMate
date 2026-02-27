package com.softwareag.naturalone.natural.pal.external;

import com.softwareag.naturalone.natural.pal.IPalType;

public interface IPalTypeNotify extends IPalType {
   int NONE = 0;
   int CONTINUE = 4;
   int ABORT = 5;
   int NEXT = 6;
   int TERMINATE = 7;
   int ERROR = 9;
   int ABORT_DEL = 12;
   int SEND = 13;
   int NON_CONVERSATIONAL = 14;
   int NATRMT_PROGRESS = 17;
   int NOFILES = 18;

   int getNotification();
}
