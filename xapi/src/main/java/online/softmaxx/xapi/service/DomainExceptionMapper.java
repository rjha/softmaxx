package online.softmaxx.xapi.service;


import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import online.softmaxx.xapi.db.DataAccessException;
import online.softmaxx.xapi.db.DuplicateKeyException;


@Provider
public final class DomainExceptionMapper implements ExceptionMapper<Exception> {

    private static final System.Logger LOGGER = System.getLogger(DomainExceptionMapper.class.getName());

    @Override
    public Response toResponse(final Exception exception) {
        // 1. Handle Domain Logic Validations
        if (exception instanceof IllegalArgumentException) {
            LOGGER.log(System.Logger.Level.WARNING, "Domain validation failed: {0}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(exception.getMessage()))
                    .build();
        }

        // 2. Handle Unique Constraint Conflicts
        if (exception instanceof DuplicateKeyException) {
            LOGGER.log(System.Logger.Level.WARNING, "Data conflict detected: {0}", exception.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(exception.getMessage()))
                    .build();
        }

        // 3. Handle Encapsulated Database Errors
        if (exception instanceof DataAccessException) {
            LOGGER.log(System.Logger.Level.ERROR, "Data access operational failure intercepted", exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of("Internal data service layer error occurred."))
                    .build();
        }

        // 4. Default Fallback for all other checked/unchecked exceptions
        LOGGER.log(System.Logger.Level.ERROR, "Unhandled exception intercepted at boundary layer", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("An unexpected server error occurred."))
                .build();
    }
}
