package guru.junaid.azadi.audit;

import java.time.Instant;

public record AuditEvent(String customerId, String eventType, String ipAddress, String sessionIdHash, String details, Instant timestamp) {
}
