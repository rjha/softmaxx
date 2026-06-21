package online.softmaxx.xapi.db;

public final class DuplicateKeyException extends RuntimeException {

    public DuplicateKeyException(final String message) {
        super(message);
    }
    
}