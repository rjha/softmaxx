package online.softmaxx.xapi.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A reusable, thread-safe utility to track incoming exception structures 
 * and detect duplicate occurrences by evaluating message string hashes.
 */
public final class LogTracker {

    private final System.Logger delegateLogger;
    private final AtomicInteger lastMessageHash = new AtomicInteger(0);

    /** 
     * Constructor accepts the parent class's logger to 
     * preserve accurate log origin tracking.
     */

    public LogTracker(final System.Logger delegateLogger) {
        this.delegateLogger = Objects.requireNonNull(delegateLogger, "Logger cannot be null");
    }

    /** 
     * Internal thread-safe helper to evaluate and store 
     * the hash of current exception message.
     */

    private boolean evaluate(final Throwable exception) {
        
        if (exception == null) {
            this.lastMessageHash.set(0);
            return false;
        }

        final String errorMsg = exception.getMessage();
        final int currentHash = (errorMsg != null) ? errorMsg.hashCode() : "UnknownError".hashCode();

        // Atomically fetch the last message hash record and 
        // overwrite it with the current message hash
        final int previousHash = this.lastMessageHash.getAndSet(currentHash);
        return currentHash == previousHash;

    }

    /**
     * Explicitly clears the internal error tracking metrics state.
     * Call this when an operation succeeds to reset the tracking window for future outages.
     */
    public void reset() {
        this.lastMessageHash.set(0);
    }

    /**
     * Process and logs the exception, automatically suppressing duplicate stack traces.
     */
    public void error(final String errorMessage, final Exception ex) {
        if (this.evaluate(ex)) {
            // Duplicate failure scenario -> Log a warning string without full stack frame dumps
            this.delegateLogger.log(System.Logger.Level.WARNING, "{0} - Reason: {1} (Stack trace suppressed)", 
                    errorMessage, ex.getMessage());
        } else {
            // Brand new failure pattern -> Include the full error stack trace details for direct debugging
            this.delegateLogger.log(System.Logger.Level.ERROR, errorMessage, ex);
        }
    }
}
