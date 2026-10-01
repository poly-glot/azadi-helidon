package guru.junaid.azadi;

import guru.junaid.azadi.payment.StripeEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentFlowIntegrationTest extends BaseIntegrationTest {

    private static final String PATH = "/finance/make-a-payment";

    private Account account;
    private Client client;

    @BeforeEach
    void signIn() {
        account = account();
        client = signedIn(account);
    }

    @Test
    @DisplayName("The payment page offers the agreement and the publishable key")
    void showsThePaymentForm() {
        var body = client.get(PATH).body();

        assertThat(body).contains(account.agreementNumber()).contains("pk_test_dummy");
    }

    @Test
    @DisplayName("A payment records the intent, audits it and returns the client secret")
    void startsAPayment() {
        var response = client.postJson(PATH,
            "{\"agreementId\":%d,\"amountPence\":45000}".formatted(account.agreementId()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("clientSecret").contains("_secret_fake");

        var record = entity("PaymentRecord", "customerId", account.customerId()).orElseThrow();
        assertThat(record.getLong("amountPence")).isEqualTo(45_000L);
        assertThat(record.getString("status")).isEqualTo("PENDING");
        assertThat(record.getString("stripePaymentIntentId")).startsWith("pi_fake_");
        assertThat(record.isNull("completedAt")).isTrue();

        assertThat(FAKES.paymentIntentBodies()).singleElement().asString()
            .contains("amount=45000").contains("currency=gbp")
            .contains("agreementNumber]=" + account.agreementNumber());

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "PAYMENT_INITIATED".equals(event.getString("eventType")));
    }

    @Test
    @DisplayName("An amount under a pound is refused before Stripe is called")
    void refusesAnAmountUnderOnePound() {
        var response = client.postJson(PATH,
            "{\"agreementId\":%d,\"amountPence\":99}".formatted(account.agreementId()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("minimum £1.00");
        assertThat(FAKES.paymentIntentBodies()).isEmpty();
    }

    @Test
    @DisplayName("A request without an agreement or with broken JSON is refused")
    void refusesAMalformedRequest() {
        assertThat(client.postJson(PATH, "{\"amountPence\":45000}").statusCode()).isEqualTo(400);
        assertThat(client.postJson(PATH, "not json at all").statusCode()).isEqualTo(400);
        assertThat(FAKES.paymentIntentBodies()).isEmpty();
    }

    @Test
    @DisplayName("Paying towards someone else's agreement fails with a JSON error, not an HTML page")
    void refusesAnotherCustomersAgreement() {
        var other = account();

        var response = client.postJson(PATH,
            "{\"agreementId\":%d,\"amountPence\":45000}".formatted(other.agreementId()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("content-type")).contains("application/json");
        assertThat(response.body()).isEqualTo("{\"error\":\"Payment could not be started. Please try again.\"}");
    }

    @Test
    @DisplayName("A succeeded webhook completes the payment, audits it and emails the customer")
    void completesOnSuccess() {
        var intentId = startPaymentAndReturnIntentId();

        var response = webhook("evt_success_1", "payment_intent.succeeded", intentId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");

        var record = entity("PaymentRecord", "stripePaymentIntentId", intentId).orElseThrow();
        assertThat(record.getString("status")).isEqualTo("COMPLETED");
        assertThat(record.getString("webhookEventId")).isEqualTo("evt_success_1");
        assertThat(record.isNull("completedAt")).isFalse();

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "PAYMENT_COMPLETED".equals(event.getString("eventType")));

        eventually(() -> !FAKES.emails().isEmpty());
        assertThat(FAKES.emails().getFirst().subject()).isEqualTo("Payment Confirmation - Azadi Finance");
        assertThat(FAKES.emails().getFirst().html()).contains("£450.00");
    }

    @Test
    @DisplayName("A failed webhook marks the payment failed and sends no email")
    void marksFailureWithoutEmailing() {
        var intentId = startPaymentAndReturnIntentId();

        var response = webhook("evt_failed_1", "payment_intent.payment_failed", intentId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(entity("PaymentRecord", "stripePaymentIntentId", intentId).orElseThrow().getString("status"))
            .isEqualTo("FAILED");
        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "PAYMENT_FAILED".equals(event.getString("eventType")));
        assertThat(FAKES.emails()).isEmpty();
    }

    @Test
    @DisplayName("An unsigned webhook is refused and changes nothing")
    void refusesAnUnsignedWebhook() {
        var intentId = startPaymentAndReturnIntentId();
        var payload = StripeEvents.event("evt_forged", "payment_intent.succeeded", intentId);

        var response = client().postJson("/api/stripe/webhook", payload, Map.of("Stripe-Signature", StripeEvents.forgedSignature()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("Invalid signature");
        assertThat(entity("PaymentRecord", "stripePaymentIntentId", intentId).orElseThrow().getString("status"))
            .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("The same event delivered twice is applied once")
    void ignoresADuplicateEvent() {
        var intentId = startPaymentAndReturnIntentId();
        webhook("evt_once", "payment_intent.succeeded", intentId);
        eventually(() -> !FAKES.emails().isEmpty());

        var response = webhook("evt_once", "payment_intent.succeeded", intentId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(FAKES.emails()).hasSize(1);
        assertThat(auditEvents(account.customerId())
            .stream().filter(event -> "PAYMENT_COMPLETED".equals(event.getString("eventType"))).toList())
            .hasSize(1);
    }

    @Test
    @DisplayName("A webhook for an intent we never recorded is accepted and ignored")
    void toleratesAnUnknownIntent() {
        var response = webhook("evt_unknown", "payment_intent.succeeded", "pi_never_seen");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(entities("PaymentRecord", "stripePaymentIntentId", "pi_never_seen")).isEmpty();
    }

    @Test
    @DisplayName("An event type we do not act on is accepted and ignored")
    void toleratesAnUninterestingEvent() {
        var intentId = startPaymentAndReturnIntentId();

        var response = webhook("evt_other", "payment_intent.created", intentId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(entity("PaymentRecord", "stripePaymentIntentId", intentId).orElseThrow().getString("status"))
            .isEqualTo("PENDING");
    }

    private String startPaymentAndReturnIntentId() {
        client.postJson(PATH, "{\"agreementId\":%d,\"amountPence\":45000}".formatted(account.agreementId()));
        return entity("PaymentRecord", "customerId", account.customerId()).orElseThrow()
            .getString("stripePaymentIntentId");
    }

    private HttpResponse<String> webhook(String eventId, String type, String intentId) {
        var payload = StripeEvents.event(eventId, type, intentId);
        return client().postJson("/api/stripe/webhook", payload, Map.of("Stripe-Signature", StripeEvents.signature(payload, WEBHOOK_SECRET)));
    }
}
