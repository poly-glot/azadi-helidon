package guru.junaid.azadi.web;

import com.stripe.exception.StripeException;
import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.payment.PaymentController;
import guru.junaid.azadi.payment.PaymentService;
import guru.junaid.azadi.payment.PaymentWebhookHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
class PaymentControllerTest {

    private static final PaymentService PAYMENTS = mock(PaymentService.class);
    private static final PaymentWebhookHandler WEBHOOKS = mock(PaymentWebhookHandler.class);
    private static final AgreementService AGREEMENTS = mock(AgreementService.class);
    private static final String PATH = "/finance/make-a-payment";

    private final DirectClient client;

    PaymentControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new PaymentController(ControllerTests.WEB, PAYMENTS, WEBHOOKS, AGREEMENTS, "pk_test_dummy").routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(PAYMENTS, WEBHOOKS, AGREEMENTS);
    }

    @Test
    @DisplayName("GET offers the agreements and the publishable key")
    void formOffersAgreements() {
        when(AGREEMENTS.agreementsFor(ControllerTests.CUSTOMER_ID)).thenReturn(List.of(Fixtures.agreement(1L, "AGR-001", ControllerTests.CUSTOMER_ID)));

        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("AGR-001").contains("pk_test_dummy");
        }
    }

    @Test
    @DisplayName("A valid JSON payment answers with the client secret")
    void paymentReturnsTheClientSecret() throws StripeException {
        when(PAYMENTS.initiate(ControllerTests.CUSTOMER_ID, ControllerTests.SESSION_ID, 1L, 45_000L, ControllerTests.IP)).thenReturn("pi_secret");

        try (var response = client.post(PATH).followRedirects(false).submit("{\"agreementId\":1,\"amountPence\":45000}")) {
            assertThat(response.status()).isEqualTo(Status.OK_200);
            assertThat(response.headers().first(HeaderNames.CONTENT_TYPE)).contains("application/json");
            assertThat(response.as(String.class)).isEqualTo("{\"clientSecret\":\"pi_secret\"}");
        }
    }

    @Test
    @DisplayName("An amount under a pound is refused before the service is called")
    void refusesAnAmountUnderOnePound() throws StripeException {
        try (var response = client.post(PATH).followRedirects(false).submit("{\"agreementId\":1,\"amountPence\":99}")) {
            assertThat(response.status()).isEqualTo(Status.BAD_REQUEST_400);
            assertThat(response.as(String.class)).contains("minimum £1.00");
        }

        verify(PAYMENTS, never()).initiate(anyString(), anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("The webhook passes the raw body and signature on and maps the verdict to a status")
    void webhookMapsTheVerdict() {
        when(WEBHOOKS.handle("{}", "sig", ControllerTests.IP)).thenReturn(true, false);

        try (var response = client.post("/api/stripe/webhook").header(HeaderNames.create("Stripe-Signature"), "sig").followRedirects(false).submit("{}")) {
            assertThat(response.status()).isEqualTo(Status.OK_200);
            assertThat(response.as(String.class)).isEqualTo("ok");
        }
        try (var response = client.post("/api/stripe/webhook").header(HeaderNames.create("Stripe-Signature"), "sig").followRedirects(false).submit("{}")) {
            assertThat(response.status()).isEqualTo(Status.BAD_REQUEST_400);
            assertThat(response.as(String.class)).isEqualTo("Invalid signature");
        }
    }
}
