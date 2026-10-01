package guru.junaid.azadi.payment;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.Optional;

public final class PaymentRepository {

    private static final Records<PaymentRecord> ROWS = Records.of(PaymentRecord.class, "PaymentRecord");

    private final Db db;

    public PaymentRepository(Db db) {
        this.db = db;
    }

    public Optional<PaymentRecord> findByStripePaymentIntentId(String intentId) {
        return db.query(ROWS).where("stripePaymentIntentId", intentId).first();
    }

    public Optional<PaymentRecord> findByWebhookEventId(String eventId) {
        return db.query(ROWS).where("webhookEventId", eventId).first();
    }

    public PaymentRecord save(PaymentRecord payment) {
        return db.save(ROWS, payment);
    }
}
