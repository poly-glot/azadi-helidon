package guru.junaid.azadi.bank;

import guru.junaid.azadi.bank.dto.UpdateBankDetailsRequest;
import guru.junaid.azadi.common.Web;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.HashMap;
import java.util.Map;

public final class BankDetailsController {

    private static final String PATH = "/finance/update-bank-details";
    private static final String TEMPLATE = "finance/update-bank-details";

    private final Web web;
    private final BankDetailsService bankDetails;

    public BankDetailsController(Web web, BankDetailsService bankDetails) {
        this.web = web;
        this.bankDetails = bankDetails;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::form)
            .post(PATH, this::update);
    }

    private void form(ServerRequest req, ServerResponse res) {
        web.page(req, res, TEMPLATE, model(req, Map.of()));
    }

    private void update(ServerRequest req, ServerResponse res) {
        var request = UpdateBankDetailsRequest.from(Web.form(req));
        var errors = request.validate();
        if (!errors.isEmpty()) {
            web.page(req, res, TEMPLATE, model(req, Map.of("error", String.join(". ", errors) + ".")));
            return;
        }
        bankDetails.update(Web.customerId(req), request, Web.ip(req), Web.sessionId(req));
        web.flashRedirect(req, res, PATH, "success", "Bank details updated successfully.");
    }

    private Map<String, Object> model(ServerRequest req, Map<String, Object> extra) {
        var model = new HashMap<>(extra);
        bankDetails.bankDetails(Web.customerId(req)).ifPresent(details -> model.put("bankDetails", details));
        return model;
    }
}
