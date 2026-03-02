package de.bund.zrb.ndv.core.type;

import de.bund.zrb.ndv.core.api.IPalTypeOperation;

public final class PalTypeOperation extends PalType implements IPalTypeOperation {
    public static final int SUBKEY_RAW = 3;
    public static final int SUBKEY_STEPLIBS = 4;
    public static final int SUBKEY_LIBSTAT_REBUILD = 5;
    public static final int SUBKEY_SEARCH_BY_LONGNAME = 8;
    public static final int SUBKEY_SEARCH_LINKED_DDMS = 16;
    public static final int SUBKEY_SEARCH_LINKED_DDM_INFO = 32;
    public static final int SUBKEY_CHECK = 2;
    public static final int SUBKEY_SAVE = 4;
    public static final int SUBKEY_EXECUTE = 6;
    public static final int SUBKEY_DEBUG = 7;
    public static final int SUBKEY_READ = 10;
    public static final int SUBKEY_READDDM = 11;
    public static final int SUBKEY_LOGON = 12;
    public static final int SUBKEY_GENDDM = 17;
    public static final int SUBKEY_LIST = 8;
    public static final int SUBKEY_EDIT = 21;
    public static final int SUBKEY_EDITDDM = 23;
    public static final int SUBKEY_LISTDDM = 24;
    public static final int SUBKEY_CHECK_NO_SRC = 28;
    public static final int SUBKEY_STEPINTO = 1;
    public static final int SUBKEY_STEPOVER = 2;
    public static final int SUBKEY_STEPRETURN = 3;
    public static final int SUBKEY_RESUME = 4;
    public static final int SUBKEY_DASRECORD = 0;
    public static final int SUBKEY_DASWAIT = 1;
    public static final int SUBKEY_DASATTACH = 2;
    public static final int SUBKEY_DASDEBUG = 3;
    public static final int SUBKEY_DASTERMINATE = 4;
    public static final int SUBKEY_DBGASINGLE = 0;
    public static final int SUBKEY_DBGACLIENT = 1;
    public static final int SUBKEY_SPYSET = 1;
    public static final int SUBKEY_SPYDEL = 2;
    public static final int SUBKEY_SPYMOD = 3;
    public static final int SUBKEY_BPBEGIN = 4;
    public static final int SUBKEY_BPEND = 5;
    public static final int SUBKEY_BPDELALL = 6;
    public static final int SUBKEY_BPACTALL = 7;
    public static final int SUBKEY_BPDEACTALL = 8;
    public static final int SUBKEY_WPDELALL = 9;
    public static final int SUBKEY_WPACTALL = 10;
    public static final int SUBKEY_WPDEACTALL = 11;
    public static final int SUBKEY_NATPARMGET = 1;
    public static final int SUBKEY_NATPARMPUT = 2;
    public static final int SUBKEY_SYSVARGET = 1;
    public static final int SUBKEY_SYSVARPUT = 2;
    public static final int SUBKEY_UNKNOWN = 0;
    public static final int SUBKEY_PRIVATEMODEFORMAT = 2;
    public static final int SUBKEY_SHAREDMODEFORMAT = 1;
    public static final int FLAG_INI = 0;
    public static final int FLAG_MAP = 1;
    private int subKey;
    private int flags;
    private String clientId;
    private String userId;
    private int transactionId;

    public PalTypeOperation(int transactionId) {
        this.clientId = "";
        this.userId = "";
        super.type = 2;
        this.transactionId = transactionId;
    }

    public PalTypeOperation() {
        this.clientId = "";
        this.userId = "";
        super.type = 2;
    }

    public PalTypeOperation(int transactionId, int subKey) {
        this();
        this.subKey = subKey;
        this.transactionId = transactionId;
    }

    public void serialize() {
        this.intToBuffer(this.transactionId);
        this.intToBuffer(this.subKey);
        this.intToBuffer(this.flags);
        this.stringToBuffer(this.clientId);
        this.stringToBuffer(this.userId);
    }

    public void restore() {
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setSubKey(int subKey) {
        this.subKey = subKey;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public final int getFlags() {
        return this.flags;
    }

    public final void setFlags(int flags) {
        this.flags |= flags;
    }
}

