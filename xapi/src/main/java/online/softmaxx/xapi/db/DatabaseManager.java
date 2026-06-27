package online.softmaxx.xapi.db;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import online.softmaxx.xapi.util.ApplicationConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;


public final class DatabaseManager {

    private static final System.Logger LOGGER = System.getLogger(DatabaseManager.class.getName());
    private static final HikariDataSource DATA_SOURCE;

    static {

        LOGGER.log(System.Logger.Level.INFO, "🚀 initializing HikariCP connection pool ...");
        
        try {

            final Optional<String> dbHostOpt = ApplicationConfig.DB_HOST.get();
            final Optional<String> dbPortOpt = ApplicationConfig.DB_PORT.get();
            final Optional<String> dbNameOpt = ApplicationConfig.DB_NAME.get();
            final Optional<String> dbUsernameOpt = ApplicationConfig.DB_USERNAME.get();
            final Optional<String> dbPasswordOpt = ApplicationConfig.DB_PASSWORD.get();

            // mandatory parameters
            if (dbUsernameOpt.isEmpty()) {
                throw new IllegalStateException("helidon config db.username is missing.");
            }

            if (dbPasswordOpt.isEmpty()) {
                throw new IllegalStateException("helidon config db.password is missing.");
            }

            final String host = dbHostOpt.orElse("localhost");
            final int port = dbPortOpt.map(Integer::parseInt).orElse(5432);
            final String dbName = dbNameOpt.orElse("xapi_db");
            final String username = dbUsernameOpt.get(); 
            final String password = dbPasswordOpt.get();  

            // Construct standard PostgreSQL native JDBC target routing string
            final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);

            // Bind properties to the Hikari operational setup layer
            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            // Extract and Map Optional Connection Pool Tuning Parameters
            final Optional<String> poolMaxOpt = ApplicationConfig.DB_POOL_MAX_SIZE.get();
            final Optional<String> poolMinIdleOpt = ApplicationConfig.DB_POOL_MIN_IDLE.get();
            final Optional<String> poolIdleTimeoutOpt = ApplicationConfig.DB_POOL_IDLE_TIMEOUT_MS.get();
            final Optional<String> poolConnTimeoutOpt = ApplicationConfig.DB_POOL_CONNECTION_TIMEOUT_MS.get();

            hikariConfig.setMaximumPoolSize(poolMaxOpt.map(Integer::parseInt).orElse(10));
            hikariConfig.setMinimumIdle(poolMinIdleOpt.map(Integer::parseInt).orElse(2));
            hikariConfig.setIdleTimeout(poolIdleTimeoutOpt.map(Long::parseLong).orElse(30000L));
            hikariConfig.setConnectionTimeout(poolConnTimeoutOpt.map(Long::parseLong).orElse(2000L));
            
            // Recommended performance optimizations for PostgreSQL drivers
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            DATA_SOURCE = new HikariDataSource(hikariConfig);
            LOGGER.log(System.Logger.Level.INFO, "👍 HikariCP connection pool initialized successfully for URL: {0}", jdbcUrl);

        } catch (final Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "❌ critical failure initializing Database Connection Pool", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    
    // Obtains a thread-safe pooled connection 
    // from the active Hikari cluster.
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void rollback(final Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (final SQLException ex) {
                LOGGER.log(System.Logger.Level.ERROR, "database transaction rollback failed", ex);
            }
        }
    }

    public static void release(final Connection connx) {

        if (connx != null) {
            try {
                connx.close();
            } catch (final SQLException ex) {
                LOGGER.log(System.Logger.Level.ERROR, "error releasing Hikari connection");
            }
        }

    }

   
    // Gracefully shuts down the connection pool 
    // cluster during app termination.
    public static void shutdown() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            LOGGER.log(System.Logger.Level.INFO, "shutting down HikariCP database connectivity resources...");
            DATA_SOURCE.close();
        }
    }
}
