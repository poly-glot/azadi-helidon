package guru.junaid.azadi.bank;

import java.time.Instant;

public record BankDetails(long id, String customerId, String accountHolderName, String encryptedAccountNumber, String encryptedSortCode,
                          String lastFourAccount, String lastTwoSortCode, Instant updatedAt) {
}
