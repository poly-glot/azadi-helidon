package guru.junaid.azadi.web;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.contact.ContactController;
import guru.junaid.azadi.contact.ContactService;
import guru.junaid.azadi.contact.dto.UpdateContactCommand;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RoutingTest
class ContactControllerTest {

    private static final ContactService CONTACT = mock(ContactService.class);
    private static final String PATH = "/my-contact-details";

    private final DirectClient client;

    ContactControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new ContactController(ControllerTests.WEB, CONTACT).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(CONTACT);
    }

    @Test
    @DisplayName("GET shows the details on file")
    void showsContactDetails() {
        when(CONTACT.customer(ControllerTests.CUSTOMER_ID)).thenReturn(Fixtures.customer(ControllerTests.CUSTOMER_ID));

        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("1 Test Street").contains("SW1A 1AA").contains("07000000000");
        }
    }

    @Test
    @DisplayName("POST passes the form to the service and redirects with a confirmation shown once")
    void updatesContactDetails() {
        when(CONTACT.customer(ControllerTests.CUSTOMER_ID)).thenReturn(Fixtures.customer(ControllerTests.CUSTOMER_ID));

        try (var response = client.post(PATH).followRedirects(false).submit("homePhone=0201&mobilePhone=0799&email=new%40example.com&houseName=2+New+Street&postcode=E1+6AN")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }
        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("Your contact details have been updated successfully.");
        }
        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).doesNotContain("Your contact details have been updated successfully.");
        }

        verify(CONTACT).update(ControllerTests.CUSTOMER_ID, new UpdateContactCommand("0201", "0799", "new@example.com", "2 New Street", "E1 6AN"),
            ControllerTests.IP, ControllerTests.SESSION_ID);
    }
}
