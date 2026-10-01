package guru.junaid.azadi.web;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.settlement.SettlementController;
import guru.junaid.azadi.settlement.SettlementFigure;
import guru.junaid.azadi.settlement.SettlementService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RoutingTest
class SettlementControllerTest {

    private static final SettlementService SETTLEMENTS = mock(SettlementService.class);
    private static final AgreementService AGREEMENTS = mock(AgreementService.class);
    private static final String PATH = "/finance/settlement-figure";

    private final DirectClient client;

    SettlementControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new SettlementController(ControllerTests.WEB, SETTLEMENTS, AGREEMENTS).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(SETTLEMENTS, AGREEMENTS);
        when(AGREEMENTS.agreementsFor(ControllerTests.CUSTOMER_ID)).thenReturn(List.of(Fixtures.agreement(1L, "AGR-001", ControllerTests.CUSTOMER_ID)));
    }

    @Test
    @DisplayName("GET offers the agreements to settle")
    void offersAgreements() {
        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("AGR-001").doesNotContain("£12,240.00");
        }
    }

    @Test
    @DisplayName("POST with an agreement calculates and shows the figure")
    void calculatesAFigure() {
        when(SETTLEMENTS.calculate(ControllerTests.CUSTOMER_ID, 1L))
            .thenReturn(new SettlementFigure(6L, 1L, ControllerTests.CUSTOMER_ID, 1_224_000L, Instant.now(), LocalDate.now().plusDays(28)));

        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=1")) {
            assertThat(ControllerTests.page(response)).contains("£12,240.00");
        }
    }

    @Test
    @DisplayName("POST without an agreement just re-renders the page")
    void toleratesAnEmptyChoice() {
        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=")) {
            assertThat(ControllerTests.page(response)).contains("AGR-001");
        }

        verify(SETTLEMENTS, never()).calculate(anyString(), anyLong());
    }
}
