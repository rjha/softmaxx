package online.softmaxx.xapi.service.error;


import java.util.Locale;
import java.util.ResourceBundle;

public final class MessageResolver {

    private static final String BUNDLE_BASE_NAME = "app_messages";

    private MessageResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public record MessageDetail(String code, String message) {}

    private static String getRawMessage(final AppMessage message, final Locale locale) {

        final Locale targetLocale = (locale == null) ? Locale.getDefault() : locale;

        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, targetLocale).getString(message.getMessageKey());
        } catch (final Exception e) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT).getString(message.getMessageKey());
        }
    }

    
    public static MessageDetail resolve(final String rawMessage, final Locale locale) {

        if (rawMessage != null && rawMessage.startsWith(AppMessage.TOKEN_PREFIX)) {

            try {

                final String enumName = rawMessage.substring(AppMessage.TOKEN_PREFIX.length());
                final AppMessage appMessage = AppMessage.valueOf(enumName);
                return new MessageDetail(appMessage.getCode(), getRawMessage(appMessage, locale));

            } catch (final IllegalArgumentException e) {
                // Fallback if the token string was somehow malformed
                return new MessageDetail(AppMessage.UNKNOWN_FAILURE.getCode(), rawMessage);
            }

        }

        // If it's a plain vanilla string, pass it through as a general system error code
        return new MessageDetail(AppMessage.UNKNOWN_FAILURE.getCode(), 
                rawMessage != null ? rawMessage : "No message provided.");
    }
}

