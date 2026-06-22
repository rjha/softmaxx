package online.softmaxx.xapi.bundle;


import java.util.Locale;
import java.util.ResourceBundle;


public final class MessageResolver {

    private static final String BUNDLE_BASE_NAME = "app_messages";

    private MessageResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String getRawMessage(final String bundleKey, final Locale locale) {

        final Locale targetLocale = (locale == null) ? Locale.getDefault() : locale;
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, targetLocale).getString(bundleKey);
        } catch (final Exception e) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT).getString(bundleKey);
        }

    }

    
    // The exceptions are raised using a MessageBundleRow.token() method that 
    // adds MessageToken.getValue():enumName prefix to the error message. When 
    // checking the error message, we look for messageToken.getValue() prefix.
    // such error messages are passed to this method. we extract the enumName 
    // from the error message and use it to get the enum code and keyName. 
     
    public static MessageDetail resolve(final MessageToken token, 
        final String errorMessage, 
        final Locale locale) {

        if (token == null || errorMessage == null) {
            return new MessageDetail(SysErrorCode.UNKNOWN_FAILURE.getCode(),errorMessage);
        }

        try {

            final String enumName = errorMessage.substring(token.getValue().length() + 1);
            final MessageBundleRow bundleRow;

            switch (token) {
                case APP_ERR -> {
                    bundleRow = AppErrorCode.valueOf(enumName);
                    return new MessageDetail(bundleRow.getCode(), getRawMessage(bundleRow.getKeyName(), locale));
                }

                case SYS_ERR-> {
                    bundleRow = SysErrorCode.valueOf(enumName);
                    return new MessageDetail(bundleRow.getCode(), getRawMessage(bundleRow.getKeyName(), locale));
                }
            }

        } catch (final Exception ex) {
            // Drop through safely to unknown default layout if valueOf breaks
        }

        return new MessageDetail(SysErrorCode.UNKNOWN_FAILURE.getCode(), errorMessage);
    }
}
