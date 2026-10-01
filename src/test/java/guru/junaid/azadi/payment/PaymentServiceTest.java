package guru.junaid.azadi.payment;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.auth.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripe;

    @Mock
    private PaymentRepository payments;

    @Mock
    private AgreementService agreements;

    @Mock
    private CustomerRepository customers;

    @Mock
    private AuditService audit;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(stripe, payments, agreements, customers, audit);
    }

    @Test
    @DisplayName("A payment creates a Stripe intent with the agreement metadata, records it pending and audits it")
    void initiatesAPayment() throws StripeException {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.customer(Fixtures.CUSTOMER_ID)));
        var intent = new PaymentIntent();
        intent.setId("pi_123");
        intent.setClientSecret("pi_123_secret");
        when(stripe.v1().paymentIntents().create(any(PaymentIntentCreateParams.class))).thenReturn(intent);

        var clientSecret = service.initiate(Fixtures.CUSTOMER_ID, "sid", 1L, 45_000L, "10.0.0.1");

        assertThat(clientSecret).isEqualTo("pi_123_secret");
        var params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        verify(stripe.v1().paymentIntents()).create(params.capture());
        assertThat(params.getValue().getAmount()).isEqualTo(45_000L);
        assertThat(params.getValue().getCurrency()).isEqualTo("gbp");
        assertThat(params.getValue().getMetadata()).containsEntry("agreementNumber", "AGR-001").containsEntry("customerEmail", "test@example.com");

        var record = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(payments).save(record.capture());
        assertThat(record.getValue().status()).isEqualTo(PaymentRecord.STATUS_PENDING);
        assertThat(record.getValue().stripePaymentIntentId()).isEqualTo("pi_123");
        assertThat(record.getValue().amountPence()).isEqualTo(45_000L);
        verify(audit).log(Fixtures.CUSTOMER_ID, "PAYMENT_INITIATED", "10.0.0.1", "sid", Map.of("amount", "45000", "agreementNumber", "AGR-001"));
    }

    @Test
    @DisplayName("Another customer's agreement is refused before Stripe is called")
    void refusesAnotherCustomersAgreement() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 2L)).thenThrow(new SecurityException("You do not have access to this agreement."));

        assertThatThrownBy(() -> service.initiate(Fixtures.CUSTOMER_ID, "sid", 2L, 45_000L, "10.0.0.1")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(payments, audit);
    }
}
