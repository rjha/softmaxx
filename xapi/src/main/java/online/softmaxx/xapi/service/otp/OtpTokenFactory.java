package online.softmaxx.xapi.service.otp;

import java.security.SecureRandom;
import java.util.stream.Collectors;


public final class OtpTokenFactory {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NUMERIC_CHARS = "0123456789";
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private OtpTokenFactory() {}

    public static String generate(final OtpType type) {

        if (type == null) {
            throw new IllegalArgumentException("OtpType cannot be null");
        }

        final String characterPool = type.isAlphanumeric() ? ALPHANUMERIC_CHARS : NUMERIC_CHARS;
        return RANDOM.ints(type.length(), 0, characterPool.length())
                     .mapToObj(characterPool::charAt)
                     .map(Object::toString)
                     .collect(Collectors.joining());
    }
}

