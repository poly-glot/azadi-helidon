package guru.junaid.azadi.payment;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookHandlerTest {

    private static final String SECRET = "whsec_test";
    private static final String INTENT = "pi_123";

    @Mock
    private PaymentRepository payments;

    @Mock
    private AuditService audit;

    @Mock
    private EmailService email;

    private PaymentWebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentWebhookHandler(payments, audit, email, SECRET);
    }

    @Test
    @DisplayName("A succeeded event completes the record, audits it and emails the customer")
    void completesOnSuccess() {
        pendingRecord();
        var payload = StripeEvents.event("evt_1", "payment_intent.succeeded", INTENT);

        assertThat(handler.handle(payload, StripeEvents.signature(payload, SECRET), "10.0.0.1")).isTrue();

        var saved = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(PaymentRecord.STATUS_COMPLETED);
        assertThat(saved.getValue().webhookEventId()).isEqualTo("evt_1");
        assertThat(saved.getValue().completedAt()).isNotNull();
        verify(audit).log(Fixtures.CUSTOMER_ID, "PAYMENT_COMPLETED", "10.0.0.1", "", Map.of("amount", "45000", "paymentIntentId", INTENT));
        verify(email).paymentConfirmation(Fixtures.CUSTOMER_ID, 45_000L);
    }

    @Test
    @DisplayName("A failed event marks the record failed and sends no email")
    void marksFailureWithoutEmailing() {
        pendingRecord();
        var payload = StripeEvents.event("evt_2", "payment_intent.payment_failed", INTENT);

        handler.handle(payload, StripeEvents.signature(payload, SECRET), "10.0.0.1");

        var saved = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(PaymentRecord.STATUS_FAILED);
        verify(email, never()).paymentConfirmation(any(), anyLong());
    }

    @Test
    @DisplayName("A bad signature is refused and nothing changes")
    void refusesABadSignature() {
        var payload = StripeEvents.event("evt_3", "payment_intent.succeeded", INTENT);

        assertThat(handler.handle(payload, StripeEvents.forgedSignature(), "10.0.0.1")).isFalse();
        verifyNoInteractions(payments, audit, email);
    }

    @Test
    @DisplayName("A duplicate event is accepted but applied once")
    void ignoresADuplicateEvent() {
        when(payments.findByWebhookEventId("evt_4")).thenReturn(Optional.of(Fixtures.pendingPayment(9L, Fixtures.CUSTOMER_ID, INTENT)));
        var payload = StripeEvents.event("evt_4", "payment_intent.succeeded", INTENT);

        assertThat(handler.handle(payload, StripeEvents.signature(payload, SECRET), "10.0.0.1")).isTrue();
        verify(payments, never()).save(any());
        verifyNoInteractions(audit, email);
    }

    @Test
    @DisplayName("An unknown intent or an uninteresting event type is accepted and ignored")
    void toleratesUnknownIntentsAndOtherEvents() {
        when(payments.findByWebhookEventId("evt_5")).thenReturn(Optional.empty());
        when(payments.findByStripePaymentIntentId("pi_never")).thenReturn(Optional.empty());
        var unknown = StripeEvents.event("evt_5", "payment_intent.succeeded", "pi_never");
        var other = StripeEvents.event("evt_6", "payment_intent.created", INTENT);

        assertThat(handler.handle(unknown, StripeEvents.signature(unknown, SECRET), "10.0.0.1")).isTrue();
        assertThat(handler.handle(other, StripeEvents.signature(other, SECRET), "10.0.0.1")).isTrue();
        verify(payments, never()).save(any());
        verifyNoInteractions(audit, email);
    }

    private void pendingRecord() {
        when(payments.findByWebhookEventId(any())).thenReturn(Optional.empty());
        when(payments.findByStripePaymentIntentId(INTENT)).thenReturn(Optional.of(Fixtures.pendingPayment(9L, Fixtures.CUSTOMER_ID, INTENT)));
        when(payments.save(any())).thenAnswer(call -> call.getArgument(0));
    }
}
