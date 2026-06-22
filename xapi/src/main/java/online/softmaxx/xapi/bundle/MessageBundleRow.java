package online.softmaxx.xapi.bundle;

// MessageKey interface is composed of 
// [MESSAGE_TOKEN + KEY_NAME]

public interface MessageBundleRow {
    String getCode();
    String getKeyName();
    String token();   
}

