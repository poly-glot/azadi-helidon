package guru.junaid.azadi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import guru.junaid.azadi.common.Web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class Fakes implements AutoCloseable {

    record Email(String to, String subject, String html) { }

    private static final Gson GSON = new Gson();

    private final HttpServer server;
    private final List<Email> emails = new CopyOnWriteArrayList<>();
    private final List<String> paymentIntentBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    private Fakes(HttpServer server) {
        this.server = server;
    }

    static Fakes start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var fakes = new Fakes(server);
        server.createContext("/v1/payment_intents", fakes::paymentIntent);
        server.createContext("/emails", fakes::email);
        server.start();
        return fakes;
    }

    String stripeApiBase() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    String resendApiUrl() {
        return stripeApiBase() + "/emails";
    }

    List<Email> emails() {
        return List.copyOf(emails);
    }

    List<String> paymentIntentBodies() {
        return List.copyOf(paymentIntentBodies);
    }

    void reset() {
        emails.clear();
        paymentIntentBodies.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void paymentIntent(HttpExchange exchange) throws IOException {
        var body = read(exchange);
        paymentIntentBodies.add(body);

        var id = "pi_fake_" + sequence.incrementAndGet();
        respond(exchange, GSON.toJson(intent(id, amountOf(body))));
    }

    private void email(HttpExchange exchange) throws IOException {
        var sent = GSON.fromJson(read(exchange), JsonObject.class);
        emails.add(new Email(sent.getAsJsonArray("to").get(0).getAsString(),
            sent.get("subject").getAsString(), sent.get("html").getAsString()));

        respond(exchange, "{\"id\":\"email_fake\"}");
    }

    private static JsonObject intent(String id, long amount) {
        var intent = new JsonObject();
        intent.addProperty("id", id);
        intent.addProperty("object", "payment_intent");
        intent.addProperty("amount", amount);
        intent.addProperty("currency", "gbp");
        intent.addProperty("client_secret", id + "_secret_fake");
        intent.addProperty("status", "requires_payment_method");
        intent.addProperty("livemode", false);
        return intent;
    }

    private static long amountOf(String formBody) {
        return Long.parseLong(Web.parseForm(formBody).getOrDefault("amount", "0"));
    }

    private static String read(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
