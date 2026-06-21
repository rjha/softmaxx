package online.softmaxx.xapi.db;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {

    private static final System.Logger LOGGER = System.getLogger(DatabaseManager.class.getName());
    private static final HikariDataSource DATA_SOURCE;

    static {

        LOGGER.log(System.Logger.Level.INFO, "🚀 initializing HikariCP connection pool ...");
        
        try {
            // 1. Fetch the global MicroProfile configuration instance provider
            final Config config = ConfigProvider.getConfig();

            // 2. Read explicit connection targets with default fallbacks
            final String host = config.getOptionalValue("db.host", String.class).orElse("localhost");
            final int port = config.getOptionalValue("db.port", Integer.class).orElse(5432);
            final String dbName = config.getOptionalValue("db.name", String.class).orElse("xapi_db");
            final String username = config.getValue("db.username", String.class);
            final String password = config.getValue("db.password", String.class);

            // Construct standard PostgreSQL native JDBC target routing string
            final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);

            // 3. Bind properties to the Hikari operational setup layer
            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            // 4. Load optimized connection pool tuning metrics
            hikariConfig.setMaximumPoolSize(config.getOptionalValue("db.pool.max-size", Integer.class).orElse(10));
            hikariConfig.setMinimumIdle(config.getOptionalValue("db.pool.min-idle", Integer.class).orElse(2));
            hikariConfig.setIdleTimeout(config.getOptionalValue("db.pool.idle-timeout-ms", Long.class).orElse(30000L));
            hikariConfig.setConnectionTimeout(config.getOptionalValue("db.pool.connection-timeout-ms", Long.class).orElse(2000L));

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

    /**
     * Obtains a thread-safe pooled connection from the active Hikari cluster.
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * Gracefully shuts down the connection pool cluster during app termination.
     */
    public static void shutdown() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            LOGGER.log(System.Logger.Level.INFO, "shutting down HikariCP database connectivity resources...");
            DATA_SOURCE.close();
        }
    }
}
