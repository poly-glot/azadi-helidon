package guru.junaid.azadi;

import guru.junaid.azadi.bank.BankDetailsEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BankDetailsFlowIntegrationTest extends BaseIntegrationTest {

    private static final String PATH = "/finance/update-bank-details";

    private final BankDetailsEncryptor crypto = new BankDetailsEncryptor(ENCRYPTION_KEY, ENCRYPTION_SALT);

    private Account account;
    private Client client;

    @BeforeEach
    void signIn() {
        account = account();
        client = signedIn(account);
    }

    @Test
    @DisplayName("With nothing on file the form is empty")
    void showsAnEmptyForm() {
        var body = client.get(PATH).body();

        assertThat(body).contains("name=\"accountNumber\"").doesNotContain("****");
    }

    @Test
    @DisplayName("Existing details are shown masked, never in full")
    void masksExistingDetails() {
        bankDetails(account.customerId(), "11227788", "44-55-66");

        var body = client.get(PATH).body();

        assertThat(body).contains("****7788").contains("**-**-66").doesNotContain("11227788").doesNotContain("44-55-66");
    }

    @Test
    @DisplayName("Valid details are stored encrypted, audited and confirmed by email")
    void storesNewDetails() {
        var response = client.postForm(PATH, Map.of(
            "accountHolderName", "Test Customer", "accountNumber", "12345678", "sortCode", "11-22-33"));

        assertThat(response.statusCode()).isEqualTo(302);

        var stored = entity("BankDetails", "customerId", account.customerId()).orElseThrow();
        assertThat(stored.getString("lastFourAccount")).isEqualTo("5678");
        assertThat(stored.getString("lastTwoSortCode")).isEqualTo("33");
        assertThat(stored.getString("encryptedAccountNumber")).isNotEqualTo("12345678");
        assertThat(crypto.decrypt(stored.getString("encryptedAccountNumber"))).isEqualTo("12345678");
        assertThat(crypto.decrypt(stored.getString("encryptedSortCode"))).isEqualTo("11-22-33");

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "BANK_DETAILS_UPDATED".equals(event.getString("eventType"))
                && event.getString("details").contains("5678"));

        eventually(() -> !FAKES.emails().isEmpty());
        var email = FAKES.emails().getFirst();
        assertThat(email.subject()).isEqualTo("Bank Details Updated - Azadi Finance");
        assertThat(email.to()).isEqualTo(customerEmail(account.customerId()));
        assertThat(email.html()).contains("Your bank details have been successfully updated");

        assertThat(client.get(PATH).body()).contains("Your bank details have been updated successfully.");
    }

    @Test
    @DisplayName("A second update replaces the stored details rather than adding a record")
    void replacesExistingDetails() {
        bankDetails(account.customerId(), "11227788", "44-55-66");

        client.postForm(PATH, Map.of(
            "accountHolderName", "New Name", "accountNumber", "87654321", "sortCode", "99-88-77"));

        var stored = entities("BankDetails", "customerId", account.customerId());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getString("accountHolderName")).isEqualTo("New Name");
        assertThat(stored.getFirst().getString("lastFourAccount")).isEqualTo("4321");
        assertThat(crypto.decrypt(stored.getFirst().getString("encryptedSortCode"))).isEqualTo("99-88-77");
    }

    @Test
    @DisplayName("Invalid details are refused with every problem listed and nothing stored")
    void refusesInvalidDetails() {
        var response = client.postForm(PATH, Map.of(
            "accountHolderName", "", "accountNumber", "123", "sortCode", "112233"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
            .contains("Account holder name is required")
            .contains("Account number must be exactly 8 digits")
            .contains("Sort code must be in XX-XX-XX format");
        assertThat(entities("BankDetails", "customerId", account.customerId())).isEmpty();
        assertThat(FAKES.emails()).isEmpty();
    }

    @Test
    @DisplayName("A rejected update still shows the details already on file")
    void keepsShowingStoredDetailsOnFailure() {
        bankDetails(account.customerId(), "11227788", "44-55-66");

        var response = client.postForm(PATH, Map.of(
            "accountHolderName", "Test Customer", "accountNumber", "nope", "sortCode", "11-22-33"));

        assertThat(response.body()).contains("****7788").contains("Account number must be exactly 8 digits");
    }
}
