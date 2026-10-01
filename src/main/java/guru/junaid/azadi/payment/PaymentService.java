package guru.junaid.azadi.payment;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.PaymentIntentCreateParams;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.auth.CustomerRepository;

import java.util.Map;
import java.util.Objects;

public final class PaymentService {

    private final StripeClient stripe;
    private final PaymentRepository payments;
    private final AgreementService agreements;
    private final CustomerRepository customers;
    private final AuditService audit;

    public PaymentService(StripeClient stripe, PaymentRepository payments, AgreementService agreements, CustomerRepository customers,
                          AuditService audit) {
        this.stripe = stripe;
        this.payments = payments;
        this.agreements = agreements;
        this.customers = customers;
        this.audit = audit;
    }

    public String initiate(String customerId, String sessionId, long agreementId, long amountPence, String ip) throws StripeException {
        var number = agreements.agreement(customerId, agreementId).agreementNumber();
        var email = customers.findByCustomerId(customerId).map(Customer::email).filter(Objects::nonNull).orElse("");

        var intent = stripe.v1().paymentIntents().create(PaymentIntentCreateParams.builder()
            .setAmount(amountPence).setCurrency("gbp")
            .putMetadata("agreementNumber", number).putMetadata("customerEmail", email).build());

        payments.save(PaymentRecord.pending(agreementId, customerId, amountPence, intent.getId()));
        audit.log(customerId, "PAYMENT_INITIATED", ip, sessionId, Map.of("amount", String.valueOf(amountPence), "agreementNumber", number));
        return intent.getClientSecret();
    }
}
