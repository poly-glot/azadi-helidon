package guru.junaid.azadi.statement;

import java.time.Instant;

public record StatementRequest(long id, String customerId, long agreementId, String status, Instant requestedAt) {

    public static StatementRequest pending(String customerId, long agreementId) {
        return new StatementRequest(0, customerId, agreementId, "PENDING", Instant.now());
    }
}
