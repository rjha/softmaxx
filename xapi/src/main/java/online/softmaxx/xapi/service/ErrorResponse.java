package online.softmaxx.xapi.service;


import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String status,
    String message,
    String timestamp,
    Integer line,      // Included only for JSON syntax failures
    Integer column     // Included only for JSON syntax failures
) {
    public static ErrorResponse of(final String message) {
        return new ErrorResponse("error", message, Instant.now().toString(), null, null);
    }

    public static ErrorResponse withLocation(final String message, final int line, final int column) {
        return new ErrorResponse("error", message, Instant.now().toString(), line, column);
    }
}

