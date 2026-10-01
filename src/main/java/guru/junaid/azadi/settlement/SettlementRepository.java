package guru.junaid.azadi.settlement;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.List;

public final class SettlementRepository {

    private static final Records<SettlementFigure> ROWS = Records.of(SettlementFigure.class, "SettlementFigure");

    private final Db db;

    public SettlementRepository(Db db) {
        this.db = db;
    }

    public List<SettlementFigure> findByCustomerId(String customerId) {
        return db.query(ROWS).where("customerId", customerId).list();
    }

    public SettlementFigure save(SettlementFigure figure) {
        return db.save(ROWS, figure);
    }
}
