package guru.junaid.azadi.web;

import guru.junaid.azadi.document.Document;
import guru.junaid.azadi.document.DocumentController;
import guru.junaid.azadi.document.DocumentService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RoutingTest
class DocumentControllerTest {

    private static final DocumentService DOCUMENTS = mock(DocumentService.class);

    private final DirectClient client;

    DocumentControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new DocumentController(ControllerTests.WEB, DOCUMENTS).routing(rules);
    }

    @Test
    @DisplayName("GET /my-documents lists each document with its file name")
    void listsDocuments() {
        when(DOCUMENTS.documentsFor(ControllerTests.CUSTOMER_ID))
            .thenReturn(List.of(new Document(ControllerTests.CUSTOMER_ID, "Finance Agreement", "finance-agreement.pdf"), new Document(ControllerTests.CUSTOMER_ID, "Welcome Letter", "welcome.docx")));

        try (var response = client.get("/my-documents").followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("finance-agreement.pdf").contains("welcome.docx");
        }
    }
}
