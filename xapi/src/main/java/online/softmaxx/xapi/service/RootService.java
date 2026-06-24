package online.softmaxx.xapi.service;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Map;
import online.softmaxx.xapi.dao.UserDao;
import online.softmaxx.xapi.service.model.PhoneRecord;
import online.softmaxx.xapi.service.otp.OtpType;
import online.softmaxx.xapi.service.otp.OtpDao;
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
        final boolean isRegisteredUser = UserDao.phoneExists(targetE164);

        if (isRegisteredUser) {

            final OtpType designatedType = new OtpType.Numeric6Digit(
            "SMS_TEMPLATE_LOGIN_V1", 
            60);
            final String secureToken = OtpTokenFactory.generate(designatedType);
            OtpDao.saveOtpToken(phoneRecord, secureToken, designatedType);

        } else {
            LOGGER.log(System.Logger.Level.INFO, "user is not registered...");
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of(
                        "status", "success",
                        "message", "Please check your registered phone number for OTP"))
                .build();

    }

}
