package guru.junaid.azadi.web;

import guru.junaid.azadi.bank.BankDetailsController;
import guru.junaid.azadi.bank.BankDetailsService;
import guru.junaid.azadi.bank.dto.BankDetailsResponse;
import guru.junaid.azadi.bank.dto.UpdateBankDetailsRequest;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RoutingTest
class BankDetailsControllerTest {

    private static final BankDetailsService BANK = mock(BankDetailsService.class);
    private static final String PATH = "/finance/update-bank-details";

    private final DirectClient client;

    BankDetailsControllerTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        ControllerTests.signedIn(rules);
        new BankDetailsController(ControllerTests.WEB, BANK).routing(rules);
    }

    @BeforeEach
    void resetMocks() {
        reset(BANK);
    }

    @Test
    @DisplayName("GET shows the masked details on file")
    void formShowsMaskedDetails() {
        when(BANK.bankDetails(ControllerTests.CUSTOMER_ID)).thenReturn(Optional.of(new BankDetailsResponse("Test Customer", "7788", "66")));

        try (var response = client.get(PATH).followRedirects(false).request()) {
            assertThat(ControllerTests.page(response)).contains("****7788").contains("**-**-66");
        }
    }

    @Test
    @DisplayName("A valid POST updates through the service and redirects with a confirmation")
    void validPostUpdates() {
        try (var response = client.post(PATH).followRedirects(false).submit("accountHolderName=Test+Customer&accountNumber=12345678&sortCode=11-22-33")) {
            assertThat(ControllerTests.redirect(response)).isEqualTo(PATH);
        }

        verify(BANK).update(ControllerTests.CUSTOMER_ID, new UpdateBankDetailsRequest("Test Customer", "12345678", "11-22-33"),
            ControllerTests.IP, ControllerTests.SESSION_ID);
    }

    @Test
    @DisplayName("An invalid POST re-renders the form with every error and changes nothing")
    void invalidPostRendersErrors() {
        try (var response = client.post(PATH).followRedirects(false).submit("accountHolderName=&accountNumber=123&sortCode=112233")) {
            assertThat(ControllerTests.page(response))
                .contains("Account holder name is required")
                .contains("Account number must be exactly 8 digits")
                .contains("Sort code must be in XX-XX-XX format");
        }

        verify(BANK, never()).update(any(), any(), any(), any());
    }
}
