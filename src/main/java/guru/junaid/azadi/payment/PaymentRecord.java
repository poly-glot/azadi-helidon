package guru.junaid.azadi.payment;

import java.time.Instant;

public record PaymentRecord(long id, long agreementId, String customerId, long amountPence, String stripePaymentIntentId, String status,
                            Instant createdAt, Instant completedAt, String webhookEventId) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static PaymentRecord pending(long agreementId, String customerId, long amountPence, String stripePaymentIntentId) {
        return new PaymentRecord(0, agreementId, customerId, amountPence, stripePaymentIntentId, STATUS_PENDING, Instant.now(), null, null);
    }

    public PaymentRecord settled(String newStatus, String eventId) {
        return new PaymentRecord(id, agreementId, customerId, amountPence, stripePaymentIntentId, newStatus, createdAt, Instant.now(), eventId);
    }
}
