
package online.softmaxx.xapi;


import io.helidon.microprofile.server.Server;
import online.softmaxx.xapi.db.DatabaseManager; 
import java.time.Instant;

public class Main {

    public static void main(String[] args) {
        
        System.out.println("🚀🚀🚀 [" + Instant.now() + "] booting xapi services...");
        
        // 1. Hook an explicit JVM Shutdown event thread to 
        // close the database pool cleanly

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            System.out.println("Stopping Database Connection Pool...");
            DatabaseManager.shutdown();
            System.out.println("shut down completed cleanly");

        }));

        try {

            System.out.println(" Starting Helidon 4.4 MicroProfile container...");

            // 2. Configure and build the Helidon application 
            // runtime environment programmatically
            // Helidon will still discover microprofile-config.properties automatically
            Server server = Server.builder().build();
            server.start();
            String tmpl = "👍👍👍[%s] helidon server started on: http://%s:%s";
            System.out.println(String.format(tmpl, Instant.now(), server.host(), server.port()));
            

        } catch (Exception e) {
            System.err.println("❌❌❌ Critical failure during application bootstrap: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
