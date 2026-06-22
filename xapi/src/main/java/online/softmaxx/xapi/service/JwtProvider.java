package online.softmaxx.xapi.service;



import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
// Contains the ALG_RS256 static constant string
import io.helidon.security.jwt.jwk.JwkRSA; 

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

public final class JwtProvider {

    private static final System.Logger LOGGER = System.getLogger(JwtProvider.class.getName());
    private static final long EXPIRATION_PERIOD_SECONDS = 86400L; // 24 Hours

    private JwtProvider() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    
    // Generates a fully signed, asymmetric specification-compliant 
    // MicroProfile JWT string.
    public static String generateToken(final String userKey) {
        LOGGER.log(System.Logger.Level.INFO, "Generating signed asymmetric JWT for subject: {0}", userKey);
        
        final Instant currentTime = Instant.now();
        final Instant expiryTime = currentTime.plusSeconds(EXPIRATION_PERIOD_SECONDS);

        // 1. Build claims and pass the standard Helidon "RS256" constant string natively
        final Jwt jwt = Jwt.builder()
                .algorithm(JwkRSA.ALG_RS256) // FIX: Uses the valid "RS256" string constant layout
                .issuer("https://softmaxx.online")
                .subject(userKey)
                .addUserGroup("XAPI_USER") 
                .addPayloadClaim("user_key", userKey)
                .issueTime(currentTime)
                .expirationTime(expiryTime)
                .build();

        try {

            // 2. Read private key file securely
            final String rawContent = Files.readString(Paths.get(".keys/helidon4.pem"));
            
            final String keyContent = rawContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", ""); 

            final byte[] privateKeyBytes = Base64.getDecoder().decode(keyContent);
            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            final KeyFactory kf = KeyFactory.getInstance("RSA");
            
            final java.security.interfaces.RSAPrivateKey rsaPrivateKey = 
                    (java.security.interfaces.RSAPrivateKey) kf.generatePrivate(keySpec);

            // 3. Build the asymmetric JWK signature token descriptor
            final JwkRSA jwk = JwkRSA.builder()
                    .privateKey(rsaPrivateKey)
                    .keyId("helidon4.pem")
                    .build();

            // 4. Construct and return the signed three-part base64 string payload
            final SignedJwt signedJwt = SignedJwt.sign(jwt, jwk);
            return signedJwt.tokenContent();

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Root signature failure intercepted inside JwtProvider", e);
            throw new RuntimeException("Failed to secure and cryptographically sign authentication payload string.", e);
        }
    }
}
