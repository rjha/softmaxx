package online.softmaxx.xapi.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class KeyGenerator {

    // Thread-safe, cryptographically strong random engine instance
    private static final SecureRandom RANDOM = new SecureRandom();
    // 24 bytes of data translates to 
    // roughly 32 characters in a Base64 encoding string
    private static final int ENTROPY_BYTES = 24; 

    public static String generateToken() {

        final byte[] bytes = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        
        // Generates a clean, URL-safe string without padding characters (=)
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return token;

    }
}

