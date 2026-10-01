package guru.junaid.azadi.seed;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.BaseEntity;
import com.google.cloud.datastore.Entity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guru.junaid.azadi.bank.BankDetailsEncryptor;
import guru.junaid.azadi.common.Db;
import io.helidon.config.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.logging.Logger;

public final class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class.getName());
    private static final String SEED_FILE = "/seed/customers.json";
    private static final long DAY_SECONDS = 24L * 3600;

    private DataSeeder() {
    }

    public static void main(String[] args) throws IOException {
        var config = Config.create();
        var db = new Db(config);

        if (!db.isEmpty("Customer")) {
            LOG.info("seed data already exists, skipping");
            return;
        }

        var encryptor = new BankDetailsEncryptor(config.get("azadi.encryption.key").asString().orElse(""),
            config.get("azadi.encryption.salt").asString().orElse(""));
        var accounts = accounts();

        accounts.forEach(account -> seedAccount(db, encryptor, account.getAsJsonObject()));
        LOG.info(() -> "seeded " + accounts.size() + " customers");
    }

    private static JsonArray accounts() throws IOException {
        var seed = DataSeeder.class.getResource(SEED_FILE);
        if (seed == null) {
            throw new IllegalStateException("No " + SEED_FILE + " on the classpath");
        }
        try (var in = seed.openStream()) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }

    private static void seedAccount(Db db, BankDetailsEncryptor encryptor, JsonObject account) {
        var customer = account.getAsJsonObject("customer");
        var customerId = text(customer, "customerId");

        db.add("Customer", b -> {
            b.set("customerId", customerId).set("fullName", text(customer, "fullName")).set("email", text(customer, "email"))
                .set("dob", Db.at(LocalDate.parse(text(customer, "dob")))).set("postcode", text(customer, "postcode"));
            optional(b, customer, "phone");
            optional(b, customer, "mobilePhone");
            optional(b, customer, "addressLine1");
            optional(b, customer, "addressLine2");
            optional(b, customer, "city");
        });

        var agreement = account.getAsJsonObject("agreement");
        var agreementId = seedAgreement(db, customerId, agreement).getKey().getId();

        seedPayments(db, customerId, agreementId);
        seedDocuments(db, customerId, account);
        seedSettlement(db, customerId, agreementId, agreement.get("balancePence").getAsLong());
        seedBankDetails(db, encryptor, customerId, text(customer, "fullName"), account.getAsJsonObject("bankDetails"));
    }

    private static Entity seedAgreement(Db db, String customerId, JsonObject agreement) {
        var today = LocalDate.now();
        var paymentsRemaining = agreement.get("paymentsRemaining").getAsInt();

        return db.add("Agreement", b -> b.set("agreementNumber", text(agreement, "agreementNumber")).set("customerId", customerId)
            .set("type", text(agreement, "type")).set("balancePence", agreement.get("balancePence").getAsLong())
            .set("apr", text(agreement, "apr")).set("originalTermMonths", agreement.get("originalTermMonths").getAsLong())
            .set("contractMileage", agreement.get("contractMileage").getAsLong())
            .set("excessPricePerMilePence", agreement.get("excessPricePerMilePence").getAsLong())
            .set("vehicleModel", text(agreement, "vehicleModel")).set("registration", text(agreement, "registration"))
            .set("lastPaymentPence", agreement.get("lastPaymentPence").getAsLong()).set("lastPaymentDate", Db.at(today.minusMonths(1)))
            .set("nextPaymentPence", agreement.get("nextPaymentPence").getAsLong()).set("nextPaymentDate", Db.at(today.plusDays(14)))
            .set("paymentsRemaining", paymentsRemaining).set("finalPaymentDate", Db.at(today.plusMonths(paymentsRemaining))));
    }

    private static void seedPayments(Db db, String customerId, long agreementId) {
        var nowSeconds = Timestamp.now().getSeconds();

        for (int month = 6; month >= 1; month--) {
            var createdAt = nowSeconds - month * 30 * DAY_SECONDS;
            var index = month;

            db.add("PaymentRecord", b -> b.set("agreementId", agreementId).set("customerId", customerId)
                .set("amountPence", 50_000L + index * 1_000L).set("stripePaymentIntentId", "pi_seed_" + customerId + "_" + index)
                .set("status", "COMPLETED").set("createdAt", Timestamp.ofTimeSecondsAndNanos(createdAt, 0))
                .set("completedAt", Timestamp.ofTimeSecondsAndNanos(createdAt + 60, 0)));
        }
    }

    private static void seedDocuments(Db db, String customerId, JsonObject account) {
        var createdAt = Timestamp.ofTimeSecondsAndNanos(Timestamp.now().getSeconds() - 180 * DAY_SECONDS, 0);

        account.getAsJsonArray("documents").forEach(element -> {
            var document = element.getAsJsonObject();

            db.add("Document", b -> b.set("customerId", customerId).set("title", text(document, "title"))
                .set("fileName", text(document, "fileName")).set("createdAt", createdAt));
        });
    }

    private static void seedSettlement(Db db, String customerId, long agreementId, long balancePence) {
        db.add("SettlementFigure", b -> b.set("agreementId", agreementId).set("customerId", customerId)
            .set("amountPence", balancePence + balancePence / 50).set("calculatedAt", Timestamp.now())
            .set("validUntil", Db.at(LocalDate.now().plusDays(28))));
    }

    private static void seedBankDetails(Db db, BankDetailsEncryptor encryptor, String customerId, String holder, JsonObject bank) {
        var account = text(bank, "accountNumber");
        var sortCode = text(bank, "sortCode");

        db.add("BankDetails", b -> b.set("customerId", customerId).set("accountHolderName", holder)
            .set("encryptedAccountNumber", encryptor.encrypt(account)).set("encryptedSortCode", encryptor.encrypt(sortCode))
            .set("lastFourAccount", account.substring(4)).set("lastTwoSortCode", sortCode.substring(sortCode.length() - 2))
            .set("updatedAt", Timestamp.now()));
    }

    private static void optional(BaseEntity.Builder<?, ?> builder, JsonObject json, String property) {
        JsonElement value = json.get(property);

        if (value != null && !value.isJsonNull()) {
            builder.set(property, value.getAsString());
        }
    }

    private static String text(JsonObject json, String property) {
        return json.get(property).getAsString();
    }
}
