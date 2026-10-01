package guru.junaid.azadi.auth;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Session {

    private final String id;
    private final String customerId;
    private final String name;
    private final AtomicLong lastAccess = new AtomicLong(System.nanoTime());
    private final ConcurrentHashMap<String, Object> flash = new ConcurrentHashMap<>();

    public Session(String id, String customerId, String name) {
        this.id = id;
        this.customerId = customerId;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String name() {
        return name;
    }

    public void flash(String key, Object value) {
        flash.put(key, value);
    }

    public Map<String, Object> takeFlash() {
        var messages = Map.copyOf(flash);
        flash.clear();
        return messages;
    }

    boolean idleFor(Duration idle) {
        return System.nanoTime() - lastAccess.get() > idle.toNanos();
    }

    void touch() {
        lastAccess.set(System.nanoTime());
    }
}
