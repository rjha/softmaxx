package online.softmaxx.xapi.service;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import online.softmaxx.xapi.dao.UserTransaction;
import online.softmaxx.xapi.kafka.KafkaProxy;
import online.softmaxx.xapi.service.model.PhoneRecord;
import online.softmaxx.xapi.service.otp.*;



@Path("/")
@RequestScoped
public class RootService {

    private static final System.Logger LOGGER = System.getLogger(HelloService.class.getName());
    
    
    @GET
    @Path("/.well-known/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwks() {
        try {
            // Return the fully compliant JsonObject structure 
            // directly to the network channel
            return Response.ok(JwtProvider.getPublicKey()).build();

        } catch (final IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage());
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

        final PhoneRecord phoneRecord = PhoneRecord.create(param);
        final String targetE164 = phoneRecord.e164Phone();
        final boolean isRegisteredUser = UserTransaction.phoneExists(targetE164);

        if (isRegisteredUser) {

            final OtpType designatedType = new OtpType.Numeric6Digit(
            "SMS_TEMPLATE_LOGIN_V1", 
            60);
            final String secureToken = OtpTokenFactory.generate(designatedType);
            OtpTransaction.saveOtpToken(phoneRecord, secureToken, designatedType);
            KafkaProxy.sendOtpEvent(phoneRecord, secureToken, designatedType);
            
            
        } else {
            LOGGER.log(System.Logger.Level.INFO, "user is not registered...");
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of(
                        "code", Response.Status.ACCEPTED.getStatusCode(),
                        "status", "accepted",
                        "message", "Please check your registered phone number for OTP"))
                .build();

    }


    @POST
    @Path("/otp/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyOtp(final OtpVerificationParam param) {

        if (param == null) {
            throw new IllegalArgumentException("OTP Verification request parameters cannot be null.");
        }

        final OtpVerificationRequest verifiedRequest = OtpVerificationRequest.create(param);
        final String targetE164 = verifiedRequest.phoneRecord().e164Phone();

        if (!UserTransaction.phoneExists(targetE164)) {
            // @todo add  targetE164 to error message 
            // DEFENSIVE PATTERN: Return an identical error schema 
            // to prevent account harvesting scripts
            return sendOtpErrorResponse();
        }

        final boolean isTokenValid = OtpTransaction.validateToken(verifiedRequest);
        if (!isTokenValid) {
            return sendOtpErrorResponse();
        }

        OtpTransaction.resetToken(targetE164);
        final String associatedUserKey = UserTransaction.getUserKeyByPhone(targetE164);

        // Issue a signed asymmetric JWT access token 
        // via your Provider utility class
        final String webTokenString = JwtProvider.generateToken(associatedUserKey);

        // Return standard authentication completion payload signature contract
        return Response.status(Response.Status.OK)
                .entity(Map.of(
                    "status", "success",
                    "message", "Verification code authorized successfully.",
                    "token", webTokenString,
                    "user_key", associatedUserKey
                )).build();
    }


    private Response sendOtpErrorResponse() {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status", "error",
                    "code", "400",
                    "message", "The verification credentials are invalid or have expired."
                )).build();
    }

}

