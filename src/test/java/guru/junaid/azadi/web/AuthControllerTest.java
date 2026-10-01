package guru.junaid.azadi.web;

import guru.junaid.azadi.auth.AuthController;
import guru.junaid.azadi.auth.AuthService;
import guru.junaid.azadi.auth.Session;
import guru.junaid.azadi.auth.Sessions;
import guru.junaid.azadi.config.Cookies;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@RoutingTest
class AuthControllerTest {

    private static final AuthService AUTH = mock(AuthService.class);
    private static final Sessions SESSIONS = new Sessions();

    private final DirectClient client;

    AuthControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.anonymous(rules);
        new AuthController(ControllerTests.WEB, AUTH, SESSIONS, new Cookies(false), true).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(AUTH);
    }

    @Test
    @DisplayName("The root path sends the browser to the login page")
    void rootRedirectsToLogin() {
        try (var response = client.get("/").followRedirects(false).request()) {
            assertThat(ControllerTests.redirect(response)).isEqualTo("/login");
        }
    }

    @Test
    @DisplayName("GET /login renders the form with the demo shortcut, GET /login-error adds the error")
    void rendersTheLoginPage() {
        try (var response = client.get("/login").followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("demo-login-link").contains("name=\"username\"");
        }
        try (var response = client.get("/login-error").followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("Invalid credentials. Please try again.");
        }
    }

    @Test
    @DisplayName("POST /login with accepted credentials sets the session and CSRF cookies and goes to the account page")
    void loginOpensASession() {
        when(AUTH.login("AGR-001", "25/3/1990|SW1A 1AA")).thenReturn(Optional.of(new Session("sid-9", "CUST-1", "Test Customer")));

        try (var response = client.post("/login").followRedirects(false).submit("username=AGR-001&password=25%2F3%2F1990%7CSW1A+1AA")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo("/my-account");
            assertThat(response.headers().values(HeaderNames.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith(Cookies.SESSION + "=sid-9") && cookie.contains("HttpOnly"))
                .anyMatch(cookie -> cookie.startsWith(Cookies.CSRF + "="));
        }
    }

    @Test
    @DisplayName("POST /login with refused credentials goes back to the error page without a cookie")
    void loginFailureRedirectsToError() {
        when(AUTH.login("AGR-001", "wrong")).thenReturn(Optional.empty());

        try (var response = client.post("/login").followRedirects(false).submit("username=AGR-001&password=wrong")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo("/login-error");
            assertThat(response.headers().values(HeaderNames.SET_COOKIE)).noneMatch(cookie -> cookie.startsWith(Cookies.SESSION + "="));
        }
    }

    @Test
    @DisplayName("POST /logout clears the session cookie and returns to the login page")
    void logoutClearsTheCookie() {
        try (var response = client.post("/logout").followRedirects(false).submit("")) {
            assertThat(response.status()).isEqualTo(Status.FOUND_302);
            assertThat(response.headers().first(HeaderNames.LOCATION)).contains("/login");
            assertThat(response.headers().values(HeaderNames.SET_COOKIE)).anyMatch(cookie -> cookie.startsWith(Cookies.SESSION + "="));
        }
    }
}
