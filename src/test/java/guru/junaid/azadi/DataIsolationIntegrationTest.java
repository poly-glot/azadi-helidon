package guru.junaid.azadi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataIsolationIntegrationTest extends BaseIntegrationTest {

    private Account mine;
    private Account theirs;
    private Client client;

    @BeforeEach
    void signInAsOneOfTwoCustomers() {
        mine = account();
        theirs = account();
        client = signedIn(mine);
    }

    @Test
    @DisplayName("The account page lists only the signed-in customer's agreements")
    void listsOnlyMyAgreements() {
        var body = client.get("/my-account").body();

        assertThat(body).contains(mine.agreementNumber()).doesNotContain(theirs.agreementNumber());
    }

    @Test
    @DisplayName("Another customer's agreement is refused, not shown")
    void refusesAnotherCustomersAgreement() {
        var response = client.get("/agreements/" + theirs.agreementId());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body())
            .contains("You do not have permission to access this resource.")
            .doesNotContain(theirs.agreementNumber());
    }

    @Test
    @DisplayName("A settlement figure cannot be calculated for another customer's agreement")
    void refusesSettlementForAnotherCustomer() {
        var response = client.postForm("/finance/settlement-figure",
            Map.of("agreementId", String.valueOf(theirs.agreementId())));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(entities("SettlementFigure", "customerId", theirs.customerId())).isEmpty();
    }

    @Test
    @DisplayName("Another customer's payment date cannot be changed")
    void refusesPaymentDateChangeForAnotherCustomer() {
        var response = client.postForm("/finance/change-payment-date",
            Map.of("agreementId", String.valueOf(theirs.agreementId()), "newPaymentDate", "7"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(entity("Agreement", "agreementNumber", theirs.agreementNumber()).orElseThrow()
            .contains("paymentDateChanged")).isFalse();
    }

    @Test
    @DisplayName("Only the signed-in customer's documents are listed")
    void listsOnlyMyDocuments() {
        document(mine.customerId(), "Mine", "mine.pdf");
        document(theirs.customerId(), "Theirs", "theirs.pdf");

        assertThat(client.get("/my-documents").body()).contains("mine.pdf").doesNotContain("theirs.pdf");
    }

    @Test
    @DisplayName("Only the signed-in customer's bank details are shown")
    void showsOnlyMyBankDetails() {
        bankDetails(mine.customerId(), "11112222", "11-11-11");
        bankDetails(theirs.customerId(), "33334444", "33-33-33");

        assertThat(client.get("/finance/update-bank-details").body())
            .contains("****2222").doesNotContain("****4444");
    }

    @Test
    @DisplayName("A statement request is recorded against the signed-in customer only")
    void requestsStatementsForMeOnly() {
        client.postForm("/finance/request-a-statement",
            Map.of("agreementId", String.valueOf(mine.agreementId())));

        assertThat(entities("StatementRequest", "customerId", theirs.customerId())).isEmpty();
        assertThat(entities("StatementRequest", "customerId", mine.customerId())).hasSize(1);
    }
}
