package online.softmaxx.xapi.bundle;

/** 
* message_key is composed of message_token:key_name. 
* each message_key enum knows its own message token
* 
*/

public enum AppErrorCode implements MessageBundleRow {

    USERNAME_BLANK("VAL-101", "VAL_USER_BLANK"),
    PASSWORD_BLANK("VAL-102", "VAL_PASS_BLANK"),
    PHONE_BLANK("VAL-103", "VAL_PHONE_BLANK"),
    COUNTRY_BLANK("VAL-104", "VAL_COUNTRY_BLANK"),
    LOCALE_BLANK("VAL-105", "VAL_LOCALE_BLANK"),
    TOKEN_OTP_BLANK("VAL-106", "VAL_TOKEN_OTP_BLANK"),
    USER_NOT_FOUND("VAL-107", "VAL_USER_NOT_FOUND"),
    INVALID_CREDENTIALS("AUTH-101", "VAL_AUTH_FAILED");

    private final String code;
    private final String keyName;

    AppErrorCode(final String code, final String bundleKey) {
        this.code = code;
        this.keyName = bundleKey;
    }

    @Override public String getCode() { return this.code; }
    @Override public String getKeyName() { return this.keyName; }

    @Override
    public String token() {
        return MessageToken.APP_ERR.getValue() + ":" + this.name();
    }

}
