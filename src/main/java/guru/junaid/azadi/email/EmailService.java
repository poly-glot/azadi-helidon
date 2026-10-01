package guru.junaid.azadi.email;

import com.google.gson.Gson;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.auth.CustomerRepository;
import io.helidon.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());
    private static final Gson GSON = new Gson();
    private static final int FIRST_ERROR_STATUS = 400;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final CustomerRepository customers;
    private final String apiKey;
    private final String from;
    private final String url;

    public EmailService(CustomerRepository customers, Config config) {
        this.customers = customers;
        this.apiKey = config.get("resend.api.key").asString().orElse("");
        this.from = config.get("resend.from.email").asString().get();
        this.url = config.get("resend.api.url").asString().get();
    }

    public void paymentConfirmation(String customerId, long amountPence) {
        sendToCustomer(customerId, "Payment Confirmation - Azadi Finance", EmailTemplates.paymentConfirmation(amountPence));
    }

    public void bankDetailsUpdated(String customerId) {
        sendToCustomer(customerId, "Bank Details Updated - Azadi Finance", EmailTemplates.bankDetailsUpdated());
    }

    private void sendToCustomer(String customerId, String subject, String html) {
        customers.findByCustomerId(customerId).map(Customer::email).filter(e -> e != null && !e.isBlank())
            .ifPresent(email -> send(email, subject, html));
    }

    public void send(String to, String subject, String html) {
        var body = GSON.toJson(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html));
        var request = HttpRequest.newBuilder().uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(r -> report(to, r))
            .exceptionally(t -> {
                LOG.severe(() -> "Error sending email to " + to + ": " + t);
                return null;
            });
    }

    private static void report(String to, HttpResponse<String> response) {
        if (response.statusCode() >= FIRST_ERROR_STATUS) {
            LOG.severe(() -> "Failed to send email to " + to + ": " + response.statusCode() + " - " + response.body());
        } else {
            LOG.info(() -> "Email sent successfully to " + to);
        }
    }
}
