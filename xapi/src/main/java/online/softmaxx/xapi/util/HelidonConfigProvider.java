package online.softmaxx.xapi.util;


import org.eclipse.microprofile.config.ConfigProvider;
import java.util.Optional;

// ConfigProvider is recommended for MP applications
// since we are using CDI, we should pick micro profile 
// config file  to store config rather than application.properties 

public final class HelidonConfigProvider implements IConfigProvider {

    // Resolves the property value from the Helidon configuration 
    // context as an Optional.
    // Returns Optional.empty() if the value is missing, empty, or blank.
    @Override
    public Optional<String> resolve(final String propertyName) {
        return ConfigProvider.getConfig()
                .getOptionalValue(propertyName, String.class)
                .map(String::trim)
                .filter(val -> !val.isEmpty());
    }
}
