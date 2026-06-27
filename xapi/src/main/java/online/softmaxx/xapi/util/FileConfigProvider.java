package online.softmaxx.xapi.util;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;


public final class FileConfigProvider implements IConfigProvider {

    private final Properties properties = new Properties();

    public FileConfigProvider(final String filePath) {
        try (InputStream input = new FileInputStream(filePath)) {
            this.properties.load(input);
        } catch (final IOException e) {
            throw new IllegalStateException("❌ Failed to load configuration file: " + filePath, e);
        }
    }

    @Override
    public Optional<String> resolve(final String propertyName) {
        return Optional.ofNullable(this.properties.getProperty(propertyName))
                .map(String::trim)
                .filter(val -> !val.isEmpty());
    }
}
