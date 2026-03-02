package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.IPalTypeDbmsInfo;

import java.io.IOException;

/**
 * Schnittstelle für die Server-Konfiguration.
 */
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
    void setDateFormatOutput(char c);
    void setDateFormatStack(char c);
    void setDateFormatTitle(char c);
    void setPm(char c);
    void setDateFormat(char c);

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

    void setLineSize(int size);
    void setPageSize(int size);
    void setSpacingFactor(int factor);
    void setTermCommandChar(char c);
    void setDecimalChar(char c);
    void setInputAssignment(char c);
    void setInputDelimiter(char c);
    void setThousandSeperator(char c);
    void setProcessingLoopLimit(int limit);

    void setDynamicThousandsSeparator(boolean flag);
    void setKeywordChecking(boolean flag);
    void setCALLNATParameterChecking(boolean flag);
    void setDatabaseShortName(boolean flag);
    void setFormatSpecification(boolean flag);
    void setStructuredMode(boolean flag);
    void setRenConst(boolean flag);

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
    void setLanguageCode(int code);

    void setPrivateMode(boolean flag);
    boolean getPrivateMode();

    void setMaxyear(int year);
    int getMaxyear();

    void setMaxprec(int prec);
    int getMaxprec();
}

