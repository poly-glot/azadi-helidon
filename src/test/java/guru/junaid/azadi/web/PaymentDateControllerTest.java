package guru.junaid.azadi.web;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.payment.PaymentDateController;
import guru.junaid.azadi.payment.PaymentDateService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RoutingTest
class PaymentDateControllerTest {

    private static final PaymentDateService PAYMENT_DATES = mock(PaymentDateService.class);
    private static final AgreementService AGREEMENTS = mock(AgreementService.class);
    private static final String PATH = "/finance/change-payment-date";

    private final DirectClient client;

    PaymentDateControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new PaymentDateController(ControllerTests.WEB, PAYMENT_DATES, AGREEMENTS).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(PAYMENT_DATES, AGREEMENTS);
        when(AGREEMENTS.agreementsFor(ControllerTests.CUSTOMER_ID)).thenReturn(List.of(Fixtures.agreement(1L, "AGR-001", ControllerTests.CUSTOMER_ID)));
    }

    @Test
    @DisplayName("GET shows the current payment day with its ordinal")
    void showsTheCurrentDay() {
        when(PAYMENT_DATES.currentPaymentDay(ControllerTests.CUSTOMER_ID, 1L)).thenReturn(21);

        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("21st");
        }
    }

    @Test
    @DisplayName("POST changes the day through the service and redirects")
    void changesTheDay() {
        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=1&newPaymentDate=21")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }

        verify(PAYMENT_DATES).change(ControllerTests.CUSTOMER_ID, 1L, 21, ControllerTests.IP, ControllerTests.SESSION_ID);
    }

    @Test
    @DisplayName("A refused second change still redirects, carrying the reason")
    void reportsARefusedChange() {
        doThrow(new IllegalStateException("Payment date has already been changed for this agreement."))
            .when(PAYMENT_DATES).change(ControllerTests.CUSTOMER_ID, 1L, 7, ControllerTests.IP, ControllerTests.SESSION_ID);

        try (var response = client.post(PATH).followRedirects(false).submit("agreementId=1&newPaymentDate=7")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }
    }
}
