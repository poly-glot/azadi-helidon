package guru.junaid.azadi;

import guru.junaid.azadi.common.Db;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceFlowIntegrationTest extends BaseIntegrationTest {

    private static final String SETTLEMENT = "/finance/settlement-figure";
    private static final String PAYMENT_DATE = "/finance/change-payment-date";
    private static final String STATEMENT = "/finance/request-a-statement";

    private Account account;
    private Client client;

    @BeforeEach
    void signIn() {
        account = account();
        client = signedIn(account);
    }


    @Test
    @DisplayName("The settlement page offers the agreements to choose from")
    void offersAgreementsForSettlement() {
        assertThat(client.get(SETTLEMENT).body()).contains(account.agreementNumber());
    }

    @Test
    @DisplayName("A settlement figure adds the early settlement fee and is stored with a validity date")
    void calculatesTheSettlementFigure() {
        var response = client.postForm(SETTLEMENT, Map.of("agreementId", String.valueOf(account.agreementId())));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("£12,240.00");

        var stored = entity("SettlementFigure", "customerId", account.customerId()).orElseThrow();
        assertThat(stored.getLong("amountPence")).isEqualTo(1_224_000L);
        assertThat(stored.getLong("agreementId")).isEqualTo(account.agreementId());
        assertThat(validUntil(stored)).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(28));
    }

    @Test
    @DisplayName("Submitting no agreement just re-renders the page")
    void toleratesAnEmptyAgreementChoice() {
        var response = client.postForm(SETTLEMENT, Map.of("agreementId", ""));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(entities("SettlementFigure", "customerId", account.customerId())).isEmpty();
    }

    @Test
    @DisplayName("The payment date page shows the current day with its ordinal")
    void showsTheCurrentPaymentDay() {
        assertThat(client.get(PAYMENT_DATE).body()).contains("14th");
    }

    @Test
    @DisplayName("Changing the payment date moves the next payment and audits the change")
    void changesThePaymentDate() {
        var response = client.postForm(PAYMENT_DATE, Map.of(
            "agreementId", String.valueOf(account.agreementId()), "newPaymentDate", "21"));

        assertThat(response.statusCode()).isEqualTo(302);

        var agreement = entity("Agreement", "agreementNumber", account.agreementNumber()).orElseThrow();
        assertThat(agreement.getBoolean("paymentDateChanged")).isTrue();
        assertThat(Db.date(agreement, "nextPaymentDate").getDayOfMonth()).isEqualTo(21);

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "PAYMENT_DATE_CHANGED".equals(event.getString("eventType"))
                && event.getString("details").contains("21"));
        assertThat(client.get(PAYMENT_DATE).body())
            .contains("Your payment date has been changed successfully.")
            .contains("already used your one-time payment date change");
    }

    @Test
    @DisplayName("The payment date may only be changed once")
    void refusesASecondChange() {
        client.postForm(PAYMENT_DATE, Map.of(
            "agreementId", String.valueOf(account.agreementId()), "newPaymentDate", "21"));

        var response = client.postForm(PAYMENT_DATE, Map.of(
            "agreementId", String.valueOf(account.agreementId()), "newPaymentDate", "7"));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(client.get(PAYMENT_DATE).body()).contains("Payment date has already been changed");
        assertThat(Db.date(entity("Agreement", "agreementNumber", account.agreementNumber()).orElseThrow(),
            "nextPaymentDate").getDayOfMonth()).isEqualTo(21);
    }

    @Test
    @DisplayName("A day outside 1 to 28 is a bad request")
    void refusesADayOutsideTheAllowedRange() {
        var response = client.postForm(PAYMENT_DATE, Map.of(
            "agreementId", String.valueOf(account.agreementId()), "newPaymentDate", "31"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("The request contained invalid data.");
        assertThat(entity("Agreement", "agreementNumber", account.agreementNumber()).orElseThrow()
            .contains("paymentDateChanged")).isFalse();
    }


    @Test
    @DisplayName("The statement page shows where the statement will be sent")
    void showsWhereTheStatementGoes() {
        var body = client.get(STATEMENT).body();

        assertThat(body).contains(customerEmail(account.customerId())).contains("1 Test Street");
    }

    @Test
    @DisplayName("Requesting a statement records it as pending and audits it")
    void requestsAStatement() {
        var response = client.postForm(STATEMENT, Map.of("agreementId", String.valueOf(account.agreementId())));

        assertThat(response.statusCode()).isEqualTo(302);

        var request = entity("StatementRequest", "customerId", account.customerId()).orElseThrow();
        assertThat(request.getString("status")).isEqualTo("PENDING");
        assertThat(request.getLong("agreementId")).isEqualTo(account.agreementId());

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "STATEMENT_REQUESTED".equals(event.getString("eventType")));
        assertThat(client.get(STATEMENT).body()).contains("Your statement request has been submitted successfully.");
    }

    @Test
    @DisplayName("With no agreement chosen the only agreement is used")
    void fallsBackToTheOnlyAgreement() {
        client.postForm(STATEMENT, Map.of("agreementId", ""));

        assertThat(entity("StatementRequest", "customerId", account.customerId()).orElseThrow()
            .getLong("agreementId")).isEqualTo(account.agreementId());
    }

    private static LocalDate validUntil(com.google.cloud.datastore.Entity settlement) {
        return Db.date(settlement, "validUntil");
    }
}
