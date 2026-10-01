package guru.junaid.azadi;

import com.google.datastore.v1.EntityResult;
import com.google.datastore.v1.QueryResultBatch;
import com.google.datastore.v1.RunQueryResponse;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class Train {

    private static final Logger LOG = Logger.getLogger(Train.class.getName());
    private static final int APP_PORT = 18_080;
    private static final int FAKE_PORT = 19_000;
    private static final String LOOPBACK = InetAddress.getLoopbackAddress().getHostAddress();

    private static final String WEBHOOK_SECRET = java.util.UUID.randomUUID().toString();

    private Train() {
    }

    public static void main(String[] args) throws Exception {
        var archive = Path.of(args[0]);
        var jar = args.length > 1 ? args[1] : "/app/app.jar";
        var jvmFlags = args.length > 2 ? List.of(args).subList(2, args.length) : List.<String>of();

        var fake = startFakeDatastore();
        var command = new java.util.ArrayList<String>();
        command.add(ProcessHandle.current().info().command().orElseThrow());
        command.addAll(jvmFlags);
        command.addAll(List.of("-XX:ArchiveClassesAtExit=" + archive, "-jar", jar));
        var pb = new ProcessBuilder(command).inheritIO();
        pb.environment().putAll(new HashMap<>(java.util.Map.of(
            "PORT", String.valueOf(APP_PORT), "DATASTORE_HOST", LOOPBACK + ":" + FAKE_PORT, "GCP_PROJECT_ID", "train",
            "COOKIE_SECURE", "false", "DEMO_MODE", "true", "STRIPE_API_KEY", "sk_test_train", "STRIPE_WEBHOOK_SECRET", WEBHOOK_SECRET,
            "AZADI_ENCRYPTION_KEY", "train-only-key-for-the-cds-archive", "AZADI_ENCRYPTION_SALT", "a1b2c3d4e5f6a7b8",
            "RESEND_API_KEY", "re_train")));
        var app = pb.start();
        try {
            drive("http://" + LOOPBACK + ":" + APP_PORT);
        } finally {
            app.destroy();                       // SIGTERM: the JVM exits normally and dumps the archive
            if (!app.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                app.destroyForcibly();
            }
            fake.stop(0);
        }
        if (!Files.exists(archive)) {
            LOG.severe(() -> "training failed: no archive at " + archive);
            System.exit(1);
        }
        var megabytes = Files.size(archive) / 1024 / 1024;
        LOG.info(() -> "AppCDS archive created: " + archive + " (" + megabytes + " MB)");
    }

    private static void drive(String base) throws Exception {
        try (var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5)).build()) {
            warm(http, base);
        }
    }

    private static void warm(HttpClient http, String base) throws Exception {
        for (int i = 0; i < 120; i++) {                     // wait for the app
            try {
                if (send(http, "GET", base + "/actuator/health", null, null).statusCode() == 200) {
                    break;
                }
            } catch (java.io.IOException e) {
                Thread.sleep(500);
            }
        }
        var login = send(http, "GET", base + "/login", null, null);
        send(http, "GET", base + "/login-error", null, null);
        for (var page : List.of("/cookies", "/privacy", "/terms", "/my-account", "/finance/make-a-payment", "/nope")) {
            send(http, "GET", base + page, null, null);
        }
        var assets = Pattern.compile("(?:href|src)=\"(/assets/[^\"]+)\"").matcher(login.body());
        while (assets.find()) {
            var first = send(http, "GET", base + assets.group(1), null, null);
            first.headers().firstValue("etag").ifPresent(etag -> {
                try {
                    send(http, "GET", base + assets.group(1), null, etag);          // conditional GET -> 304 path
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        var csrf = http.cookieHandler().map(h -> (CookieManager) h).orElseThrow().getCookieStore().getCookies().stream()
            .filter(c -> "XSRF-TOKEN".equals(c.getName())).findFirst().orElseThrow().getValue();
        send(http, "POST", base + "/login", "username=AGR-000000&password=1/1/1990|AA1 1AA", null);      // no CSRF -> 403
        send(http, "POST", base + "/login", "username=AGR-000000&password=1/1/1990|AA1 1AA&_csrf=" + csrf, null);   // datastore query
        send(http, "POST", base + "/api/csp-report", "{}", null);
        var event = "{\"id\":\"evt_train\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION
            + "\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_train\",\"object\":\"payment_intent\","
            + "\"amount\":1000,\"currency\":\"gbp\",\"status\":\"succeeded\"}}}";
        var ts = String.valueOf(System.currentTimeMillis() / 1000);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var signature = HexFormat.of().formatHex(mac.doFinal((ts + "." + event).getBytes(StandardCharsets.UTF_8)));
        webhook(http, base, event, "t=" + ts + ",v1=deadbeef");                          // bad signature
        webhook(http, base, event, "t=" + ts + ",v1=" + signature);                       // good signature, no record
        for (int i = 0; i < 20; i++) {                                                    // let the JIT/lazy paths warm a bit
            send(http, "GET", base + "/login", null, null);
        }
    }

    private static void webhook(HttpClient http, String base, String body, String signature) throws Exception {
        http.send(HttpRequest.newBuilder(URI.create(base + "/api/stripe/webhook")).header("Stripe-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> send(HttpClient http, String method, String url, String body, String ifNoneMatch) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
        if (ifNoneMatch != null) {
            b.header("If-None-Match", ifNoneMatch);
        }
        if ("POST".equals(method)) {
            b.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }


    private static HttpServer startFakeDatastore() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), FAKE_PORT), 0);
        server.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            var body = ex.getRequestURI().getPath().endsWith(":runQuery")
                ? RunQueryResponse.newBuilder().setBatch(QueryResultBatch.newBuilder()
                    .setMoreResults(QueryResultBatch.MoreResultsType.NO_MORE_RESULTS)
                    .setEntityResultType(EntityResult.ResultType.FULL)).build().toByteArray()
                : new byte[0];
            ex.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            ex.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
        return server;
    }
}
