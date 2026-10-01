package guru.junaid.azadi.settlement.dto;

import guru.junaid.azadi.common.Money;
import guru.junaid.azadi.settlement.SettlementFigure;

import java.time.Instant;
import java.time.LocalDate;

public record SettlementResponse(long id, long agreementId, String amount, Instant calculatedAt, LocalDate validUntil) {

    public static SettlementResponse from(SettlementFigure s) {
        return new SettlementResponse(s.id(), s.agreementId(), Money.pence(s.amountPence()), s.calculatedAt(), s.validUntil());
    }

    public long getAgreementId() {
        return agreementId;
    }

    public String getAmount() {
        return amount;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }
}
