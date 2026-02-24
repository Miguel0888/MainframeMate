package com.softwareag.naturalone.natural.paltransactions.external;

import java.io.Serializable;

public interface IPalTypeSQLAuthentification extends Serializable {
   String getTitle();

   void setTitle(String var1);

   String getText();

   void setText(String var1);

   String getPrompt1();

   void setPrompt1(String var1);

   String getPrompt2();

   void setPrompt2(String var1);

   String getUid();

   void setUid(String var1);

   String getPwd();

   void setPwd(String var1);

   int getLengthUid();

   void setLengthUid(int var1);

   int getLengthPwd();

   void setLengthPwd(int var1);
}
