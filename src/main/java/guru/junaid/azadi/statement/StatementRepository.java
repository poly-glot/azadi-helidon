package guru.junaid.azadi.statement;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

public final class StatementRepository {

    private static final Records<StatementRequest> ROWS = Records.of(StatementRequest.class, "StatementRequest");

    private final Db db;

    public StatementRepository(Db db) {
        this.db = db;
    }

    public StatementRequest save(StatementRequest request) {
        return db.save(ROWS, request);
    }
}
