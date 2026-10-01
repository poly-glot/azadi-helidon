package guru.junaid.azadi.bank;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.bank.dto.UpdateBankDetailsRequest;
import guru.junaid.azadi.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankDetailsServiceTest {

    private static final UpdateBankDetailsRequest REQUEST = new UpdateBankDetailsRequest("Test Customer", "12345678", "11-22-33");

    @Mock
    private BankDetailsRepository repository;

    @Mock
    private AuditService audit;

    @Mock
    private EmailService email;

    private final BankDetailsEncryptor encryptor = new BankDetailsEncryptor("dev-only-key-change-in-prod-32ch", "a1b2c3d4e5f6a7b8");
    private BankDetailsService service;

    @BeforeEach
    void setUp() {
        service = new BankDetailsService(repository, encryptor, audit, email);
    }

    @Test
    @DisplayName("New details are stored encrypted with masked endings, audited and confirmed by email")
    void storesNewDetails() {
        when(repository.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(Fixtures.CUSTOMER_ID, REQUEST, "10.0.0.1", "sid");

        var saved = ArgumentCaptor.forClass(BankDetails.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isZero();
        assertThat(saved.getValue().lastFourAccount()).isEqualTo("5678");
        assertThat(saved.getValue().lastTwoSortCode()).isEqualTo("33");
        assertThat(saved.getValue().encryptedAccountNumber()).isNotEqualTo("12345678");
        assertThat(encryptor.decrypt(saved.getValue().encryptedAccountNumber())).isEqualTo("12345678");
        assertThat(encryptor.decrypt(saved.getValue().encryptedSortCode())).isEqualTo("11-22-33");
        verify(audit).log(Fixtures.CUSTOMER_ID, "BANK_DETAILS_UPDATED", "10.0.0.1", "sid", Map.of("accountEnding", "5678"));
        verify(email).bankDetailsUpdated(Fixtures.CUSTOMER_ID);
    }

    @Test
    @DisplayName("Existing details are replaced in place rather than duplicated")
    void replacesExistingDetails() {
        when(repository.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.bankDetails(42L, Fixtures.CUSTOMER_ID)));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(Fixtures.CUSTOMER_ID, REQUEST, "10.0.0.1", "sid");

        var saved = ArgumentCaptor.forClass(BankDetails.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("The page only ever sees the masked endings")
    void masksStoredDetails() {
        when(repository.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.bankDetails(42L, Fixtures.CUSTOMER_ID)));

        var shown = service.bankDetails(Fixtures.CUSTOMER_ID).orElseThrow();

        assertThat(shown.getLastFourAccount()).isEqualTo("7788");
        assertThat(shown.getLastTwoSortCode()).isEqualTo("66");
        assertThat(shown.getAccountHolderName()).isEqualTo("Test Customer");
    }
}
