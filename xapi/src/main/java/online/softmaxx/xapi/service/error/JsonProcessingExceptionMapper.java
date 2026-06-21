package online.softmaxx.xapi.service.error;


import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public final class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    private static final System.Logger LOGGER = System.getLogger(JsonProcessingExceptionMapper.class.getName());

    @Override
    public Response toResponse(final JsonProcessingException exception) {

        final String errorMessage =  exception.getOriginalMessage();
        final JsonLocation location = exception.getLocation();
        LOGGER.log(System.Logger.Level.WARNING, "malformed JSON in request: {0}", errorMessage);
        
        // Check if Jackson was able to isolate the structural failure coordinates
        if (location != null && location.getLineNr() > 0) {

            final JsonCoordinate coordinate = new JsonCoordinate(location.getLineNr(), location.getColumnNr());
            final String errorMessageWithLocation = errorMessage + coordinate.toDisplayString();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(Response.Status.BAD_REQUEST, errorMessageWithLocation))
                    .build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(Response.Status.BAD_REQUEST, errorMessage))
                .build();
    }
}

