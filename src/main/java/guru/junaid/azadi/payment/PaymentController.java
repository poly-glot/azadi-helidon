package guru.junaid.azadi.payment;

import com.google.gson.Gson;
import com.stripe.exception.StripeException;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.agreement.dto.AgreementResponse;
import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.payment.dto.MakePaymentRequest;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class PaymentController {

    private static final Gson GSON = new Gson();
    private static final HeaderName STRIPE_SIGNATURE = HeaderNames.create("Stripe-Signature");
    private static final String PATH = "/finance/make-a-payment";

    private final Web web;
    private final PaymentService payments;
    private final PaymentWebhookHandler webhooks;
    private final AgreementService agreements;
    private final String stripePublishableKey;

    public PaymentController(Web web, PaymentService payments, PaymentWebhookHandler webhooks, AgreementService agreements,
                             String stripePublishableKey) {
        this.web = web;
        this.payments = payments;
        this.webhooks = webhooks;
        this.agreements = agreements;
        this.stripePublishableKey = stripePublishableKey;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::form)
            .post(PATH, this::pay)
            .post("/api/stripe/webhook", this::webhook);
    }

    private void form(ServerRequest req, ServerResponse res) {
        var responses = agreements.agreementsFor(Web.customerId(req)).stream().map(AgreementResponse::from).toList();
        web.page(req, res, "finance/make-a-payment", Map.of("agreements", responses, "stripePublishableKey", stripePublishableKey));
    }

    private void pay(ServerRequest req, ServerResponse res) throws StripeException {
        var request = MakePaymentRequest.parse(Web.body(req));
        if (request.isEmpty()) {
            Web.json(res, Status.BAD_REQUEST_400, "{\"error\":\"Please enter a valid amount (minimum £1.00).\"}");
            return;
        }
        var session = Web.session(req).orElseThrow();
        var clientSecret = payments.initiate(session.customerId(), session.id(), request.get().agreementId(),
            request.get().amountPence(), Web.ip(req));
        Web.json(res, Status.OK_200, GSON.toJson(Map.of("clientSecret", clientSecret)));
    }

    private void webhook(ServerRequest req, ServerResponse res) {
        var signature = req.headers().value(STRIPE_SIGNATURE).orElse("");
        if (webhooks.handle(Web.body(req), signature, Web.ip(req))) {
            res.send("ok");
            return;
        }
        res.status(Status.BAD_REQUEST_400).send("Invalid signature");
    }
}
