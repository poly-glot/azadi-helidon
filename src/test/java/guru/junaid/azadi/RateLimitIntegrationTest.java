package guru.junaid.azadi;

import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIntegrationTest extends BaseIntegrationTest {

    private static final int GENERAL_LIMIT = 50;
    private static final int LOGIN_LIMIT = 5;
    private static final int PAYMENT_LIMIT = 3;
    private static final AtomicInteger CALLERS = new AtomicInteger();

    private static WebServer throttled;

    @BeforeAll
    static void startThrottledServer() {
        throttled = Main.start(config(GENERAL_LIMIT));
    }

    @AfterAll
    static void stopThrottledServer() {
        throttled.stop();
    }

    @Test
    @DisplayName("Requests past the general limit are answered 429 in plain text")
    void throttlesTheGeneralLimit() {
        var client = throttledClient();

        var statuses = IntStream.rangeClosed(1, GENERAL_LIMIT)
            .map(request -> client.get("/login").statusCode())
            .boxed()
            .toList();
        var throttledResponse = client.get("/login");

        assertThat(statuses).containsOnly(200);
        assertThat(throttledResponse.statusCode()).isEqualTo(429);
        assertThat(throttledResponse.body()).isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    @DisplayName("One caller's limit does not affect another")
    void countsPerCaller() {
        var first = throttledClient();
        IntStream.rangeClosed(1, GENERAL_LIMIT + 1).forEach(request -> first.get("/login"));

        assertThat(throttledClient().get("/login").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("A sixth login attempt from one address is throttled before the lockout answers")
    void throttlesRepeatedLoginAttempts() {
        var account = account();
        var client = throttledClient();
        client.get("/login");

        var wrong = Map.of("username", account.agreementNumber(), "password", "1/1/1980|E1 6AN");
        var statuses = IntStream.rangeClosed(1, LOGIN_LIMIT)
            .map(attempt -> client.postForm("/login", wrong).statusCode())
            .boxed()
            .toList();

        assertThat(statuses).containsOnly(302);
        assertThat(client.postForm("/login", wrong).statusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("A fourth payment attempt in an hour is throttled per session")
    void throttlesRepeatedPayments() {
        var account = account();
        var client = signIn(throttledClient(), account);
        var payment = "{\"agreementId\":%d,\"amountPence\":45000}".formatted(account.agreementId());

        var statuses = IntStream.rangeClosed(1, PAYMENT_LIMIT)
            .map(attempt -> client.postJson("/finance/make-a-payment", payment).statusCode())
            .boxed()
            .toList();

        assertThat(statuses).containsOnly(200);
        assertThat(client.postJson("/finance/make-a-payment", payment).statusCode()).isEqualTo(429);
    }

    private static Client throttledClient() {
        var n = CALLERS.incrementAndGet();
        return new Client("http://localhost:" + throttled.port(), "172.16." + (n >> 8 & 0xFF) + "." + (n & 0xFF));
    }
}
