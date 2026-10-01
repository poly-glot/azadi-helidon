package guru.junaid.azadi.agreement;

import guru.junaid.azadi.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {

    @Mock
    private AgreementRepository repository;

    private AgreementService service;

    @BeforeEach
    void setUp() {
        service = new AgreementService(repository);
    }

    @Test
    @DisplayName("The customer's agreements are listed in repository order")
    void listsTheCustomersAgreements() {
        when(repository.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(List.of(
            Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID), Fixtures.agreement(2L, "AGR-002", Fixtures.CUSTOMER_ID)));

        assertThat(service.agreementsFor(Fixtures.CUSTOMER_ID)).extracting(Agreement::agreementNumber).containsExactly("AGR-001", "AGR-002");
    }

    @Test
    @DisplayName("An owned agreement is returned")
    void returnsAnOwnedAgreement() {
        when(repository.findById(1L)).thenReturn(Optional.of(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID)));

        assertThat(service.agreement(Fixtures.CUSTOMER_ID, 1L).agreementNumber()).isEqualTo("AGR-001");
    }

    @Test
    @DisplayName("Another customer's agreement is refused")
    void refusesAnotherCustomersAgreement() {
        when(repository.findById(1L)).thenReturn(Optional.of(Fixtures.agreement(1L, "AGR-001", Fixtures.OTHER_CUSTOMER_ID)));

        assertThatThrownBy(() -> service.agreement(Fixtures.CUSTOMER_ID, 1L))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("You do not have access to this agreement");
    }

    @Test
    @DisplayName("A missing agreement is not found")
    void reportsAMissingAgreement() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agreement(Fixtures.CUSTOMER_ID, 999L))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Agreement not found: 999");
    }
}
