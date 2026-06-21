package online.softmaxx.xapi.auth;



import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordService {

    private static final System.Logger LOGGER = System.getLogger(PasswordService.class.getName());

    private static final int ARGON2_TYPE = Argon2Parameters.ARGON2_id;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; 
    private static final int PARALLELISM = 4;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordService() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String hashPassword(final String plainPassword) {

        LOGGER.log(System.Logger.Level.DEBUG, "Generating password hash...");
        final byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        final byte[] hash = generateRawHash(plainPassword, salt);

        final String encodedSalt = Base64.getEncoder().encodeToString(salt).replace("=", "");
        final String encodedHash = Base64.getEncoder().encodeToString(hash).replace("=", "");

        return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s", 
                MEMORY_KB, ITERATIONS, PARALLELISM, encodedSalt, encodedHash);

    }

    public static boolean verifyPassword(final String plainPassword, final String storedHash) {

        if (plainPassword == null || storedHash == null || !storedHash.startsWith("$argon2id$")) {
            return false;
        }

        try {
            final String[] parts = storedHash.split("\\$");
            if (parts.length < 6) return false;

            final String paramStr = parts[3]; 
            final String saltStr = parts[4];  
            final String hashStr = parts[5];  

            int memory = 65536;
            int iterations = 3;
            int parallelism = 4;
            
            for (final String param : paramStr.split(",")) {
                final String[] kv = param.split("=");
                if ("m".equals(kv[0])) memory = Integer.parseInt(kv[1]);
                if ("t".equals(kv[0])) iterations = Integer.parseInt(kv[1]);
                if ("p".equals(kv[0])) parallelism = Integer.parseInt(kv[1]);
            }

            final byte[] salt = Base64.getDecoder().decode(addPadding(saltStr));
            final byte[] expectedHash = Base64.getDecoder().decode(addPadding(hashStr));

            final Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(iterations)
                    .withMemoryAsKB(memory)
                    .withParallelism(parallelism)
                    .withSalt(salt);

            final Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());

            final byte[] actualHash = new byte[expectedHash.length];
            generator.generateBytes(plainPassword.toCharArray(), actualHash, 0, actualHash.length);

            int result = 0;
            for (int i = 0; i < expectedHash.length; i++) {
                result |= expectedHash[i] ^ actualHash[i];
            }
            return result == 0;

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error occurred during password verification matrix execution", e);
            return false;
        }

    }

    private static byte[] generateRawHash(final String password, final byte[] salt) {

        final Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM)
                .withSalt(salt);

        final Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        final byte[] result = new byte[HASH_LENGTH_BYTES];
        generator.generateBytes(password.toCharArray(), result, 0, result.length);
        return result;
        
    }

    private static String addPadding(final String base64) {
        final int paddingNeeded = (4 - (base64.length() % 4)) % 4;
        return base64 + "=".repeat(paddingNeeded);
    }
}
