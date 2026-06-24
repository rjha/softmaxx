package online.softmaxx.xapi.service;



import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
// Contains the ALG_RS256 static constant string
import io.helidon.security.jwt.jwk.JwkRSA;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import online.softmaxx.xapi.util.HelidonConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;;

public final class JwtProvider {

    private static final System.Logger LOGGER = System.getLogger(JwtProvider.class.getName());
    private static final long EXPIRATION_PERIOD_SECONDS = 86400L; // 24 Hours

    
    private JwtProvider() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    
    // Generates a fully signed, asymmetric specification-compliant 
    // MicroProfile JWT string.
    public static String generateToken(final String userKey) {

        final Optional<String> privateKeyPath = HelidonConfig.JWT_PRIVATE_KEY_PATH.get();
        final Optional<String> privateKeyName = HelidonConfig.JWT_PRIVATE_KEY_NAME.get();
        
        if (privateKeyPath.isEmpty()) {
            throw new IllegalStateException("helidon config JWT_PRIVATE_KEY_PATH is missing.");
        }

        if (privateKeyName.isEmpty()) {
            throw new IllegalStateException("helidon config JWT_PRIVATE_KEY_NAME is missing.");
        }
 
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
            final String rawContent = Files.readString(Paths.get(privateKeyPath.get()));
            
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
                    .keyId(privateKeyName.get())
                    .build();

            // 4. Construct and return the signed three-part base64 string payload
            final SignedJwt signedJwt = SignedJwt.sign(jwt, jwk);
            return signedJwt.tokenContent();

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Root signature failure intercepted inside JwtProvider", e);
            throw new RuntimeException("Failed to secure and cryptographically sign authentication payload", e);
        }

    }

    public static JsonObject getPublicKey() throws IOException {

        final Optional<String> publicKeyPath = HelidonConfig.JWT_PUBLIC_KEY_PATH.get();
        final Optional<String> publicKeyName = HelidonConfig.JWT_PUBLIC_KEY_NAME.get();

        if (publicKeyPath.isEmpty()) {
            throw new IllegalStateException("helidon config JWT_PUBLIC_KEY_PATH is missing.");
        }

        if (publicKeyName.isEmpty()) {
            throw new IllegalStateException("helidon config JWT_PUBLIC_KEY_NAME is missing.");
        }

        // public key is part of the classpath 
        try (final InputStream is = UserService.class.getResourceAsStream(publicKeyPath.get())) {
            if (is == null) {
                throw new IllegalStateException("JWT public key asset missing from classpath: " + publicKeyPath.get());
            }

            final String rawContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            final String keyContent = rawContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            final byte[] publicKeyBytes = Base64.getDecoder().decode(keyContent);
            final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            final KeyFactory kf = KeyFactory.getInstance("RSA");
            final java.security.interfaces.RSAPublicKey rsaPublicKey = 
                    (java.security.interfaces.RSAPublicKey) kf.generatePublic(keySpec);

            // 1. Extract the raw BigInteger byte parameters natively for Base64URL transformation
            // This isolates the modulus (n) and public exponent (e) parameters required by RFC 7517
            final String modulus = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rsaPublicKey.getModulus().toByteArray());
            final String exponent = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rsaPublicKey.getPublicExponent().toByteArray());

            // 2. Declaratively assemble the standard RFC 7517 JSON structure 
            // using clean Jakarta JSON builders
            final JsonObject jwkJson = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("alg", "RS256")
                    .add("use", "sig")
                    .add("kid", publicKeyName.get())
                    .add("n", modulus)
                    .add("e", exponent)
                    .build();

            // 3. Wrap inside a standard public "keys" array object
            final JsonObject jwksResponse = Json.createObjectBuilder()
                    .add("keys", Json.createArrayBuilder().add(jwkJson))
                    .build();

            // 4. Return the fully compliant JsonObject structure directly to the network channel
            return jwksResponse ;

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to compile public JWKS key", e);
            throw new IOException("unable to compile public JWKS key");
        }

    }
}
