package online.softmaxx.xapi.worker.test;


import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import online.softmaxx.xapi.db.DatabaseManager;

/**
 * 
 * CLI utility command to send a ticker() to pgque. 
 * Run this directly from your terminal console.
 * 
 * $mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
 * $java -cp "target/xapi.jar:target/dependency/*" online.softmaxx.xapi.worker.test.PgQueueTicker
 * 
 */
public class PgQueueTicker {

    private static final System.Logger LOGGER = System.getLogger(PgQueueTicker.class.getName());

    public static void main(final String[] args) {

        LOGGER.log(System.Logger.Level.INFO, "🚀 Starting manual CLI PgQue ticker invocation...");
        Connection conn = null;

        try {
            // 1. Establish database connection slice
            conn = DatabaseManager.getConnection();
            
            // 2. Open explicit transaction boundary matching the OtpTransaction specification pattern
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // 3. Align namespace resolution pathways
                stmt.execute("SET search_path TO pgque, public;");
                
                // 4. Force immediate queue status tracking updates
                stmt.execute("SELECT pgque.ticker();");
            }

            // 5. Commit transaction and persist changes cleanly to disk
            conn.commit();
            LOGGER.log(System.Logger.Level.INFO, "✅ Manual PgQue ticker statement successfully processed and committed!");

        } catch (final SQLException e) {
            // 6. Rollback explicit transaction bounds immediately on engine faults
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "❌ Database SQL crash running manual ticker command", e);
            System.exit(1);

        } catch (final Exception ex) {
            // 7. Safety rollback for unhandled runtime layer failure modes
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "❌ Unexpected runtime exception inside CLI ticker execution context", ex);
            System.exit(1);

        } finally {
            // 8. Deterministically release connection resource links back to pool layers
            DatabaseManager.release(conn);
            LOGGER.log(System.Logger.Level.INFO, "🏁 Connection cleanup complete. Exiting terminal process.");
        }
    }
}
