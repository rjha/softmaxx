package online.softmaxx.xapi.bundle;


public enum SysErrorCode implements MessageBundleRow {

    MALFORMED_JSON("SYS-400", "SYS_MALFORMED_JSON"),
    DATABASE_CRASH("DB-500", "DB_GENERIC"),
    DUPLICATE_PHONE("DB-201", "DB_DUP_PHONE"),
    DUPLICATE_EMAIL("DB-202", "DB_DUP_EMAIL"),
    UNKNOWN_FAILURE("SYS-500", "SYS_UNEXPECTED"),
    TOO_MANY_REQUESTS("SYS-409", "SYS_TOO_MANY_REQUESTS");

    private final String code;
    private final String keyName;

    SysErrorCode(final String code, final String bundleKey) {
        this.code = code;
        this.keyName = bundleKey;
    }

    @Override public String getCode() { return this.code; }
    @Override public String getKeyName() { return this.keyName; }

    @Override
    public String token() {
        return MessageToken.SYS_ERR.getValue() + ":" + this.name();
    }
}

