package online.softmaxx.xapi.bundle;

public enum MessageToken {

    APP_ERR("__APP_ERR__"),
    SYS_ERR("__SYS_ERR__");

    private final String value;

    // Private constructor
    MessageToken(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

}