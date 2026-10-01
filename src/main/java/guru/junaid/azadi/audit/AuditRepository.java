package guru.junaid.azadi.audit;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

public final class AuditRepository {

    private static final Records<AuditEvent> ROWS = Records.of(AuditEvent.class, "AuditEvent");

    private final Db db;

    public AuditRepository(Db db) {
        this.db = db;
    }

    public void save(AuditEvent event) {
        db.save(ROWS, event);
    }
}
