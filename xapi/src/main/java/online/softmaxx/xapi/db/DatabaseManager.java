package online.softmaxx.xapi.db;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;


public class DatabaseManager {

    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        // Native PostgreSQL driver configurations
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/xapi_db");
        config.setUsername("xapi_user");
        config.setPassword("your_secure_password"); // Replace with xapi_user password
        
        // Optimizations for the connection pool
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(2000);
        
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}

