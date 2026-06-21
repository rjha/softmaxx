package online.softmaxx.xapi.service;


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
        
        LOGGER.log(System.Logger.Level.WARNING, "Malformed JSON syntax intercepted: {0}", exception.getOriginalMessage());
        
        final JsonLocation location = exception.getLocation();
        
        // Check if Jackson was able to isolate the structural failure coordinates
        if (location != null && location.getLineNr() > 0) {
            final int line = location.getLineNr();
            final int column = location.getColumnNr();
            
            final String specificMsg = String.format("Malformed JSON payload syntax at line %d, column %d.", line, column);
            
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.withLocation(specificMsg, line, column))
                    .build();
        }

        // Fallback for general deserialization data type mismatches
        final String fallbackMsg = "Invalid JSON payload structure. Please verify syntax properties and data field types.";
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(fallbackMsg))
                .build();
    }
}

