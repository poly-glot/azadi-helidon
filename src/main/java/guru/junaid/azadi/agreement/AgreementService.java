package guru.junaid.azadi.agreement;

import java.util.List;
import java.util.NoSuchElementException;

public final class AgreementService {

    private final AgreementRepository agreements;

    public AgreementService(AgreementRepository agreements) {
        this.agreements = agreements;
    }

    public List<Agreement> agreementsFor(String customerId) {
        return agreements.findByCustomerId(customerId);
    }

    public Agreement agreement(String customerId, long agreementId) {
        var agreement = agreements.findById(agreementId)
            .orElseThrow(() -> new NoSuchElementException("Agreement not found: " + agreementId));
        if (!customerId.equals(agreement.customerId())) {
            throw new SecurityException("You do not have access to this agreement.");
        }
        return agreement;
    }
}
