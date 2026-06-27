package online.softmaxx.xapi.util;


import java.util.Optional;

@FunctionalInterface
public interface IConfigProvider {
    Optional<String> resolve(String propertyName);
}
