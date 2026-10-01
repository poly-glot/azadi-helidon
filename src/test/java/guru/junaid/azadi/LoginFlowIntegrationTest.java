package guru.junaid.azadi;

import guru.junaid.azadi.config.Cookies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoginFlowIntegrationTest extends BaseIntegrationTest {

    private Account account;

    @BeforeEach
    void createAccount() {
        account = account();
    }

    @Test
    @DisplayName("Correct credentials redirect to the account page and open a session")
    void signsIn() {
        var client = signedIn(account);

        assertThat(client.cookie(Cookies.SESSION)).isNotBlank();
        assertThat(client.get("/my-account").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("A postcode is matched without regard to spaces or case")
    void normalisesThePostcode() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", credentials("sw1a1aa"));

        assertThat(response.headers().firstValue("location")).contains("/my-account");
    }

    @Test
    @DisplayName("The wrong date of birth returns to the login page with an error")
    void rejectsTheWrongDateOfBirth() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", Map.of(
            "username", account.agreementNumber(), "password", "1/1/1985|" + DEFAULT_POSTCODE));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/login-error");
        assertThat(client.get("/login-error").body()).contains("Invalid credentials. Please try again.");
    }

    @Test
    @DisplayName("The wrong postcode is rejected")
    void rejectsTheWrongPostcode() {
        var client = client();
        client.get("/login");

        assertThat(client.postForm("/login", credentials("E1 6AN")).headers().firstValue("location"))
            .contains("/login-error");
    }

    @Test
    @DisplayName("An unknown agreement number is rejected")
    void rejectsAnUnknownAgreement() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", Map.of(
            "username", "AGR-DOES-NOT-EXIST", "password", DEFAULT_DOB.format(DOB_FORMAT) + "|" + DEFAULT_POSTCODE));

        assertThat(response.headers().firstValue("location")).contains("/login-error");
    }

    @Test
    @DisplayName("Credentials that are not date|postcode are rejected")
    void rejectsMalformedCredentials() {
        var client = client();
        client.get("/login");

        assertThat(client.postForm("/login", Map.of("username", account.agreementNumber(), "password", "nonsense"))
            .headers().firstValue("location")).contains("/login-error");
    }

    @Test
    @DisplayName("A date of birth that is not a date is rejected")
    void rejectsUnparseableDateOfBirth() {
        var client = client();
        client.get("/login");

        assertThat(client.postForm("/login", credentials("32/13/1990", DEFAULT_POSTCODE))
            .headers().firstValue("location")).contains("/login-error");
    }

    @Test
    @DisplayName("Five failures lock the agreement, so even the right credentials are refused")
    void locksOutAfterFiveFailures() {
        for (var attempt = 0; attempt < 5; attempt++) {
            var attacker = client();
            attacker.get("/login");
            assertThat(attacker.postForm("/login", credentials("E1 6AN")).headers().firstValue("location"))
                .contains("/login-error");
        }

        var client = client();
        client.get("/login");

        assertThat(client.postForm("/login", credentials(DEFAULT_POSTCODE)).headers().firstValue("location"))
            .contains("/login-error");
    }

    @Test
    @DisplayName("A successful login clears the earlier failures")
    void successClearsFailures() {
        var client = client();
        client.get("/login");
        client.postForm("/login", credentials("E1 6AN"));
        client.postForm("/login", credentials("E1 6AN"));

        assertThat(client.postForm("/login", credentials(DEFAULT_POSTCODE)).headers().firstValue("location"))
            .contains("/my-account");
    }

    @Test
    @DisplayName("Logging out ends the session and sends the browser back to the login page")
    void signsOut() {
        var client = signedIn(account);

        var response = client.postForm("/logout", Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/login");
        assertThat(client.get("/my-account").headers().firstValue("location")).contains("/login");
    }

    @Test
    @DisplayName("Logging in again invalidates the previous session")
    void oneSessionPerCustomer() {
        var first = signedIn(account);

        signedIn(account);

        assertThat(first.cookie(Cookies.SESSION)).isNotBlank();
        assertThat(first.get("/my-account").headers().firstValue("location")).contains("/login");
    }

    @Test
    @DisplayName("The login page offers the demo shortcut when demo mode is on")
    void showsDemoModeHint() {
        var body = client().get("/login").body();

        assertThat(body).contains("Login as").contains("demo-login-link");
    }

    private Map<String, String> credentials(String postcode) {
        return credentials(DEFAULT_DOB.format(DOB_FORMAT), postcode);
    }

    private Map<String, String> credentials(String dob, String postcode) {
        return Map.of("username", account.agreementNumber(), "password", dob + "|" + postcode);
    }
}
