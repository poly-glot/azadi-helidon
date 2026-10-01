package guru.junaid.azadi;

import guru.junaid.azadi.auth.Sessions;
import guru.junaid.azadi.config.Cookies;
import guru.junaid.azadi.config.SecurityFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/login-error", "/cookies", "/privacy", "/terms", "/actuator/health"})
    @DisplayName("Public pages are served without a session")
    void servesPublicPages(String path) {
        assertThat(client().get(path).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("The root path sends the browser to the login page")
    void redirectsRootToLogin() {
        var response = client().get("/");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/my-account", "/my-documents", "/my-contact-details",
        "/finance/make-a-payment", "/finance/update-bank-details", "/finance/settlement-figure",
        "/finance/change-payment-date", "/finance/request-a-statement", "/help/faqs"})
    @DisplayName("Private pages redirect to the login page without a session")
    void protectsPrivatePages(String path) {
        var response = client().get(path);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/login");
    }

    @Test
    @DisplayName("Every response carries the security headers")
    void sendsSecurityHeaders() {
        var headers = client().get("/login").headers();

        assertThat(headers.firstValue("x-content-type-options")).contains("nosniff");
        assertThat(headers.firstValue("x-frame-options")).contains("DENY");
        assertThat(headers.firstValue("referrer-policy")).contains("strict-origin-when-cross-origin");
        assertThat(headers.firstValue("permissions-policy")).contains("camera=(), microphone=(), geolocation=()");
        assertThat(headers.firstValue("strict-transport-security")).contains("max-age=63072000; includeSubDomains");
    }

    @Test
    @DisplayName("The CSP nonce changes per request and appears in the page")
    void noncesThePolicyAndThePage() {
        var client = client();

        var first = client.get("/login");
        var second = client.get("/login");

        var nonce = nonce(first.headers().firstValue("content-security-policy").orElseThrow());
        assertThat(nonce).isNotBlank();
        assertThat(first.body()).contains("nonce=\"" + nonce + "\"");
        assertThat(nonce).isNotEqualTo(nonce(second.headers().firstValue("content-security-policy").orElseThrow()));
    }

    @Test
    @DisplayName("The policy allows only the origin plus Stripe and Google Fonts")
    void restrictsContentSources() {
        var policy = client().get("/login").headers().firstValue("content-security-policy").orElseThrow();

        assertThat(policy)
            .contains("default-src 'self';")
            .contains("https://js.stripe.com")
            .contains("connect-src 'self' https://api.stripe.com;")
            .contains("form-action 'self';")
            .contains("report-uri /api/csp-report;");
    }

    @Test
    @DisplayName("The session cookie is HttpOnly and same site")
    void hardensTheSessionCookie() {
        var account = account();
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", Map.of("username", account.agreementNumber(),
            "password", account.dob().format(DOB_FORMAT) + "|" + account.postcode()));

        assertThat(response.headers().allValues("set-cookie"))
            .anyMatch(cookie -> cookie.startsWith(Cookies.SESSION + "=")
                && cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict"));
    }

    @Test
    @DisplayName("A form post without the CSRF token is refused")
    void refusesAPostWithoutCsrf() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", "username=AGR-1&password=1/1/1990|SW1A 1AA", Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("A form post with someone else's CSRF token is refused")
    void refusesAPostWithTheWrongCsrf() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/login", "_csrf=" + Sessions.token(), Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("The CSRF token may travel in the X-XSRF-TOKEN header instead")
    void acceptsTheCsrfHeader() {
        var client = client();
        client.get("/login");

        var response = client.postForm("/logout", "",
            Map.of(SecurityFilter.XSRF_HEADER.defaultCase(), client.cookie(Cookies.CSRF)));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
    }

    @Test
    @DisplayName("Stripe's webhook and the CSP report endpoint are exempt from CSRF")
    void exemptsMachineEndpoints() {
        var client = client();

        assertThat(client.postJson("/api/csp-report", "{}", Map.of()).statusCode()).isEqualTo(204);
        assertThat(client.postJson("/api/stripe/webhook", "{}", Map.of()).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Assets are served without a session and revalidate with an ETag")
    void servesAssetsToAnyone() {
        var client = client();

        var first = client.get("/assets/img/logo-azadi.svg");
        var etag = first.headers().firstValue("etag").orElseThrow();
        var revalidated = client.get("/assets/img/logo-azadi.svg", "If-None-Match", etag);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(revalidated.statusCode()).isEqualTo(304);
    }

    @Test
    @DisplayName("An unknown path renders the not-found page for a signed-in customer")
    void rendersTheNotFoundPage() {
        var response = signedIn(account()).get("/no/such/page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("The page or resource you requested could not be found.");
    }

    @Test
    @DisplayName("An unknown path looks like the login redirect to anyone without a session")
    void hidesUnknownPathsFromStrangers() {
        var response = client().get("/no/such/page");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/login");
    }

    @Test
    @DisplayName("A post with no body at all is a CSRF failure, not a server error")
    void refusesAPostWithNoBody() {
        var client = client();
        client.get("/login");

        assertThat(client.postForm("/logout", "", Map.of()).statusCode()).isEqualTo(403);
    }

    private static String nonce(String policy) {
        var start = policy.indexOf("'nonce-") + "'nonce-".length();
        return policy.substring(start, policy.indexOf('\'', start));
    }
}
