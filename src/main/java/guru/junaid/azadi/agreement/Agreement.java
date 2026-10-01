package guru.junaid.azadi.agreement;

import java.time.LocalDate;

public record Agreement(long id, String agreementNumber, String customerId, String type, long balancePence, String apr,
                        int originalTermMonths, int contractMileage, long excessPricePerMilePence, String vehicleModel,
                        String registration, long lastPaymentPence, LocalDate lastPaymentDate, long nextPaymentPence,
                        LocalDate nextPaymentDate, int paymentsRemaining, LocalDate finalPaymentDate, boolean paymentDateChanged) {

    public Agreement withPaymentDay(int day) {
        var next = nextPaymentDate == null ? null : nextPaymentDate.withDayOfMonth(day);
        return new Agreement(id, agreementNumber, customerId, type, balancePence, apr, originalTermMonths, contractMileage,
            excessPricePerMilePence, vehicleModel, registration, lastPaymentPence, lastPaymentDate, nextPaymentPence, next,
            paymentsRemaining, finalPaymentDate, true);
    }
}
