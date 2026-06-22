
package online.softmaxx.xapi;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@HelidonTest
class XapiServiceTest {

    @Inject
    private WebTarget target;


    @Test
    void testGreet() {
        String greeting = target
                .path("/xapi/greeting")
                .request()
                .get(String.class);
        assertThat(greeting, is("Hello"));

    }

    
}
