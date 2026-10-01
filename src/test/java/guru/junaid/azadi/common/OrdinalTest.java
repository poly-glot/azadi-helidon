package guru.junaid.azadi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrdinalTest {

    @ParameterizedTest
    @CsvSource({
        "1, 1st", "2, 2nd", "3, 3rd", "4, 4th", "5, 5th", "10, 10th",
        "21, 21st", "22, 22nd", "23, 23rd", "24, 24th", "28, 28th", "31, 31st",
    })
    @DisplayName("A day takes the suffix of its last digit")
    void suffixFollowsLastDigit(int day, String expected) {
        assertThat(Ordinal.INSTANCE.dayWithSuffix(day)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"11, 11th", "12, 12th", "13, 13th"})
    @DisplayName("Eleven, twelve and thirteen are the exceptions")
    void teensTakeTh(int day, String expected) {
        assertThat(Ordinal.INSTANCE.dayWithSuffix(day)).isEqualTo(expected);
    }
}
