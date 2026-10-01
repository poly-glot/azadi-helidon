package guru.junaid.azadi.document;

import guru.junaid.azadi.common.Web;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.Map;

public final class DocumentController {

    private final Web web;
    private final DocumentService documents;

    public DocumentController(Web web, DocumentService documents) {
        this.web = web;
        this.documents = documents;
    }

    public void routing(HttpRules rules) {
        rules.get("/my-documents", this::myDocuments);
    }

    private void myDocuments(ServerRequest req, ServerResponse res) {
        web.page(req, res, "my-documents", Map.of("documents", documents.documentsFor(Web.customerId(req))));
    }
}
