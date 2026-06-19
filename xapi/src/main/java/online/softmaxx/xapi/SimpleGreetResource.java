
package online.softmaxx.xapi;

import io.helidon.webserver.cors.Cors;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.HttpMethod;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;


/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/simple-greet
 *
 * The message is returned as a JSON object.
 */
@Path("/simple-greet")
public class SimpleGreetResource {
    private final String message;

    @Inject
    public SimpleGreetResource(@ConfigProperty(name = "app.greeting") String message) {
        this.message = message;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link Message}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Message getDefaultMessage() {
        String msg = String.format("%s %s!", message, "World");
        Message message = new Message();
        message.setMessage(msg);
        message.setGreeting("earthlings!");
        return message;

    }
    
    @PUT
    public Response getCustomMessage(String greeting) {
        String msg = String.format("%s %s!", greeting, "World");
        return Response.ok(msg).build();
    }

    /**
     * CORS set-up for getCustomMessage.
     */
    @OPTIONS
    @Cors.AllowOrigins({"http://foo.com", "http://there.com"})
    @Cors.AllowMethods({HttpMethod.PUT, HttpMethod.GET})
    public void optionsForGetCustomMessage() {
    }


}
