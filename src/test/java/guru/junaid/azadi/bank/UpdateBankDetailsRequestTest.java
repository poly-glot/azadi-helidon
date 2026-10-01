package guru.junaid.azadi.bank;

import guru.junaid.azadi.bank.dto.UpdateBankDetailsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateBankDetailsRequestTest {

    private static final String HOLDER = "Test Customer";
    private static final String ACCOUNT = "12345678";
    private static final String SORT_CODE = "11-22-33";

    @Test
    @DisplayName("Well formed bank details produce no errors")
    void acceptsWellFormedDetails() {
        assertThat(request(HOLDER, ACCOUNT, SORT_CODE).validate()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A missing account holder name is reported as required")
    void requiresAccountHolderName(String holder) {
        assertThat(request(holder, ACCOUNT, SORT_CODE).validate()).containsExactly("Account holder name is required");
    }

    @Test
    @DisplayName("An account holder name over 70 characters is rejected, exactly 70 is accepted")
    void limitsAccountHolderNameLength() {
        assertThat(request("x".repeat(71), ACCOUNT, SORT_CODE).validate())
            .containsExactly("Account holder name must not exceed 70 characters");
        assertThat(request("x".repeat(70), ACCOUNT, SORT_CODE).validate()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456789", "1234567a", "12 345678"})
    @DisplayName("An account number that is not eight digits is rejected")
    void rejectsMalformedAccountNumber(String account) {
        assertThat(request(HOLDER, account, SORT_CODE).validate()).containsExactly("Account number must be exactly 8 digits");
    }

    @ParameterizedTest
    @ValueSource(strings = {"112233", "11-22-3", "11-22-333", "aa-bb-cc", "11/22/33"})
    @DisplayName("A sort code outside XX-XX-XX is rejected")
    void rejectsMalformedSortCode(String sortCode) {
        assertThat(request(HOLDER, ACCOUNT, sortCode).validate()).containsExactly("Sort code must be in XX-XX-XX format");
    }

    @Test
    @DisplayName("Every blank field is reported, in field order")
    void reportsEveryMissingField() {
        assertThat(UpdateBankDetailsRequest.from(Map.of()).validate()).containsExactly(
            "Account holder name is required",
            "Account number is required",
            "Sort code is required");
    }

    @Test
    @DisplayName("The masked endings come from the submitted values")
    void exposesMaskedEndings() {
        var request = request(HOLDER, ACCOUNT, SORT_CODE);

        assertThat(request.lastFourAccount()).isEqualTo("5678");
        assertThat(request.lastTwoSortCode()).isEqualTo("33");
    }

    private static UpdateBankDetailsRequest request(String holder, String account, String sortCode) {
        return new UpdateBankDetailsRequest(holder, account, sortCode);
    }
}
