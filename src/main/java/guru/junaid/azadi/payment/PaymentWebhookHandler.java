package guru.junaid.azadi.payment;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.email.EmailService;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class PaymentWebhookHandler {

    private static final Logger LOG = Logger.getLogger(PaymentWebhookHandler.class.getName());
    private static final Map<String, String> STATUS_BY_EVENT = Map.of(
        "payment_intent.succeeded", PaymentRecord.STATUS_COMPLETED,
        "payment_intent.payment_failed", PaymentRecord.STATUS_FAILED);

    private final PaymentRepository payments;
    private final AuditService audit;
    private final EmailService email;
    private final String webhookSecret;

    public PaymentWebhookHandler(PaymentRepository payments, AuditService audit, EmailService email, String webhookSecret) {
        this.payments = payments;
        this.audit = audit;
        this.email = email;
        this.webhookSecret = webhookSecret;
    }

    public boolean handle(String payload, String signature, String ip) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            LOG.warning("Invalid webhook signature");
            return false;
        }
        Optional.ofNullable(STATUS_BY_EVENT.get(event.getType()))
            .ifPresent(status -> paymentIntent(event).ifPresentOrElse(
                intent -> update(intent.getId(), event.getId(), status, ip),
                () -> LOG.warning(() -> "Webhook event " + event.getId() + " could not be deserialised (API version mismatch?)")));
        return true;
    }

    private static Optional<PaymentIntent> paymentIntent(Event event) {
        return event.getDataObjectDeserializer().getObject()
            .filter(PaymentIntent.class::isInstance)
            .map(PaymentIntent.class::cast);
    }

    private void update(String intentId, String eventId, String status, String ip) {
        if (payments.findByWebhookEventId(eventId).isPresent()) {
            LOG.info(() -> "Duplicate webhook event ignored: " + eventId);
            return;
        }
        payments.findByStripePaymentIntentId(intentId).ifPresentOrElse(
            record -> settle(record, eventId, status, ip),
            () -> LOG.warning(() -> "No PaymentRecord for " + intentId));
    }

    private void settle(PaymentRecord record, String eventId, String status, String ip) {
        var saved = payments.save(record.settled(status, eventId));

        audit.log(saved.customerId(), "PAYMENT_" + status, ip, "",
            Map.of("amount", String.valueOf(saved.amountPence()), "paymentIntentId", saved.stripePaymentIntentId()));

        if (PaymentRecord.STATUS_COMPLETED.equals(status)) {
            email.paymentConfirmation(saved.customerId(), saved.amountPence());
        }
    }
}
