package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IPalTypeDbmsInfo;

import java.io.IOException;

public interface IServerConfiguration {
    boolean isZeroPrinting();
    void setZeroPrinting();
    boolean isFillerCharacterProtected();
    void setFillerCharacterProtected();
    boolean isTranslateOutput();
    void setTranslateOutput();
    boolean isMessageLineTop();
    void setMessageLineTop();
    boolean isMessageLineBottom();
    void setMessageLineBottom();

    char getDateFormatOutput();
    void setDateFormatOutput(char fmt);
    char getDateFormatStack();
    void setDateFormatStack(char fmt);
    char getDateFormatTitle();
    void setDateFormatTitle(char fmt);
    char getPm();
    void setPm(char pm);
    char getDateFormat();
    void setDateFormat(char fmt);

    int getLineSize();
    void setLineSize(int size);
    int getPageSize();
    void setPageSize(int size);
    int getSpacingFactor();
    void setSpacingFactor(int factor);

    char getTermCommandChar();
    void setTermCommandChar(char c);
    char getDecimalChar();
    void setDecimalChar(char c);
    char getInputAssignment();
    void setInputAssignment(char c);
    char getInputDelimiter();
    void setInputDelimiter(char c);
    char getThousandSeperator();
    void setThousandSeperator(char c);

    int getProcessingLoopLimit();
    void setProcessingLoopLimit(int limit);
    boolean getDynamicThousandsSeparator();
    void setDynamicThousandsSeparator(boolean value);
    boolean getKeywordChecking();
    void setKeywordChecking(boolean value);
    boolean getCALLNATParameterChecking();
    void setCALLNATParameterChecking(boolean value);
    boolean getDatabaseShortName();
    void setDatabaseShortName(boolean value);
    boolean getFormatSpecification();
    void setFormatSpecification(boolean value);
    boolean getStructuredMode();
    void setStructuredMode(boolean value);
    boolean getRenConst();
    void setRenConst(boolean value);

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
    void setLanguageCode(int code);
    boolean getPrivateMode();
    void setPrivateMode(boolean mode);
    int getMaxyear();
    void setMaxyear(int year);
    int getMaxprec();
    void setMaxprec(int prec);

    void sendToServer() throws IOException, PalResultException;
}
