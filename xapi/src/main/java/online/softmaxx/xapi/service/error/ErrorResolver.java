package online.softmaxx.xapi.service.error;


import java.util.Locale;
import java.util.ResourceBundle;

public final class ErrorResolver {

    private static final String BUNDLE_BASE_NAME = "errors";
    private static final String TOKEN_PREFIX = "APP_ERR:";

    private ErrorResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Data record used to pass both the API code and the resolved message text cleanly.
     */
    public record ErrorDetails(String apiCode, String message) {}

    /**
     * Serializes an enum token into a safe string format that can be passed inside standard exceptions.
     */
    public static String toToken(final AppExceptionCode code) {
        return TOKEN_PREFIX + code.name();
    }

    /**
     * Resolves a plain localized message string directly from the resource bundle using the Enum token.
     */
    public static String getMessage(final AppExceptionCode code, final Locale locale) {
        final Locale targetLocale = (locale == null) ? Locale.getDefault() : locale;
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, targetLocale).getString(code.getBundleKey());
        } catch (final Exception e) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT).getString(code.getBundleKey());
        }
    }

    /**
     * Resolves complete ErrorDetails (Code + Message) from a raw exception message string.
     * Supports both structured enum tokens and generic plain text.
     */
    public static ErrorDetails resolve(final String exceptionMessage, final Locale locale) {
        if (exceptionMessage != null && exceptionMessage.startsWith(TOKEN_PREFIX)) {
            try {
                final String enumName = exceptionMessage.substring(TOKEN_PREFIX.length());
                final AppExceptionCode code = AppExceptionCode.valueOf(enumName);
                return new ErrorDetails(code.getApiCode(), getMessage(code, locale));
            } catch (final IllegalArgumentException e) {
                // Fallback if the token string was somehow malformed
                return new ErrorDetails(AppExceptionCode.UNKNOWN_FAILURE.getApiCode(), exceptionMessage);
            }
        }
        // If it's a plain vanilla string, pass it through as a general system error code
        return new ErrorDetails(AppExceptionCode.UNKNOWN_FAILURE.getApiCode(), 
                exceptionMessage != null ? exceptionMessage : "No message provided.");
    }
}

