package guru.junaid.azadi;

import com.google.cloud.NoCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import guru.junaid.azadi.bank.BankDetailsEncryptor;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

abstract class BaseIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_test_secret";
    static final String ENCRYPTION_KEY = "test-only-key-change-in-prod-32c";
    static final String ENCRYPTION_SALT = "a1b2c3d4e5f6a7b8";
    static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("d/M/yyyy");
    static final LocalDate DEFAULT_DOB = LocalDate.of(1990, 3, 25);
    static final String DEFAULT_POSTCODE = "SW1A 1AA";

    private static final String PROJECT = "test-azadi";
    private static final int EMULATOR_PORT = 8081;
    private static final int GENEROUS_RATE_LIMIT = 100_000;
    private static final List<String> KINDS = List.of("Customer", "Agreement", "Document", "BankDetails",
        "AuditEvent", "SettlementFigure", "StatementRequest", "PaymentRecord");
    private static final AtomicInteger CLIENTS = new AtomicInteger();
    private static final AtomicInteger AGREEMENTS = new AtomicInteger();

    private static final GenericContainer<?> EMULATOR;
    static final Fakes FAKES;
    static final Datastore DATASTORE;
    private static final WebServer SERVER;

    static {
        EMULATOR = new GenericContainer<>("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
            .withCommand("gcloud", "emulators", "firestore", "start",
                "--host-port=0.0.0.0:" + EMULATOR_PORT, "--database-mode=datastore-mode", "--project=" + PROJECT)
            .withExposedPorts(EMULATOR_PORT)
            .waitingFor(Wait.forLogMessage(".*Dev App Server is now running.*", 1));
        EMULATOR.start();

        try {
            FAKES = Fakes.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the Stripe and Resend stand-ins", e);
        }

        var config = config(GENEROUS_RATE_LIMIT);
        DATASTORE = datastore(config);
        SERVER = Main.start(config);
    }

    record Account(String customerId, long agreementId, String agreementNumber, LocalDate dob, String postcode) { }

    static Config config(int generalRateLimit) {
        return Config.builder()
            .addSource(ConfigSources.create(Map.ofEntries(
                Map.entry("server.port", "0"),
                Map.entry("datastore.host", EMULATOR.getHost() + ":" + EMULATOR.getMappedPort(EMULATOR_PORT)),
                Map.entry("gcp.project.id", PROJECT),
                Map.entry("cookie.secure", "false"),
                Map.entry("demo.mode", "true"),
                Map.entry("rate.limit.general", String.valueOf(generalRateLimit)),
                Map.entry("stripe.api.key", "sk_test_dummy"),
                Map.entry("stripe.webhook.secret", WEBHOOK_SECRET),
                Map.entry("stripe.api.base", FAKES.stripeApiBase()),
                Map.entry("vite.stripe.publishable.key", "pk_test_dummy"),
                Map.entry("azadi.encryption.key", ENCRYPTION_KEY),
                Map.entry("azadi.encryption.salt", ENCRYPTION_SALT),
                Map.entry("resend.api.key", "re_dummy"),
                Map.entry("resend.api.url", FAKES.resendApiUrl()))))
            .addSource(ConfigSources.classpath("application.properties"))
            .disableEnvironmentVariablesSource()
            .disableSystemPropertiesSource()
            .build();
    }

    static Datastore datastore(Config config) {
        return DatastoreOptions.newBuilder()
            .setProjectId(config.get("gcp.project.id").asString().get())
            .setDatabaseId(config.get("firestore.db").asString().get())
            .setHost("http://" + config.get("datastore.host").asString().get())
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();
    }

    @BeforeEach
    void clearStateBetweenTests() {
        KINDS.forEach(BaseIntegrationTest::deleteAll);
        FAKES.reset();
    }

    private static void deleteAll(String kind) {
        var keys = DATASTORE.run(Query.newKeyQueryBuilder().setKind(kind).build());
        keys.forEachRemaining(DATASTORE::delete);
    }

    static Client client() {
        var n = CLIENTS.incrementAndGet();
        return new Client("http://localhost:" + SERVER.port(),
            "10." + (n >> 16 & 0xFF) + "." + (n >> 8 & 0xFF) + "." + (n & 0xFF));
    }

    static Client signedIn(Account account) {
        return signIn(client(), account);
    }

    static Client signIn(Client client, Account account) {
        client.get("/login");

        var response = client.postForm("/login", Map.of(
            "username", account.agreementNumber(),
            "password", account.dob().format(DOB_FORMAT) + "|" + account.postcode()));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).contains("/my-account");
        return client;
    }

    static Account account() {
        return account(DEFAULT_DOB, DEFAULT_POSTCODE);
    }

    static Account account(LocalDate dob, String postcode) {
        var agreementNumber = "AGR-%06d".formatted(AGREEMENTS.incrementAndGet());
        var key = DATASTORE.allocateId(DATASTORE.newKeyFactory().setKind("Customer").newKey());
        var customerId = "CUST-" + key.getId();

        DATASTORE.put(Entity.newBuilder(key)
            .set("customerId", customerId)
            .set("fullName", "Test Customer")
            .set("email", "test-" + key.getId() + "@example.com")
            .set("dob", at(dob))
            .set("postcode", postcode)
            .set("phone", "02000000000")
            .set("mobilePhone", "07000000000")
            .set("addressLine1", "1 Test Street")
            .set("city", "London")
            .build());

        return new Account(customerId, agreement(customerId, agreementNumber), agreementNumber, dob, postcode);
    }

    static long agreement(String customerId, String agreementNumber) {
        var key = DATASTORE.allocateId(DATASTORE.newKeyFactory().setKind("Agreement").newKey());
        var today = LocalDate.now(ZoneOffset.UTC);

        DATASTORE.put(Entity.newBuilder(key)
            .set("agreementNumber", agreementNumber)
            .set("customerId", customerId)
            .set("type", "Personal Contract Purchase")
            .set("balancePence", 1_200_000L)
            .set("apr", "6.9")
            .set("originalTermMonths", 48L)
            .set("contractMileage", 10_000L)
            .set("excessPricePerMilePence", 10L)
            .set("vehicleModel", "2024 Test Vehicle")
            .set("registration", "AB24 TST")
            .set("lastPaymentPence", 45_000L)
            .set("lastPaymentDate", at(today.minusMonths(1)))
            .set("nextPaymentPence", 45_000L)
            .set("nextPaymentDate", at(today.withDayOfMonth(14).plusMonths(1)))
            .set("paymentsRemaining", 24L)
            .set("finalPaymentDate", at(today.plusMonths(24)))
            .build());

        return key.getId();
    }

    static void document(String customerId, String title, String fileName) {
        DATASTORE.put(Entity.newBuilder(DATASTORE.allocateId(DATASTORE.newKeyFactory().setKind("Document").newKey()))
            .set("customerId", customerId)
            .set("title", title)
            .set("fileName", fileName)
            .set("createdAt", Timestamp.now())
            .build());
    }

    static void bankDetails(String customerId, String accountNumber, String sortCode) {
        var encryptor = new BankDetailsEncryptor(ENCRYPTION_KEY, ENCRYPTION_SALT);

        DATASTORE.put(Entity.newBuilder(DATASTORE.allocateId(DATASTORE.newKeyFactory().setKind("BankDetails").newKey()))
            .set("customerId", customerId)
            .set("accountHolderName", "Test Customer")
            .set("encryptedAccountNumber", encryptor.encrypt(accountNumber))
            .set("encryptedSortCode", encryptor.encrypt(sortCode))
            .set("lastFourAccount", accountNumber.substring(accountNumber.length() - 4))
            .set("lastTwoSortCode", sortCode.substring(sortCode.length() - 2))
            .set("updatedAt", Timestamp.now())
            .build());
    }

    static Timestamp at(LocalDate date) {
        return Timestamp.ofTimeSecondsAndNanos(date.atStartOfDay(ZoneOffset.UTC).toEpochSecond(), 0);
    }

    static Optional<Entity> entity(String kind, String property, String value) {
        var found = DATASTORE.run(Query.newEntityQueryBuilder()
            .setKind(kind)
            .setFilter(PropertyFilter.eq(property, value))
            .setLimit(1)
            .build());
        return found.hasNext() ? Optional.of(found.next()) : Optional.empty();
    }

    static List<Entity> entities(String kind, String property, String value) {
        var found = DATASTORE.run(Query.newEntityQueryBuilder()
            .setKind(kind)
            .setFilter(PropertyFilter.eq(property, value))
            .build());
        var all = new ArrayList<Entity>();
        found.forEachRemaining(all::add);
        return all;
    }

    static List<Entity> auditEvents(String customerId) {
        return entities("AuditEvent", "customerId", customerId);
    }

    static String customerEmail(String customerId) {
        return entity("Customer", "customerId", customerId).orElseThrow().getString("email");
    }

    static void eventually(BooleanSupplier condition) {
        for (var attempt = 0; attempt < 100 && !condition.getAsBoolean(); attempt++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(condition.getAsBoolean()).as("condition did not hold within five seconds").isTrue();
    }
}
