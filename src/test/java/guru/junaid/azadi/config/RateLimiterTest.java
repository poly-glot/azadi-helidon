package guru.junaid.azadi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    @DisplayName("The first max hits pass and the next is limited")
    void limitsAfterMaxHits() {
        var window = Duration.ofMinutes(1);

        var passed = IntStream.range(0, 3).mapToObj(i -> limiter.limited("k", 3, window)).toList();

        assertThat(passed).containsOnly(false);
        assertThat(limiter.limited("k", 3, window)).isTrue();
    }

    @Test
    @DisplayName("Keys are counted independently")
    void countsPerKey() {
        var window = Duration.ofMinutes(1);
        limiter.limited("a", 1, window);

        assertThat(limiter.limited("a", 1, window)).isTrue();
        assertThat(limiter.limited("b", 1, window)).isFalse();
    }

    @Test
    @DisplayName("An expired window starts a fresh count")
    void resetsAfterTheWindow() throws InterruptedException {
        limiter.limited("k", 1, Duration.ofMillis(20));
        Thread.sleep(40);

        assertThat(limiter.limited("k", 1, Duration.ofMillis(20))).isFalse();
    }
}
