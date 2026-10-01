package guru.junaid.azadi.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RateLimiter {

    private static final int MAX_BUCKETS = 10_000;

    private record Bucket(AtomicLong hits, long expiresAt) { }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean limited(String key, int max, Duration window) {
        var now = System.currentTimeMillis();
        if (buckets.size() > MAX_BUCKETS) {
            buckets.values().removeIf(bucket -> bucket.expiresAt() < now);
        }
        var bucket = buckets.compute(key, (k, current) ->
            current == null || current.expiresAt() < now ? new Bucket(new AtomicLong(), now + window.toMillis()) : current);
        return bucket.hits().incrementAndGet() > max;
    }
}
