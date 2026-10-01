package guru.junaid.azadi.agreement.dto;

import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.common.Money;

import java.time.LocalDate;

public record AgreementResponse(long id, String agreementNumber, String type, String balance, String apr, int originalTermMonths,
                                int contractMileage, String excessPricePerMile, String vehicleModel, String registration,
                                String lastPayment, LocalDate lastPaymentDate, String nextPayment, LocalDate nextPaymentDate,
                                int paymentsRemaining, LocalDate finalPaymentDate) {

    public static AgreementResponse from(Agreement a) {
        return new AgreementResponse(a.id(), a.agreementNumber(), a.type(), Money.pence(a.balancePence()),
            a.apr() == null || a.apr().isEmpty() ? "N/A" : a.apr() + "%", a.originalTermMonths(), a.contractMileage(),
            Money.pence(a.excessPricePerMilePence()), a.vehicleModel(), a.registration(), Money.pence(a.lastPaymentPence()),
            a.lastPaymentDate(), Money.pence(a.nextPaymentPence()), a.nextPaymentDate(), a.paymentsRemaining(), a.finalPaymentDate());
    }

    public long getId() {
        return id;
    }

    public String getAgreementNumber() {
        return agreementNumber;
    }
}
