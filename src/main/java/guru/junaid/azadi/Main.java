package guru.junaid.azadi;

import com.stripe.StripeClient;
import guru.junaid.azadi.agreement.AgreementController;
import guru.junaid.azadi.agreement.AgreementRepository;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditRepository;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.auth.AuthController;
import guru.junaid.azadi.auth.AuthService;
import guru.junaid.azadi.auth.CustomerRepository;
import guru.junaid.azadi.auth.LoginAttemptTracker;
import guru.junaid.azadi.auth.Sessions;
import guru.junaid.azadi.bank.BankDetailsController;
import guru.junaid.azadi.bank.BankDetailsEncryptor;
import guru.junaid.azadi.bank.BankDetailsRepository;
import guru.junaid.azadi.bank.BankDetailsService;
import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.ErrorHandler;
import guru.junaid.azadi.common.Views;
import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.config.Cookies;
import guru.junaid.azadi.config.RateLimiter;
import guru.junaid.azadi.config.SecurityFilter;
import guru.junaid.azadi.contact.ContactController;
import guru.junaid.azadi.contact.ContactService;
import guru.junaid.azadi.document.DocumentController;
import guru.junaid.azadi.document.DocumentRepository;
import guru.junaid.azadi.document.DocumentService;
import guru.junaid.azadi.email.EmailService;
import guru.junaid.azadi.help.HelpController;
import guru.junaid.azadi.payment.PaymentController;
import guru.junaid.azadi.payment.PaymentDateController;
import guru.junaid.azadi.payment.PaymentDateService;
import guru.junaid.azadi.payment.PaymentRepository;
import guru.junaid.azadi.payment.PaymentService;
import guru.junaid.azadi.payment.PaymentWebhookHandler;
import guru.junaid.azadi.settlement.SettlementController;
import guru.junaid.azadi.settlement.SettlementRepository;
import guru.junaid.azadi.settlement.SettlementService;
import guru.junaid.azadi.statement.StatementController;
import guru.junaid.azadi.statement.StatementRepository;
import guru.junaid.azadi.statement.StatementService;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.staticcontent.ClasspathHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final String SEEDED_ACCOUNT_ENDING = "7788";
    private static final String ROUND_TRIP_SAMPLE = "12345678";

    private final SecurityFilter filter;
    private final ErrorHandler errors;
    private final List<Consumer<HttpRules>> controllers;
    private final BankDetailsEncryptor encryptor;
    private final BankDetailsRepository bankDetails;
    private final boolean encryptionConfigured;

    Main(Config config) {
        var db = new Db(config);
        var sessions = new Sessions();
        var cookies = new Cookies(config.get("cookie.secure").asBoolean().get());
        var customers = new CustomerRepository(db);
        var agreements = new AgreementRepository(db);
        var audit = new AuditService(new AuditRepository(db));
        var email = new EmailService(customers, config);
        var agreementService = new AgreementService(agreements);
        var contactService = new ContactService(customers, audit);
        var paymentRepository = new PaymentRepository(db);
        var web = new Web(new Views(config.get("template.cache").asBoolean().get()));

        this.encryptor = new BankDetailsEncryptor(config.get("azadi.encryption.key").asString().orElse(""),
            config.get("azadi.encryption.salt").asString().orElse(""));
        this.encryptionConfigured = config.get("azadi.encryption.key").asString().filter(key -> !key.isEmpty()).isPresent();
        this.bankDetails = new BankDetailsRepository(db);
        this.filter = new SecurityFilter(sessions, cookies, new RateLimiter(), config.get("rate.limit.general").asInt().get());
        this.errors = new ErrorHandler(web);
        this.controllers = List.of(
            new AuthController(web, new AuthService(agreements, customers, new LoginAttemptTracker(), sessions), sessions, cookies,
                config.get("demo.mode").asBoolean().get())::routing,
            new AgreementController(web, agreementService)::routing,
            new DocumentController(web, new DocumentService(new DocumentRepository(db)))::routing,
            new ContactController(web, contactService)::routing,
            new BankDetailsController(web, new BankDetailsService(bankDetails, encryptor, audit, email))::routing,
            new PaymentController(web, new PaymentService(stripe(config), paymentRepository, agreementService, customers, audit),
                new PaymentWebhookHandler(paymentRepository, audit, email, config.get("stripe.webhook.secret").asString().orElse("")),
                agreementService, config.get("vite.stripe.publishable.key").asString().orElse(""))::routing,
            new PaymentDateController(web, new PaymentDateService(agreementService, agreements, audit), agreementService)::routing,
            new SettlementController(web, new SettlementService(agreementService, new SettlementRepository(db)), agreementService)::routing,
            new StatementController(web, new StatementService(agreementService, new StatementRepository(db), audit), agreementService,
                contactService)::routing,
            new HelpController(web)::routing);
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        var server = start(Config.create());
        LOG.info(() -> "started on port " + server.port());
    }

    static WebServer start(Config config) {
        var app = new Main(config);
        app.selfCheck();
        var server = WebServer.builder()
            .config(config.get("server"))
            .addFeature(ObserveFeature.builder().endpoint("/actuator").addObserver(HealthObserver.builder().details(true).build()).build())
            .routing(app::routes);
        config.get("port").asInt().ifPresent(server::port);
        return server.build().start();
    }

    private void routes(HttpRouting.Builder rules) {
        rules.addFilter(filter);
        controllers.forEach(controller -> controller.accept(rules));
        rules.post("/api/csp-report", (req, res) -> res.status(Status.NO_CONTENT_204).send())
            .register("/assets", StaticContentFeature.createService(ClasspathHandlerConfig.builder().location("static/assets").build()))
            .error(Throwable.class, errors::handle);
    }

    private void selfCheck() {
        if (!encryptionConfigured) {
            return;
        }
        var round = encryptor.decrypt(encryptor.encrypt(ROUND_TRIP_SAMPLE));
        var stored = bankDetails.findByLastFourAccount(SEEDED_ACCOUNT_ENDING)
            .map(d -> encryptor.decrypt(d.encryptedAccountNumber()))
            .orElse("(no record)");
        LOG.info(() -> "crypto round-trip ok=" + ROUND_TRIP_SAMPLE.equals(round) + ", decrypted seeded account ends "
            + (stored.length() >= SEEDED_ACCOUNT_ENDING.length() ? stored.substring(stored.length() - SEEDED_ACCOUNT_ENDING.length()) : stored));
    }

    private static StripeClient stripe(Config config) {
        var builder = StripeClient.builder().setApiKey(config.get("stripe.api.key").asString().orElse(""));
        config.get("stripe.api.base").asString().filter(base -> !base.isEmpty()).ifPresent(builder::setApiBase);
        return builder.build();
    }
}
