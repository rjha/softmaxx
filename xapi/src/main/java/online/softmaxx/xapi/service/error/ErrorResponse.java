package online.softmaxx.xapi.service.error;


import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String status,      // Top-level discriminator ("error")
    int statusCode,     // Derived numerical identifier (e.g., 400, 500)
    String message,     // Localized or generic description string
    String timestamp    
) {
    
    public static ErrorResponse of(final Response.Status status, final String message) {
        if (status == null) {
            throw new IllegalArgumentException("http status cannot be null");
        }

        return new ErrorResponse("error", 
            status.getStatusCode(), 
            message, 
            Instant.now().toString());

    }

}


