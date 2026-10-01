package guru.junaid.azadi.bank;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.Optional;

public final class BankDetailsRepository {

    private static final Records<BankDetails> ROWS = Records.of(BankDetails.class, "BankDetails");

    private final Db db;

    public BankDetailsRepository(Db db) {
        this.db = db;
    }

    public Optional<BankDetails> findByCustomerId(String customerId) {
        return db.query(ROWS).where("customerId", customerId).first();
    }

    public Optional<BankDetails> findByLastFourAccount(String lastFour) {
        return db.query(ROWS).where("lastFourAccount", lastFour).first();
    }

    public BankDetails save(BankDetails details) {
        return db.save(ROWS, details);
    }
}
