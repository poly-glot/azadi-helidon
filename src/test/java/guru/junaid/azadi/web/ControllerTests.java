package guru.junaid.azadi.web;

import guru.junaid.azadi.auth.Session;
import guru.junaid.azadi.common.Views;
import guru.junaid.azadi.common.Web;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;

import static org.assertj.core.api.Assertions.assertThat;

final class ControllerTests {

    static final String CUSTOMER_ID = "CUST-1";
    static final String SESSION_ID = "session-1";
    static final String IP = "10.0.0.1";
    static final Web WEB = new Web(new Views(true));
    static final Session SESSION = new Session(SESSION_ID, CUSTOMER_ID, "Test Customer");

    private ControllerTests() {
    }

    static void signedIn(HttpRouting.Builder rules) {
        rules.addFilter((chain, req, res) -> {
            req.context().register(SESSION);
            requestState(req);
            chain.proceed();
        });
    }

    static void anonymous(HttpRouting.Builder rules) {
        rules.addFilter((chain, req, res) -> {
            requestState(req);
            chain.proceed();
        });
    }

    private static void requestState(RoutingRequest req) {
        req.context().register("ip", IP);
        if (req.content().hasEntity()) {
            req.context().register("body", req.content().as(String.class));
        }
    }

    static String page(Http1ClientResponse response) {
        assertThat(response.status()).isEqualTo(Status.OK_200);
        return response.as(String.class);
    }

    static String redirect(Http1ClientResponse response) {
        assertThat(response.status()).isEqualTo(Status.FOUND_302);
        return response.headers().first(HeaderNames.LOCATION).orElseThrow();
    }
}
