package online.softmaxx.xapi.util;



import java.util.Optional;

public enum ApplicationConfig {
    
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
    private static final Provider PROVIDER = new Provider();

    ApplicationConfig(final String propertyName) {
        this.propertyName = propertyName;
    }

    public String propertyName() {
        return this.propertyName;
    }

    public Optional<String> get() {
        return PROVIDER.instance.resolve(this.propertyName);
    }

    private static final class Provider {

        private final IConfigProvider instance;

        private Provider() {
            
            final String sysPath = System.getProperty("app.config.path");

            if (sysPath != null && !sysPath.isBlank()) {
                this.instance = new FileConfigProvider(sysPath);
            } else {
                this.instance = new HelidonConfigProvider();
            }

        }

    }

}
