package guru.junaid.azadi.settlement;

import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.agreement.dto.AgreementResponse;
import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.settlement.dto.SettlementResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SettlementController {

    private static final String PATH = "/finance/settlement-figure";
    private static final String TEMPLATE = "finance/settlement-figure";

    private final Web web;
    private final SettlementService settlements;
    private final AgreementService agreements;

    public SettlementController(Web web, SettlementService settlements, AgreementService agreements) {
        this.web = web;
        this.settlements = settlements;
        this.agreements = agreements;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::form)
            .post(PATH, this::calculate);
    }

    private void form(ServerRequest req, ServerResponse res) {
        var customerId = Web.customerId(req);
        web.page(req, res, TEMPLATE, Map.of(
            "agreements", agreements.agreementsFor(customerId).stream().map(AgreementResponse::from).toList(),
            "settlements", settlements.settlementsFor(customerId).stream().map(SettlementResponse::from).toList()));
    }

    private void calculate(ServerRequest req, ServerResponse res) {
        var customerId = Web.customerId(req);
        var model = new HashMap<String, Object>();
        model.put("agreements", agreements.agreementsFor(customerId).stream().map(AgreementResponse::from).toList());

        agreementId(req).ifPresent(id -> model.put("settlement", SettlementResponse.from(settlements.calculate(customerId, id))));

        web.page(req, res, TEMPLATE, model);
    }

    private static Optional<Long> agreementId(ServerRequest req) {
        return Optional.ofNullable(Web.form(req).get("agreementId")).filter(raw -> !raw.isBlank()).map(Long::parseLong);
    }
}
