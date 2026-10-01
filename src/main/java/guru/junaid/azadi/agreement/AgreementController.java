package guru.junaid.azadi.agreement;

import guru.junaid.azadi.agreement.dto.AgreementResponse;
import guru.junaid.azadi.common.Web;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class AgreementController {

    private final Web web;
    private final AgreementService agreements;

    public AgreementController(Web web, AgreementService agreements) {
        this.web = web;
        this.agreements = agreements;
    }

    public void routing(HttpRules rules) {
        rules.get("/my-account", this::myAccount)
            .get("/agreements/{id}", this::agreementDetail);
    }

    private void myAccount(ServerRequest req, ServerResponse res) {
        var responses = agreements.agreementsFor(Web.customerId(req)).stream().map(AgreementResponse::from).toList();
        web.page(req, res, "my-account", Map.of("agreements", responses));
    }

    private void agreementDetail(ServerRequest req, ServerResponse res) {
        var id = Long.parseLong(req.path().pathParameters().get("id"));
        web.page(req, res, "agreement-detail", Map.of("agreement", AgreementResponse.from(agreements.agreement(Web.customerId(req), id))));
    }
}
