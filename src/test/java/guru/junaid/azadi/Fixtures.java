package guru.junaid.azadi;

import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.bank.BankDetails;
import guru.junaid.azadi.payment.PaymentRecord;

import java.time.Instant;
import java.time.LocalDate;

public final class Fixtures {

    public static final String CUSTOMER_ID = "CUST-1";
    public static final String OTHER_CUSTOMER_ID = "CUST-2";
    public static final LocalDate DOB = LocalDate.of(1990, 3, 25);
    public static final String POSTCODE = "SW1A 1AA";

    private Fixtures() {
    }

    public static Agreement agreement(long id, String agreementNumber, String customerId) {
        return new Agreement(id, agreementNumber, customerId, "Personal Contract Purchase", 1_200_000L, "6.9", 48, 10_000, 10L,
            "2024 Test Vehicle", "AB24 TST", 45_000L, LocalDate.of(2026, 3, 1), 45_000L, LocalDate.of(2026, 4, 14), 24,
            LocalDate.of(2028, 3, 1), false);
    }

    public static Customer customer(String customerId) {
        return new Customer(7L, customerId, "Test Customer", "test@example.com", DOB, POSTCODE, "02000000000", "07000000000",
            "1 Test Street", null, "London");
    }

    public static BankDetails bankDetails(long id, String customerId) {
        return new BankDetails(id, customerId, "Test Customer", "enc-acc", "enc-sort", "7788", "66", Instant.now());
    }

    public static PaymentRecord pendingPayment(long id, String customerId, String intentId) {
        return new PaymentRecord(id, 1L, customerId, 45_000L, intentId, PaymentRecord.STATUS_PENDING, Instant.now(), null, null);
    }
}
