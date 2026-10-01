package guru.junaid.azadi.audit;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

public final class AuditService {

    private static final Gson GSON = new Gson();

    private final AuditRepository events;

    public AuditService(AuditRepository events) {
        this.events = events;
    }

    public void log(String customerId, String eventType, String ip, String sessionId, Map<String, String> details) {
        events.save(new AuditEvent(customerId, eventType, ip, sha256(sessionId), GSON.toJson(details), Instant.now()));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
