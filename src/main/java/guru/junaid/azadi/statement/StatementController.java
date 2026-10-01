package guru.junaid.azadi.statement;

import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.agreement.dto.AgreementResponse;
import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.contact.ContactService;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.HashMap;
import java.util.Optional;

public final class StatementController {

    private static final String PATH = "/finance/request-a-statement";

    private final Web web;
    private final StatementService statements;
    private final AgreementService agreements;
    private final ContactService contact;

    public StatementController(Web web, StatementService statements, AgreementService agreements, ContactService contact) {
        this.web = web;
        this.statements = statements;
        this.agreements = agreements;
        this.contact = contact;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::form)
            .post(PATH, this::request);
    }

    private void form(ServerRequest req, ServerResponse res) {
        var customerId = Web.customerId(req);
        var customer = contact.customer(customerId);
        var model = new HashMap<String, Object>();
        model.put("agreements", agreements.agreementsFor(customerId).stream().map(AgreementResponse::from).toList());
        model.put("email", customer.email());
        model.put("address", customer.addressLine1());
        web.page(req, res, "finance/request-a-statement", model);
    }

    private void request(ServerRequest req, ServerResponse res) {
        var agreementId = Optional.ofNullable(Web.form(req).get("agreementId")).filter(raw -> !raw.isBlank()).map(Long::parseLong);
        statements.request(Web.customerId(req), agreementId, Web.ip(req), Web.sessionId(req));
        web.flashRedirect(req, res, PATH, "success", "Statement requested successfully. You will receive it by email.");
    }
}
