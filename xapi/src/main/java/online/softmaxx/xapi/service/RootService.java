package online.softmaxx.xapi.service;

import jakarta.enterprise.context.RequestScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

import java.security.KeyFactory;
import java.util.Base64;

import online.softmaxx.xapi.service.model.PhoneRecord;
import online.softmaxx.xapi.service.otp.OtpType;
import online.softmaxx.xapi.service.otp.OtpRequestParam;
import online.softmaxx.xapi.service.otp.OtpTokenFactory;



@Path("/")
@RequestScoped
public class RootService {

    private static final System.Logger LOGGER = System.getLogger(HelloService.class.getName());
    
    
    @GET
    @Path("/.well-known/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwks() {

        LOGGER.log(System.Logger.Level.INFO, "JWKS public verification matrix request received.");

        try (final InputStream is = UserService.class.getResourceAsStream("/helidon4_pub.pem")) {
            if (is == null) {
                throw new IllegalStateException("Public key asset missing from classpath: /publicKey.pem");
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

            // 2. Declaratively assemble the standard RFC 7517 JSON structure using clean Jakarta JSON builders
            final JsonObject jwkJson = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("alg", "RS256")
                    .add("use", "sig")
                    .add("kid", "helidon4.pem")
                    .add("n", modulus)
                    .add("e", exponent)
                    .build();

            // 3. Wrap inside a standard public "keys" array object
            final JsonObject jwksResponse = Json.createObjectBuilder()
                    .add("keys", Json.createArrayBuilder().add(jwkJson))
                    .build();

            // 4. Return the fully compliant JsonObject structure directly to the network channel
            return Response.ok(jwksResponse).build();

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to compile public JWKS metadata payload structure", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    @POST
    @Path("/otp/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateOtp(final OtpRequestParam param) {

        if (param == null) {
            throw new IllegalArgumentException("Request body cannot be null or empty.");
        }

        // 1. Initialise validated PhoneRecord domain model
        final PhoneRecord phoneRecord = PhoneRecord.create(param);

        // 2. Determine target configuration (Example: 60 seconds expiration for Login)
        final OtpType designatedType = new OtpType.Numeric6Digit("SMS_TEMPLATE_LOGIN_V1", 60);

        // 3. Request a token from the Factory based on the chosen type
        final String secureToken = OtpTokenFactory.generate(designatedType);
        
        // 4. Calculate database expiry timestamp dynamically using the OtpType TTL
        // final long expireOnEpochSeconds = (System.currentTimeMillis() / 1000) + designatedType.timeToLiveSeconds();

        // Ready to pass to DAO layer in the next step
        System.out.printf("DAO Data Check -> Country: %s, Phone: %s, Token: %s, template:%s, TTL: %d%n",
                phoneRecord.countryCode(), 
                phoneRecord.phoneNumber(), 
                secureToken, 
                designatedType.templateId(),
                designatedType.timeToLiveSeconds());
        
        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of(
                    "status", "success",
                    "message", "OTP generated successfully."
                )).build();
                
    }

}
