package online.softmaxx.xapi.worker;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import online.softmaxx.xapi.db.DatabaseManager;

/**
 * The controller for pgqueue ticker() and maintenance tasks.
 */
public class PgQueueWorker {

    private static final System.Logger LOGGER = System.getLogger(PgQueueWorker.class.getName());

    private static final List<String> MAINTENANCE_TASKS = List.of(
        "SELECT pgque.maint_retry_events();",
        "SELECT pgque.maint(); "
    );

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("pgque-worker-" + thread.hashCode());
        thread.setDaemon(false); // Keeps the JVM process alive under systemd
        return thread;
    });

    public static void main(final String[] args) {
        PgQueueWorker worker = new PgQueueWorker();
        worker.start();
    }

    private void start() {
        // Send ticker() every second
        scheduler.scheduleAtFixedRate(this::executeTickerTask, 0, 1, TimeUnit.SECONDS);
        // Do Maintenance Loop every 30 seconds
        scheduler.scheduleAtFixedRate(this::executeMaintenanceTask, 5, 30, TimeUnit.SECONDS);
        LOGGER.log(System.Logger.Level.INFO, "🚀 starting PgQueueWorker process ...");
    }

    private void executeTickerTask() {
        LOGGER.log(System.Logger.Level.INFO, "executing pgque ticker task...");
        executeTransaction("SELECT pgque.ticker();", "PgQueue:Ticker:Task");
    }

    private void executeMaintenanceTask() {
        LOGGER.log(System.Logger.Level.INFO, "executing pgque maintenance tasks...");
        for (final String sqlCommand : MAINTENANCE_TASKS) {
            executeTransaction(sqlCommand, "PgQueue:Maintenance:Task");
        }
    }

    private void executeTransaction(final String sqlCommand, final String taskName) {
        Connection conn = null;
        
        // FIX: The try block MUST sit at the absolute top of the scope wrapper to catch connection initialization errors.
        try {
            // If the pool is uninitialized or fails to connect, this error is now safely caught and won't kill the thread.
            conn = DatabaseManager.getConnection();
            
            // Tx: start 
            conn.setAutoCommit(false);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET search_path TO pgque, public;");
                stmt.execute(sqlCommand);
            }
            
            // Tx: end 
            conn.commit();
            LOGGER.log(System.Logger.Level.DEBUG, "✅ executed task: " + taskName);

        } catch (final SQLException e) {
            // Safely rollback if connection was established but SQL statements crashed
            if (conn != null) {
                DatabaseManager.rollback(conn);
            }
            LOGGER.log(System.Logger.Level.ERROR, "❌ SQL Failure during task [" + taskName + "]", e);
            
        } catch (final Throwable ex) {
            // CRITICAL DEFENCE: Catching Throwable covers NullPointerExceptions and any severe Runtime Errors
            if (conn != null) {
                DatabaseManager.rollback(conn);
            }
            LOGGER.log(System.Logger.Level.ERROR, "❌ Unexpected execution error during task [" + taskName + "]", ex);
            
        } finally {
            // Always cleanly return resources to HikariCP safely
            if (conn != null) {
                DatabaseManager.release(conn);
            }
        }
    }
}
