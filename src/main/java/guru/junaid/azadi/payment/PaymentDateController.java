package guru.junaid.azadi.payment;

import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.agreement.dto.AgreementResponse;
import guru.junaid.azadi.common.Ordinal;
import guru.junaid.azadi.common.Web;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.HashMap;

public final class PaymentDateController {

    private static final String PATH = "/finance/change-payment-date";

    private final Web web;
    private final PaymentDateService paymentDates;
    private final AgreementService agreements;

    public PaymentDateController(Web web, PaymentDateService paymentDates, AgreementService agreements) {
        this.web = web;
        this.paymentDates = paymentDates;
        this.agreements = agreements;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::form)
            .post(PATH, this::change);
    }

    private void form(ServerRequest req, ServerResponse res) {
        var customerId = Web.customerId(req);
        var all = agreements.agreementsFor(customerId);
        var model = new HashMap<String, Object>();
        model.put("agreements", all.stream().map(AgreementResponse::from).toList());

        all.stream().map(Agreement::id).findFirst().ifPresent(first -> {
            var day = paymentDates.currentPaymentDay(customerId, first);
            model.put("currentPaymentDay", day);
            model.put("currentPaymentDate", Ordinal.INSTANCE.dayWithSuffix(day));
            model.put("alreadyChanged", paymentDates.alreadyChanged(customerId, first));
        });

        web.page(req, res, "finance/change-payment-date", model);
    }

    private void change(ServerRequest req, ServerResponse res) {
        var form = Web.form(req);
        try {
            paymentDates.change(Web.customerId(req), Long.parseLong(form.get("agreementId")), Integer.parseInt(form.get("newPaymentDate")),
                Web.ip(req), Web.sessionId(req));
            web.flashRedirect(req, res, PATH, "success", true);
        } catch (IllegalStateException e) {
            web.flashRedirect(req, res, PATH, "error", e.getMessage());
        }
    }

}
