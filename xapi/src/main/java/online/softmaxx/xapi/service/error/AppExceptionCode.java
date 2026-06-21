package online.softmaxx.xapi.service.error;


public enum AppExceptionCode {

    // Validation Error Mappings (API Code, Clean Resource Bundle Key)
    USERNAME_BLANK("VAL-101", "VAL_USER_BLANK"),
    PASSWORD_BLANK("VAL-102", "VAL_PASS_BLANK"),
    PHONE_BLANK("VAL-103", "VAL_PHONE_BLANK"),
    COUNTRY_BLANK("VAL-104", "VAL_COUNTRY_BLANK"),
    LOCALE_BLANK("VAL-105", "VAL_LOCALE_BLANK"),
    
    // Database Conflict Mappings
    DUPLICATE_PHONE("DB-201", "DB_DUP_PHONE"),
    DUPLICATE_EMAIL("DB-202", "DB_DUP_EMAIL"),
    DATABASE_CRASH("DB-500", "DB_GENERIC"),
    
    // System General Mappings
    MALFORMED_JSON("SYS-400", "SYS_MALFORMED_JSON"),
    UNKNOWN_FAILURE("SYS-500", "SYS_UNEXPECTED");

    private final String apiCode;
    private final String bundleKey;

    AppExceptionCode(final String apiCode, final String bundleKey) {
        this.apiCode = apiCode;
        this.bundleKey = bundleKey;
    }

    public String getApiCode() { 
        return this.apiCode; 
    }

    public String getBundleKey() { 
        return this.bundleKey; 
    }
    
}
