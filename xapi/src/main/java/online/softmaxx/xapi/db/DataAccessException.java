package online.softmaxx.xapi.db;

public final class DataAccessException extends RuntimeException {

    public DataAccessException(final String message, final Throwable cause) {
        super(message, cause);
    }
    
}
