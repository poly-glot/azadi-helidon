package guru.junaid.azadi.agreement;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.dto.AgreementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementResponseTest {

    @Test
    @DisplayName("Money and APR are formatted for the page")
    void formatsMoneyAndApr() {
        var response = AgreementResponse.from(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));

        assertThat(response.balance()).isEqualTo("£12,000.00");
        assertThat(response.apr()).isEqualTo("6.9%");
        assertThat(response.excessPricePerMile()).isEqualTo("£0.10");
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAgreementNumber()).isEqualTo("AGR-001");
    }

    @Test
    @DisplayName("A missing APR reads N/A")
    void showsMissingAprAsNotAvailable() {
        var agreement = new Agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID, "HP", 0, null, 0, 0, 0, "", "", 0, null, 0, null, 0, null, false);

        assertThat(AgreementResponse.from(agreement).apr()).isEqualTo("N/A");
    }

    @Test
    @DisplayName("Changing the payment day moves the next payment and marks the agreement")
    void movesTheNextPaymentDay() {
        var changed = Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID).withPaymentDay(21);

        assertThat(changed.nextPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 21));
        assertThat(changed.paymentDateChanged()).isTrue();
    }
}
