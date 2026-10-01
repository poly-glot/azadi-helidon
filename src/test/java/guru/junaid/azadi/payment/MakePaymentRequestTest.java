package guru.junaid.azadi.payment;

import guru.junaid.azadi.payment.dto.MakePaymentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MakePaymentRequestTest {

    @Test
    @DisplayName("A well formed request carries the agreement and the amount")
    void parsesAWellFormedRequest() {
        var request = MakePaymentRequest.parse("{\"agreementId\":7,\"amountPence\":45000}").orElseThrow();

        assertThat(request.agreementId()).isEqualTo(7L);
        assertThat(request.amountPence()).isEqualTo(45_000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"agreementId\":7,\"amountPence\":99}", "{\"amountPence\":45000}", "not json at all", "", "null"})
    @DisplayName("Under a pound, no agreement, broken or empty JSON are all refused")
    void refusesInvalidRequests(String json) {
        assertThat(MakePaymentRequest.parse(json)).isEmpty();
    }

    @Test
    @DisplayName("Exactly one pound is the minimum")
    void acceptsOnePound() {
        assertThat(MakePaymentRequest.parse("{\"agreementId\":7,\"amountPence\":100}")).isPresent();
    }
}
