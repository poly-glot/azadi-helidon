package guru.junaid.azadi;

import guru.junaid.azadi.config.Cookies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountFlowIntegrationTest extends BaseIntegrationTest {

    private Account account;
    private Client client;

    @BeforeEach
    void signIn() {
        account = account();
        client = signedIn(account);
    }

    @Test
    @DisplayName("The account page lists the agreement with its balance and vehicle")
    void listsTheAgreement() {
        var body = client.get("/my-account").body();

        assertThat(body)
            .contains(account.agreementNumber())
            .contains("£12,000.00")
            .contains("2024 Test Vehicle")
            .contains("AB24 TST")
            .contains("Test Customer");
    }

    @Test
    @DisplayName("Every agreement of the customer is listed")
    void listsEveryAgreement() {
        agreement(account.customerId(), "AGR-SECOND-ONE");

        assertThat(client.get("/my-account").body())
            .contains(account.agreementNumber())
            .contains("AGR-SECOND-ONE");
    }

    @Test
    @DisplayName("The agreement detail page shows the full agreement")
    void showsAgreementDetail() {
        var body = client.get("/agreements/" + account.agreementId()).body();

        assertThat(body)
            .contains(account.agreementNumber())
            .contains("6.9%")
            .contains("£0.10")
            .contains("AB24 TST");
    }

    @Test
    @DisplayName("An agreement that does not exist renders the not-found page")
    void rejectsAnUnknownAgreement() {
        var response = client.get("/agreements/999999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("The page or resource you requested could not be found.");
    }

    @Test
    @DisplayName("An agreement id that is not a number is a bad request")
    void rejectsANonNumericAgreementId() {
        var response = client.get("/agreements/not-a-number");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("The request contained invalid data.");
    }

    @Test
    @DisplayName("Documents are listed with their file type")
    void listsDocuments() {
        document(account.customerId(), "Finance Agreement", "finance-agreement.pdf");
        document(account.customerId(), "Welcome Letter", "welcome.docx");

        var body = client.get("/my-documents").body();

        assertThat(body).contains("finance-agreement.pdf").contains("welcome.docx");
    }

    @Test
    @DisplayName("The contact details page shows what is on file")
    void showsContactDetails() {
        var body = client.get("/my-contact-details").body();

        assertThat(body).contains("1 Test Street").contains("SW1A 1AA").contains("07000000000");
    }

    @Test
    @DisplayName("Updating contact details saves them, confirms once and leaves an audit trail")
    void updatesContactDetails() {
        var response = client.postForm("/my-contact-details", Map.of(
            "homePhone", "02011112222",
            "mobilePhone", "07999888777",
            "email", "updated@example.com",
            "houseName", "2 New Street",
            "postcode", "E1 6AN"));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/my-contact-details");

        var customer = entity("Customer", "customerId", account.customerId()).orElseThrow();
        assertThat(customer.getString("email")).isEqualTo("updated@example.com");
        assertThat(customer.getString("phone")).isEqualTo("02011112222");
        assertThat(customer.getString("mobilePhone")).isEqualTo("07999888777");
        assertThat(customer.getString("addressLine1")).isEqualTo("2 New Street");
        assertThat(customer.getString("postcode")).isEqualTo("E1 6AN");

        assertThat(auditEvents(account.customerId()))
            .anyMatch(event -> "CONTACT_DETAILS_UPDATED".equals(event.getString("eventType")));

        var afterRedirect = client.get("/my-contact-details");
        assertThat(afterRedirect.body()).contains("Your contact details have been updated successfully.");
        assertThat(client.get("/my-contact-details").body()).doesNotContain("Your contact details have been updated successfully.");
    }

    @Test
    @DisplayName("Blank fields leave the stored value alone")
    void ignoresBlankFields() {
        client.postForm("/my-contact-details", Map.of(
            "homePhone", "", "mobilePhone", "  ", "email", "kept@example.com", "houseName", "", "postcode", ""));

        var customer = entity("Customer", "customerId", account.customerId()).orElseThrow();
        assertThat(customer.getString("email")).isEqualTo("kept@example.com");
        assertThat(customer.getString("phone")).isEqualTo("02000000000");
        assertThat(customer.getString("addressLine1")).isEqualTo("1 Test Street");
    }

    @Test
    @DisplayName("The audit trail records the client IP and a hashed session id, never the session id itself")
    void auditsWithoutLeakingTheSession() {
        client.postForm("/my-contact-details", Map.of("email", "audited@example.com"));

        var event = auditEvents(account.customerId()).getFirst();

        assertThat(event.getString("ipAddress")).startsWith("10.");
        assertThat(event.getString("sessionIdHash")).hasSize(64).isNotEqualTo(client.cookie(Cookies.SESSION));
        assertThat(event.getString("details")).contains("audited@example.com");
    }

    @Test
    @DisplayName("Help pages are served to a signed-in customer")
    void servesHelpPages() {
        assertThat(client.get("/help/faqs").statusCode()).isEqualTo(200);
        assertThat(client.get("/help/ways-to-pay").statusCode()).isEqualTo(200);
        assertThat(client.get("/help/contact-us").statusCode()).isEqualTo(200);
    }
}
