package online.softmaxx.xapi.service.error;


import java.util.Locale;
import java.util.Map;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.HttpHeaders;


@Provider
public final class XapiExceptionMapper implements ExceptionMapper<Exception> {

    private static final System.Logger LOGGER = System.getLogger(XapiExceptionMapper.class.getName());

    // Exception => http status code mapping 
    private static final Map<Class<? extends Exception>, Response.Status> EXCEPTION_STATUS_MAP = Map.of(
        IllegalArgumentException.class, Response.Status.BAD_REQUEST 
        
    );


    @Context
    private HttpHeaders headers;

    @Override
    public Response toResponse(final Exception exception) {

        LOGGER.log(System.Logger.Level.ERROR, "web application exception mapper invoked...");
        // get locale from from request headers
        final Locale clientLocale = headers.getAcceptableLanguages().stream()
                .findFirst()
                .orElse(Locale.getDefault());

        // get exception => http status code
        final Response.Status httpStatus = EXCEPTION_STATUS_MAP.getOrDefault(
            exception.getClass(), 
            Response.Status.INTERNAL_SERVER_ERROR
        );

        if (httpStatus == Response.Status.INTERNAL_SERVER_ERROR) {
            LOGGER.log(System.Logger.Level.ERROR, "Internal system error: ", exception);
        }

        final String errorMessage = exception.getMessage();
        final String finalMessage;

        // 1. Check if the exception originates from our custom Jackson problem handler
        if (errorMessage != null && errorMessage.startsWith("MALFORMED_JSON_AT:")) {
            return sendJsonErrorResponse(errorMessage);
        }

        if (errorMessage != null && errorMessage.startsWith(AppMessage.TOKEN_PREFIX)) {

            final MessageResolver.MessageDetail details = MessageResolver.resolve(errorMessage, clientLocale);
            // Format: Append the API client error code directly to the localized message bundle text
            finalMessage = String.format("[%s] %s", details.code(), details.message());

        } else {
            // Plain vanilla Exception string pass-through fallback
            finalMessage = (errorMessage != null) ? errorMessage : "No message provided.";
        }

        // 5. Return the standardized ErrorResponse utilizing the mapped HTTP status code
        return Response.status(httpStatus)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(httpStatus, finalMessage))
                .build();
    }


    private Response sendJsonErrorResponse(final String tokenMessage) {

        try {

            final String[] parts = tokenMessage.split(":");
            final int line = Integer.parseInt(parts[1]);
            final int column = Integer.parseInt(parts[2]);
            
            final JsonCoordinate coordinate = new JsonCoordinate(line, column);
            final String specificMsg = String.format("container JSON parsing error at %s", coordinate.toDisplayString());
            
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(Response.Status.BAD_REQUEST, specificMsg))
                    .build();
                    
        } catch (final Exception ex) {

            LOGGER.log(System.Logger.Level.ERROR, "failed to get malformed JSON coordinates from token message: {0}", tokenMessage);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(Response.Status.BAD_REQUEST, "container JSON parsing error"))
                    .build();
        }
    }


}
       

