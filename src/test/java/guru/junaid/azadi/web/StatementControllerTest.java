package guru.junaid.azadi.web;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.contact.ContactService;
import guru.junaid.azadi.statement.StatementController;
import guru.junaid.azadi.statement.StatementService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RoutingTest
class StatementControllerTest {

    private static final StatementService STATEMENTS = mock(StatementService.class);
    private static final AgreementService AGREEMENTS = mock(AgreementService.class);
    private static final ContactService CONTACT = mock(ContactService.class);
    private static final String PATH = "/finance/request-a-statement";

    private final DirectClient client;

    StatementControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new StatementController(ControllerTests.WEB, STATEMENTS, AGREEMENTS, CONTACT).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(STATEMENTS, AGREEMENTS, CONTACT);
    }

    @Test
    @DisplayName("GET shows where the statement will be sent")
    void showsTheDestination() {
        when(AGREEMENTS.agreementsFor(ControllerTests.CUSTOMER_ID)).thenReturn(List.of(Fixtures.agreement(1L, "AGR-001", ControllerTests.CUSTOMER_ID)));
        when(CONTACT.customer(ControllerTests.CUSTOMER_ID)).thenReturn(Fixtures.customer(ControllerTests.CUSTOMER_ID));

        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("test@example.com").contains("1 Test Street");
        }
    }

    @Test
    @DisplayName("POST passes the chosen agreement on and redirects; an empty choice passes nothing")
    void requestsAStatement() {
        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=3")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }
        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }

        verify(STATEMENTS).request(ControllerTests.CUSTOMER_ID, Optional.of(3L), ControllerTests.IP, ControllerTests.SESSION_ID);
        verify(STATEMENTS).request(ControllerTests.CUSTOMER_ID, Optional.empty(), ControllerTests.IP, ControllerTests.SESSION_ID);
    }
}
