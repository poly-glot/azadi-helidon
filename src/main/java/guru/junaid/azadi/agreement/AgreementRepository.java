package guru.junaid.azadi.agreement;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.List;
import java.util.Optional;

public final class AgreementRepository {

    private static final Records<Agreement> ROWS = Records.of(Agreement.class, "Agreement");

    private final Db db;

    public AgreementRepository(Db db) {
        this.db = db;
    }

    public List<Agreement> findByCustomerId(String customerId) {
        return db.query(ROWS).where("customerId", customerId).list();
    }

    public Optional<Agreement> findById(long id) {
        return db.find(ROWS, id);
    }

    public Optional<Agreement> findByAgreementNumber(String agreementNumber) {
        return db.query(ROWS).where("agreementNumber", agreementNumber).first();
    }

    public Agreement save(Agreement agreement) {
        return db.save(ROWS, agreement);
    }
}
