package guru.junaid.azadi.web;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementController;
import guru.junaid.azadi.agreement.AgreementService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@RoutingTest
class AgreementControllerTest {

    private static final AgreementService AGREEMENTS = mock(AgreementService.class);

    private final DirectClient client;

    AgreementControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new AgreementController(ControllerTests.WEB, AGREEMENTS).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(AGREEMENTS);
    }

    @Test
    @DisplayName("GET /my-account renders every agreement with its formatted balance")
    void myAccountListsAgreements() {
        when(AGREEMENTS.agreementsFor(ControllerTests.CUSTOMER_ID)).thenReturn(List.of(
            Fixtures.agreement(1L, "AGR-001", ControllerTests.CUSTOMER_ID), Fixtures.agreement(2L, "AGR-002", ControllerTests.CUSTOMER_ID)));

        try (var response = client.get("/my-account").followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("AGR-001").contains("AGR-002").contains("£12,000.00").contains("Test Customer");
        }
    }

    @Test
    @DisplayName("GET /agreements/{id} renders the owned agreement in full")
    void agreementDetailRendersTheAgreement() {
        when(AGREEMENTS.agreement(ControllerTests.CUSTOMER_ID, 5L)).thenReturn(Fixtures.agreement(5L, "AGR-005", ControllerTests.CUSTOMER_ID));

        try (var response = client.get("/agreements/5").followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("AGR-005").contains("6.9%").contains("AB24 TST");
        }
    }
}
