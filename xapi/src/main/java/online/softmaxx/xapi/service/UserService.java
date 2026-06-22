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


import online.softmaxx.xapi.service.param.*;
import online.softmaxx.xapi.service.model.*;
import online.softmaxx.xapi.dao.UserDao;
import java.security.KeyFactory;
import java.util.Base64;


@Path("/user")
@RequestScoped
public class UserService {

    private static final System.Logger LOGGER = System.getLogger(UserService.class.getName());


    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(final NewUserRequestParam param) {

        if (param == null) {
            throw new IllegalArgumentException("Request body cannot be null or empty.");
        }

        final NewUserRequest userModel = NewUserRequest.create(param);
        final String systemUserKey = UserDao.registerNewUser(userModel);
        
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                    "status", "success",
                    "message", "User created successfully.",
                    "user_key", systemUserKey
                )).build();
    }


    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(final UserLoginParam param) {
        
        if (param == null) {
            throw new IllegalArgumentException("login request param cannot be null or empty.");
        }

        final UserLoginRequest loginRequest = UserLoginRequest.create(param);
        final String verifiedUserKey = UserDao.authenticateUser(loginRequest);

        // Issue cryptographically signed token string
        final String webTokenString = JwtProvider.generateToken(verifiedUserKey);

        // Return explicit success payload contract 
        // along with bearer token details
        return Response.status(Response.Status.OK)
                .entity(Map.of(
                    "status", "success",
                    "message", "Authentication verified successfully.",
                    "token", webTokenString,
                    "user_key", verifiedUserKey
                )).build();

    }

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


}
