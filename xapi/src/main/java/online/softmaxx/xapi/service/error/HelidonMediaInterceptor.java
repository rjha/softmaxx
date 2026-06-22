package online.softmaxx.xapi.service.error;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;


/**
 * 
 * When Helidon detects a malformed JSON payload (such as a missing comma), its 
 * core Níma WebServer routing engine intercepts the broken byte stream at the 
 * socket level. It throws a low-level container exception and automatically generates 
 * its own hardcoded HTTP 400 response string. This happens inside Helidon's fundamental 
 * HTTP server stack before Jersey even boots for the request.
 * 
 * The is a problem because clients sending malformed JSON to our xapi code will 
 * see an error message from the helidon container. That message breaks our guarantee 
 * to return error code and message to the client in case of errors. 
 * 
 * To bypass this container rule and route the raw syntax details to our ExceptionMapper
 * we must inject a standard JAX-RS MessageBodyReader interceptor.
 * This intercepts the raw payload parsing cycle before Helidon media handler blocks 
 * it. We add absolute top-priority (1) intercept status to override helidon default 
 * JSON processor and force Helidon media registry to pass incoming JSON streams 
 * to our code first.
 * 
 */

@Provider
@Priority(1) 
@Consumes(MediaType.APPLICATION_JSON)
public final class HelidonMediaInterceptor implements MessageBodyReader<Object> {

    private static final System.Logger LOGGER = System.getLogger(HelidonMediaInterceptor.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    public static final String JSON_ERROR_TOKEN = "__JSON_ERROR__";

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType);
    }

    @Override
    public Object readFrom(
        final Class<Object> type, 
        final Type genericType, 
        final Annotation[] annotations, 
        final MediaType mediaType, 
        final MultivaluedMap<String, String> httpHeaders, 
        final InputStream entityStream
    ) throws IOException, WebApplicationException {
        
        try {
            // Force Jackson to deserialize the incoming payload stream natively
            return mapper.readValue(entityStream, type);
        } catch (final JsonProcessingException ex) {
            // This block successfully traps JSON parsing errors!
            handleParsingFailure(ex);
            return null;
        }
    }

    
    // Extracts coordinates from the Jackson stream and passes them via a 
    // type-safe token string.
    private static void handleParsingFailure(final JsonProcessingException ex) {
        
        LOGGER.log(System.Logger.Level.WARNING, "helidon media processing error: ", ex);
        final com.fasterxml.jackson.core.JsonLocation location = ex.getLocation();
        
        if (location != null && location.getLineNr() > 0) {
            throw new IllegalArgumentException(String.format("%s:%d:%d", JSON_ERROR_TOKEN, location.getLineNr(), location.getColumnNr()));
        }

        throw new IllegalArgumentException(String.format("%s:1:1", JSON_ERROR_TOKEN));
    }
}
