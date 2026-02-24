package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbmsInfo;
import java.io.IOException;

public interface IServerConfiguration {
   boolean isZeroPrinting();

   boolean isFillerCharacterProtected();

   boolean isTranslateOutput();

   boolean isMessageLineTop();

   boolean isMessageLineBottom();

   char getDateFormatOutput();

   char getDateFormatStack();

   char getDateFormatTitle();

   char getPm();

   char getDateFormat();

   void setZeroPrinting();

   void setFillerCharacterProtected();

   void setTranslateOutput();

   void setMessageLineTop();

   void setMessageLineBottom();

   void setDateFormatOutput(char var1);

   void setDateFormatStack(char var1);

   void setDateFormatTitle(char var1);

   void setPm(char var1);

   void setDateFormat(char var1);

   int getLineSize();

   int getPageSize();

   int getSpacingFactor();

   char getTermCommandChar();

   char getDecimalChar();

   char getInputAssignment();

   char getInputDelimiter();

   char getThousandSeperator();

   int getProcessingLoopLimit();

   boolean getDynamicThousandsSeparator();

   boolean getKeywordChecking();

   boolean getCALLNATParameterChecking();

   boolean getDatabaseShortName();

   boolean getFormatSpecification();

   boolean getStructuredMode();

   boolean getRenConst();

   void setLineSize(int var1);

   void setPageSize(int var1);

   void setSpacingFactor(int var1);

   void setTermCommandChar(char var1);

   void setDecimalChar(char var1);

   void setInputAssignment(char var1);

   void setInputDelimiter(char var1);

   void setThousandSeperator(char var1);

   void setProcessingLoopLimit(int var1);

   void setDynamicThousandsSeparator(boolean var1);

   void setKeywordChecking(boolean var1);

   void setCALLNATParameterChecking(boolean var1);

   void setDatabaseShortName(boolean var1);

   void setFormatSpecification(boolean var1);

   void setStructuredMode(boolean var1);

   void setRenConst(boolean var1);

   void sendToServer() throws IOException, PalResultException;

   char getNonDBFieldCharacter();

   char getSqlSeparatorCharacter();

   char getDynamicSourceCharacter();

   char getGlobalVariableCharacter();

   char getAlternatCaretCharacter();

   char[] geIdentifiertValid1st();

   char[] geIdentifiertValidSubSequent();

   char[] geObjectNameValid1st();

   char[] geObjectNameValidSubSequent();

   char[] geDdmNameValid1st();

   char[] geDdmNameValidSubSequent();

   char[] geLibraryNameValid1st();

   char[] geLibraryNameValidSubSequent();

   IPalTypeDbmsInfo[] getDbmsAssignments();

   int getLanguageCode();

   void setLanguageCode(int var1);

   void setPrivateMode(boolean var1);

   boolean getPrivateMode();

   void setMaxyear(int var1);

   int getMaxyear();

   void setMaxprec(int var1);

   int getMaxprec();
}
