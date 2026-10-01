package guru.junaid.azadi.settlement;

import guru.junaid.azadi.agreement.AgreementService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SettlementService {

    private static final long EARLY_SETTLEMENT_FEE_BPS = 200;
    private static final long BPS_PER_WHOLE = 10_000;
    private static final int VALIDITY_DAYS = 28;

    private final AgreementService agreements;
    private final SettlementRepository settlements;

    public SettlementService(AgreementService agreements, SettlementRepository settlements) {
        this.agreements = agreements;
        this.settlements = settlements;
    }

    public SettlementFigure calculate(String customerId, long agreementId) {
        var balance = agreements.agreement(customerId, agreementId).balancePence();
        var total = balance + balance * EARLY_SETTLEMENT_FEE_BPS / BPS_PER_WHOLE;
        return settlements.save(new SettlementFigure(0, agreementId, customerId, total, Instant.now(), LocalDate.now().plusDays(VALIDITY_DAYS)));
    }

    public List<SettlementFigure> settlementsFor(String customerId) {
        return settlements.findByCustomerId(customerId);
    }
}
