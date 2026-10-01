package guru.junaid.azadi.auth;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK = Duration.ofMinutes(30);

    private record Attempts(int count, long lockUntil) {

        Attempts failed(long now) {
            var next = count + 1;
            return new Attempts(next, next >= MAX_ATTEMPTS ? now + LOCK.toMillis() : lockUntil);
        }
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String agreementNumber) {
        var current = attempts.get(agreementNumber);
        return current != null && System.currentTimeMillis() < current.lockUntil();
    }

    public void recordFailure(String agreementNumber) {
        var now = System.currentTimeMillis();
        attempts.compute(agreementNumber, (key, current) -> (current == null ? new Attempts(0, 0) : current).failed(now));
    }

    public void recordSuccess(String agreementNumber) {
        attempts.remove(agreementNumber);
    }
}
