package guru.junaid.azadi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
        "0, £0.00",
        "1, £0.01",
        "99, £0.99",
        "100, £1.00",
        "123456, '£1,234.56'",
        "1234567890, '£12,345,678.90'",
    })
    @DisplayName("Pence render as pounds with two decimals and thousands separators")
    void formatsPence(long pence, String expected) {
        assertThat(Money.pence(pence)).isEqualTo(expected);
    }

    @Test
    @DisplayName("A negative balance keeps its sign")
    void formatsNegativePence() {
        assertThat(Money.pence(-12345)).isEqualTo("£-123.45");
    }

    @Test
    @DisplayName("Formatting does not follow the default locale")
    void usesRootLocale() {
        assertThat(Money.pence(100000)).isEqualTo("£1,000.00");
    }
}
