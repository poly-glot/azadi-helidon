package guru.junaid.azadi.auth;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.Optional;

public final class CustomerRepository {

    private static final Records<Customer> ROWS = Records.of(Customer.class, "Customer");

    private final Db db;

    public CustomerRepository(Db db) {
        this.db = db;
    }

    public Optional<Customer> findByCustomerId(String customerId) {
        return db.query(ROWS).where("customerId", customerId).first();
    }

    public Customer save(Customer customer) {
        return db.save(ROWS, customer);
    }
}
