package guru.junaid.azadi.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Sessions {

    private static final Duration IDLE = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final ConcurrentHashMap<String, Session> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idByCustomer = new ConcurrentHashMap<>();

    public Session create(String customerId, String name) {
        var previous = idByCustomer.remove(customerId);
        if (previous != null) {
            byId.remove(previous);
        }
        var session = new Session(token(), customerId, name);
        byId.put(session.id(), session);
        idByCustomer.put(customerId, session.id());
        return session;
    }

    public Optional<Session> get(String id) {
        var session = id == null ? null : byId.get(id);
        if (session == null) {
            return Optional.empty();
        }
        if (session.idleFor(IDLE)) {
            invalidate(id);
            return Optional.empty();
        }
        session.touch();
        return Optional.of(session);
    }

    public void invalidate(String id) {
        var session = id == null ? null : byId.remove(id);
        if (session != null) {
            idByCustomer.remove(session.customerId(), id);
        }
    }

    public static String token() {
        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
