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

    
    // Resolves metadata configurations using type-safe MessageToken case routes.
     
    public static MessageDetail resolve(final MessageToken messageToken, 
        final String errorMessage, 
        final Locale locale) {

        if (messageToken == null || errorMessage == null) {
            return new MessageDetail(SysErrorCode.UNKNOWN_FAILURE.getCode(),errorMessage);
        }

        try {

            final String enumName = errorMessage.substring(messageToken.name().length() + 1);
            final MessageBundleRow bundleRow;

            switch (messageToken) {
                case __APP_ERR__ -> {
                    bundleRow = AppErrorCode.valueOf(enumName);
                    return new MessageDetail(bundleRow.getCode(), getRawMessage(bundleRow.getKeyName(), locale));
                }

                case __SYS_ERR__ -> {
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
