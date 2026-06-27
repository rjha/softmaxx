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
        thread.setName("PGQUE-WORKER-" + thread.hashCode());
        thread.setDaemon(false); // Keeps the JVM process alive under systemd
        return thread;
    });

    public static void main(final String[] args) {
        PgQueueWorker worker = new PgQueueWorker();
        worker.start();
    }

    private void start() {
        // Send ticker() every second
        scheduler.scheduleWithFixedDelay(this::executeTickerTask, 10, 1, TimeUnit.SECONDS);
        // Do Maintenance Loop every 30 seconds
        scheduler.scheduleWithFixedDelay(this::executeMaintenanceTask, 30, 30, TimeUnit.SECONDS);
        LOGGER.log(System.Logger.Level.INFO, "🚀 starting PgQueueWorker process ...");
    }

    private void executeTickerTask() {
        executeTransaction("SELECT pgque.ticker();", "PGQUE:TICKER:TASK");
    }

    private void executeMaintenanceTask() {
        for (final String sqlCommand : MAINTENANCE_TASKS) {
            executeTransaction(sqlCommand, "PGQUE:MAINTENANCE:TASK");
        }
    }

    private void executeTransaction(final String sqlCommand, final String taskName) {

        Connection conn = null;
        
        // The try block MUST sit at the absolute top of the scope 
        // The errors should be caught so they do not kill the thread
        try {

            conn = DatabaseManager.getConnection();
            // Tx: start 
            conn.setAutoCommit(false);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET search_path TO pgque, public;");
                // stmt.execute("SELECT pg_sleep(random() * 0.05);");
                stmt.execute(sqlCommand);
            }
            
            // Tx: end 
            conn.commit();
            LOGGER.log(System.Logger.Level.DEBUG, "✅ executed task: " + taskName);

        } catch (final SQLException e) {
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "❌ SQL Failure during task [" + taskName + "]", e);
            
        } catch (final Throwable ex) {
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "❌ Unexpected execution error during task [" + taskName + "]", ex);
            
        } finally {
            DatabaseManager.release(conn);
        }
    }
}
