package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.paltransactions.external.IPalTypeSQLAuthentification;

/** Stub — SQL-Authentifizierung nicht unterstützt (nur ADABAS). */
public class PalTypeSQLAuthentification extends PalType implements IPalTypeSQLAuthentification {
    @Override public void serialize() { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public void restore()   { throw new UnsupportedOperationException("Not implemented yet"); }
    @Override public int get()        { return 26; }

    @Override public String getTitle() { return null; }
    @Override public void setTitle(String v) {}
    @Override public String getText() { return null; }
    @Override public void setText(String v) {}
    @Override public String getPrompt1() { return null; }
    @Override public void setPrompt1(String v) {}
    @Override public String getPrompt2() { return null; }
    @Override public void setPrompt2(String v) {}
    @Override public String getUid() { return null; }
    @Override public void setUid(String v) {}
    @Override public String getPwd() { return null; }
    @Override public void setPwd(String v) {}
    @Override public int getLengthUid() { return 0; }
    @Override public void setLengthUid(int v) {}
    @Override public int getLengthPwd() { return 0; }
    @Override public void setLengthPwd(int v) {}
}
