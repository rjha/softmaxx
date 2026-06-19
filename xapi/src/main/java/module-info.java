
module online.softmaxx.xapi {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires jakarta.xml.bind;
    requires io.helidon.microprofile.cors;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires io.helidon;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;

    exports online.softmaxx.xapi;
    opens online.softmaxx.xapi;
    
}