package guru.junaid.azadi.payment;

import guru.junaid.azadi.agreement.AgreementRepository;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;

import java.util.Map;

public final class PaymentDateService {

    private static final int FIRST_DAY = 1;
    private static final int LAST_DAY = 28;

    private final AgreementService agreements;
    private final AgreementRepository repository;
    private final AuditService audit;

    public PaymentDateService(AgreementService agreements, AgreementRepository repository, AuditService audit) {
        this.agreements = agreements;
        this.repository = repository;
        this.audit = audit;
    }

    public int currentPaymentDay(String customerId, long agreementId) {
        var next = agreements.agreement(customerId, agreementId).nextPaymentDate();
        return next == null ? FIRST_DAY : next.getDayOfMonth();
    }

    public boolean alreadyChanged(String customerId, long agreementId) {
        return agreements.agreement(customerId, agreementId).paymentDateChanged();
    }

    public void change(String customerId, long agreementId, int newDay, String ip, String sessionId) {
        var agreement = agreements.agreement(customerId, agreementId);
        if (agreement.paymentDateChanged()) {
            throw new IllegalStateException("Payment date has already been changed for this agreement.");
        }
        if (newDay < FIRST_DAY || newDay > LAST_DAY) {
            throw new IllegalArgumentException("Payment day must be between " + FIRST_DAY + " and " + LAST_DAY + ".");
        }
        repository.save(agreement.withPaymentDay(newDay));
        audit.log(customerId, "PAYMENT_DATE_CHANGED", ip, sessionId,
            Map.of("agreementId", String.valueOf(agreementId), "newDay", String.valueOf(newDay)));
    }
}
