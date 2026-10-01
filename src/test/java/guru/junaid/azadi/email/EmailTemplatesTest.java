package guru.junaid.azadi.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    @Test
    @DisplayName("The payment confirmation states the amount paid")
    void paymentConfirmationCarriesTheAmount() {
        assertThat(EmailTemplates.paymentConfirmation(123456))
            .contains("Payment Confirmed")
            .contains("<strong>£1,234.56</strong>")
            .contains("2-3 business days");
    }

    @Test
    @DisplayName("The bank details notice explains what changed")
    void bankDetailsNoticeExplainsTheChange() {
        assertThat(EmailTemplates.bankDetailsUpdated())
            .contains("Bank Details Updated")
            .contains("Future payments will be taken from your new bank account.");
    }

    @Test
    @DisplayName("Both emails are complete HTML documents branded Azadi Finance")
    void rendersACompleteDocument() {
        assertThat(EmailTemplates.bankDetailsUpdated())
            .startsWith("<!DOCTYPE html>")
            .contains("<title>Bank Details Updated</title>")
            .contains("Azadi Finance")
            .endsWith("</html>\n");
    }
}
