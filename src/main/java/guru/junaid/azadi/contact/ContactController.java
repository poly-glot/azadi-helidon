package guru.junaid.azadi.contact;

import guru.junaid.azadi.common.Web;
import guru.junaid.azadi.contact.dto.ContactDetailsResponse;
import guru.junaid.azadi.contact.dto.UpdateContactCommand;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class ContactController {

    private static final String PATH = "/my-contact-details";

    private final Web web;
    private final ContactService contact;

    public ContactController(Web web, ContactService contact) {
        this.web = web;
        this.contact = contact;
    }

    public void routing(HttpRules rules) {
        rules.get(PATH, this::details)
            .post(PATH, this::update);
    }

    private void details(ServerRequest req, ServerResponse res) {
        web.page(req, res, "my-contact-details", Map.of("customer", ContactDetailsResponse.from(contact.customer(Web.customerId(req)))));
    }

    private void update(ServerRequest req, ServerResponse res) {
        contact.update(Web.customerId(req), UpdateContactCommand.from(Web.form(req)), Web.ip(req), Web.sessionId(req));
        web.flashRedirect(req, res, PATH, "success", "Contact details updated successfully.");
    }
}
