package guru.junaid.azadi.bank;

import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.bank.dto.BankDetailsResponse;
import guru.junaid.azadi.bank.dto.UpdateBankDetailsRequest;
import guru.junaid.azadi.email.EmailService;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public final class BankDetailsService {

    private final BankDetailsRepository bankDetails;
    private final BankDetailsEncryptor encryptor;
    private final AuditService audit;
    private final EmailService email;

    public BankDetailsService(BankDetailsRepository bankDetails, BankDetailsEncryptor encryptor, AuditService audit, EmailService email) {
        this.bankDetails = bankDetails;
        this.encryptor = encryptor;
        this.audit = audit;
        this.email = email;
    }

    public Optional<BankDetailsResponse> bankDetails(String customerId) {
        return bankDetails.findByCustomerId(customerId).map(BankDetailsResponse::from);
    }

    public BankDetails update(String customerId, UpdateBankDetailsRequest request, String ip, String sessionId) {
        var id = bankDetails.findByCustomerId(customerId).map(BankDetails::id).orElse(0L);
        var saved = bankDetails.save(new BankDetails(id, customerId, request.accountHolderName(),
            encryptor.encrypt(request.accountNumber()), encryptor.encrypt(request.sortCode()),
            request.lastFourAccount(), request.lastTwoSortCode(), Instant.now()));

        audit.log(customerId, "BANK_DETAILS_UPDATED", ip, sessionId, Map.of("accountEnding", saved.lastFourAccount()));
        email.bankDetailsUpdated(customerId);
        return saved;
    }
}
