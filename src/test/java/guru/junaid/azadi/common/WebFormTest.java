package guru.junaid.azadi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class WebFormTest {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("No body means no fields")
    void emptyBodyYieldsNoFields(String body) {
        assertThat(Web.parseForm(body)).isEmpty();
    }

    @Test
    @DisplayName("Fields are split on & and =")
    void parsesFields() {
        assertThat(Web.parseForm("username=AGR-1&password=secret"))
            .containsOnly(entry("username", "AGR-1"), entry("password", "secret"));
    }

    @Test
    @DisplayName("Percent and plus encoding are decoded in names and values")
    void decodesEncodedFields() {
        assertThat(Web.parseForm("password=1%2F1%2F1990%7CSW1A+1AA&a%20b=c"))
            .containsOnly(entry("password", "1/1/1990|SW1A 1AA"), entry("a b", "c"));
    }

    @Test
    @DisplayName("A field with no = is present and empty")
    void fieldWithoutValueIsEmpty() {
        assertThat(Web.parseForm("_csrf")).containsOnly(entry("_csrf", ""));
    }

    @Test
    @DisplayName("A field with an empty value is kept")
    void keepsEmptyValue() {
        assertThat(Web.parseForm("newPaymentDate=")).containsOnly(entry("newPaymentDate", ""));
    }

    @Test
    @DisplayName("A repeated field takes the last value")
    void repeatedFieldTakesLastValue() {
        assertThat(Web.parseForm("agreementId=1&agreementId=2")).containsOnly(entry("agreementId", "2"));
    }
}
