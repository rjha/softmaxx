
package online.softmaxx.xapi.service;


import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;



@Path("/test")
public class HelloService {

   
    private final String greeting;
    
    @Inject
    public HelloService(@ConfigProperty(name = "app.greeting") String greeting) {
        this.greeting = greeting;
    }

    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    public String getHellotMessage() {
        return "Hello World!";
    }
    
     @GET
    @Path("/greeting")
    @Produces(MediaType.TEXT_PLAIN)
    public String getConfigGreeting() {
        return this.greeting; 
    }


}
