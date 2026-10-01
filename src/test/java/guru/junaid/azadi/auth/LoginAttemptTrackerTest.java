package guru.junaid.azadi.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptTrackerTest {

    private static final String AGREEMENT = "AGR-001";

    private final LoginAttemptTracker tracker = new LoginAttemptTracker();

    @Test
    @DisplayName("Four failures do not block, the fifth does")
    void blocksOnTheFifthFailure() {
        IntStream.range(0, 4).forEach(i -> tracker.recordFailure(AGREEMENT));
        assertThat(tracker.isBlocked(AGREEMENT)).isFalse();

        tracker.recordFailure(AGREEMENT);

        assertThat(tracker.isBlocked(AGREEMENT)).isTrue();
    }

    @Test
    @DisplayName("A success clears the failures")
    void successClearsFailures() {
        IntStream.range(0, 4).forEach(i -> tracker.recordFailure(AGREEMENT));

        tracker.recordSuccess(AGREEMENT);
        tracker.recordFailure(AGREEMENT);

        assertThat(tracker.isBlocked(AGREEMENT)).isFalse();
    }

    @Test
    @DisplayName("Agreements are tracked independently")
    void tracksPerAgreement() {
        IntStream.range(0, 5).forEach(i -> tracker.recordFailure(AGREEMENT));

        assertThat(tracker.isBlocked("AGR-002")).isFalse();
    }
}
