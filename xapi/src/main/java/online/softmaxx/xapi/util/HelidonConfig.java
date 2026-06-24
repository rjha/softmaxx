package online.softmaxx.xapi.util;

// ConfigProvider is recommended for MP applications
// since we are using CDI, we should pick micro profile 
// config file  to store config rather than application.properties 
// 
import org.eclipse.microprofile.config.ConfigProvider;
import java.util.Optional;

public enum HelidonConfig {
    
    // JWT Security Infrastructure
    JWT_PRIVATE_KEY_PATH("app.jwt.private-key-path"),
    JWT_PRIVATE_KEY_NAME("app.jwt.private-key-name"),
    JWT_PUBLIC_KEY_PATH("app.jwt.public-key-path"),
    JWT_PUBLIC_KEY_NAME("app.jwt.public-key-name"),
    // Database 
    DB_HOST("db.host"),
    DB_PORT("db.port"),
    DB_NAME("db.name"),
    DB_USERNAME("db.username"),
    DB_PASSWORD("db.password"),
    // Hikari Connection Pool Tuning
    DB_POOL_MAX_SIZE("db.pool.max-size"),
    DB_POOL_MIN_IDLE("db.pool.min-idle"),
    DB_POOL_IDLE_TIMEOUT_MS("db.pool.idle-timeout-ms"),
    DB_POOL_CONNECTION_TIMEOUT_MS("db.pool.connection-timeout-ms");
    
    private final String propertyName;

    HelidonConfig(final String propertyName) {
        this.propertyName = propertyName;
    }

    public String propertyName() {
        return this.propertyName;
    }

    // Resolves the property value from the Helidon configuration 
    // context as an Optional.
    // Returns Optional.empty() if the value is missing, empty, or blank.
    public Optional<String> get() {
        return ConfigProvider.getConfig()
                .getOptionalValue(this.propertyName, String.class)
                .map(String::trim)
                .filter(val -> !val.isEmpty());
    }

}
