package online.softmaxx.xapi.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;


@ApplicationPath("/xapi") 
@ApplicationScoped
public class XapiApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        // Explicitly register your classes here. 
        // This stops you from needing to configure 
        // individual classes across XML documents.
        return Set.of(
            DomainExceptionMapper.class,
            JsonProcessingExceptionMapper.class,
            HelloService.class,
            UserService.class
        );
    }

}