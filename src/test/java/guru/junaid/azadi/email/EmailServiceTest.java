package guru.junaid.azadi.email;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.auth.CustomerRepository;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private CustomerRepository customers;

    private final List<JsonObject> received = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private HttpServer resend;
    private EmailService email;

    @BeforeEach
    void startFakeResend() throws IOException {
        resend = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        resend.createContext("/emails", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            received.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        resend.start();

        var config = Config.builder().addSource(ConfigSources.create(Map.of(
                "resend.api.key", "re_test", "resend.from.email", "noreply@junaid.guru",
                "resend.api.url", "http://localhost:" + resend.getAddress().getPort() + "/emails")))
            .disableEnvironmentVariablesSource().disableSystemPropertiesSource().build();
        email = new EmailService(customers, config);
    }

    @AfterEach
    void stopFakeResend() {
        resend.stop(0);
    }

    @Test
    @DisplayName("A payment confirmation goes to the customer's address with the amount")
    void sendsThePaymentConfirmation() {
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.customer(Fixtures.CUSTOMER_ID)));

        email.paymentConfirmation(Fixtures.CUSTOMER_ID, 45_000L);

        eventually();
        var sent = received.getFirst();
        assertThat(sent.get("from").getAsString()).isEqualTo("noreply@junaid.guru");
        assertThat(sent.getAsJsonArray("to").get(0).getAsString()).isEqualTo("test@example.com");
        assertThat(sent.get("subject").getAsString()).isEqualTo("Payment Confirmation - Azadi Finance");
        assertThat(sent.get("html").getAsString()).contains("£450.00");
        assertThat(authorizations).containsExactly("Bearer re_test");
    }

    @Test
    @DisplayName("A customer without an email address gets nothing")
    void skipsCustomersWithoutAnAddress() throws InterruptedException {
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID))
            .thenReturn(Optional.of(new Customer(7L, Fixtures.CUSTOMER_ID, "Test Customer", " ", Fixtures.DOB, Fixtures.POSTCODE, null, null, null, null, null)));

        email.bankDetailsUpdated(Fixtures.CUSTOMER_ID);
        Thread.sleep(200);

        assertThat(received).isEmpty();
    }

    private void eventually() {
        for (var attempt = 0; attempt < 100 && received.isEmpty(); attempt++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(received).isNotEmpty();
    }
}
