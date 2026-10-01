package guru.junaid.azadi.web;

import guru.junaid.azadi.help.HelpController;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@RoutingTest
class HelpControllerTest {

    private final DirectClient client;

    HelpControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new HelpController(ControllerTests.WEB).routing(rules);
    }

    @ParameterizedTest
    @CsvSource({
        "/help/faqs, FAQs",
        "/help/ways-to-pay, pay",
        "/help/contact-us, Contact",
        "/cookies, Cookie",
        "/privacy, Privacy",
        "/terms, Terms",
    })
    @DisplayName("Each help and legal page renders its template")
    void rendersStaticPages(String path, String expected) {
        try (var response = client.get(path).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).containsIgnoringCase(expected);
        }
    }
}
