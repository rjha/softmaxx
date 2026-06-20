package online.softmaxx.xapi.auth;



import jakarta.enterprise.context.ApplicationScoped;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PasswordService {

    private static final int ARGON2_TYPE = Argon2Parameters.ARGON2_id;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; // 64MB
    private static final int PARALLELISM = 4;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Hashes a plain-text password using pure Bouncy Castle Argon2id.
     * Returns a standard format string starting with $argon2id$
     */
    public String hashPassword(String plainPassword) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] hash = generateRawHash(plainPassword, salt);

        String encodedSalt = Base64.getEncoder().encodeToString(salt)
                .replace("=", ""); // Strip padding to match standard formatting cleanly
        String encodedHash = Base64.getEncoder().encodeToString(hash)
                .replace("=", "");

        // Returns the globally recognized standard format layout string
        return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s", 
                MEMORY_KB, ITERATIONS, PARALLELISM, encodedSalt, encodedHash);
    }

    /**
     * Verifies an incoming login password text against the standard database hash.
     */
    public boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || !storedHash.startsWith("$argon2id$")) {
            return false;
        }

        try {
            // Split by literal '$' character
            String[] parts = storedHash.split("\\$");
            if (parts.length < 6) return false;

            // parts[0] is empty due to initial '$'
            // parts[1] = "argon2id", parts[2] = "v=19"
            String paramStr = parts[3]; // "m=65536,t=3,p=4"
            String saltStr = parts[4];  // Base64 encoded salt
            String hashStr = parts[5];  // Base64 encoded hash

            // Parse cryptographic config parameters out of the string dynamically
            int memory = 65536;
            int iterations = 3;
            int parallelism = 4;
            
            for (String param : paramStr.split(",")) {
                String[] kv = param.split("=");
                if (kv[0].equals("m")) memory = Integer.parseInt(kv[1]);
                if (kv[0].equals("t")) iterations = Integer.parseInt(kv[1]);
                if (kv[0].equals("p")) parallelism = Integer.parseInt(kv[1]);
            }

            // Restore required Base64 padding for the safe decoder array instantiation
            byte[] salt = Base64.getDecoder().decode(addPadding(saltStr));
            byte[] expectedHash = Base64.getDecoder().decode(addPadding(hashStr));

            Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(iterations)
                    .withMemoryAsKB(memory)
                    .withParallelism(parallelism)
                    .withSalt(salt);

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());

            byte[] actualHash = new byte[expectedHash.length];
            generator.generateBytes(plainPassword.toCharArray(), actualHash, 0, actualHash.length);

            // Time-constant comparison to completely block timing side-channel exploits
            int result = 0;
            for (int i = 0; i < expectedHash.length; i++) {
                result |= expectedHash[i] ^ actualHash[i];
            }
            return result == 0;

        } catch (Exception e) {
            return false;
        }
    }

    private byte[] generateRawHash(String password, byte[] salt) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] result = new byte[HASH_LENGTH_BYTES];
        generator.generateBytes(password.toCharArray(), result, 0, result.length);
        return result;
    }

    private String addPadding(String base64) {
        int paddingNeeded = (4 - (base64.length() % 4)) % 4;
        return base64 + "=".repeat(paddingNeeded);
    }
}
