package guru.junaid.azadi.statement;

import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;

import java.util.Map;
import java.util.Optional;

public final class StatementService {

    private final AgreementService agreements;
    private final StatementRepository statements;
    private final AuditService audit;

    public StatementService(AgreementService agreements, StatementRepository statements, AuditService audit) {
        this.agreements = agreements;
        this.statements = statements;
        this.audit = audit;
    }

    public Optional<StatementRequest> request(String customerId, Optional<Long> agreementId, String ip, String sessionId) {
        return agreementId.or(() -> agreements.agreementsFor(customerId).stream().map(Agreement::id).findFirst())
            .map(id -> request(customerId, id, ip, sessionId));
    }

    private StatementRequest request(String customerId, long agreementId, String ip, String sessionId) {
        var saved = statements.save(StatementRequest.pending(customerId, agreementId));
        audit.log(customerId, "STATEMENT_REQUESTED", ip, sessionId, Map.of("agreementId", String.valueOf(agreementId)));
        return saved;
    }
}
