package guru.junaid.azadi.settlement;

import java.time.Instant;
import java.time.LocalDate;

public record SettlementFigure(long id, long agreementId, String customerId, long amountPence, Instant calculatedAt, LocalDate validUntil) {
}
