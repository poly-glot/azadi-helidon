package guru.junaid.azadi.settlement;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private AgreementService agreements;

    @Mock
    private SettlementRepository repository;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(agreements, repository);
    }

    @Test
    @DisplayName("The figure is the balance plus a two percent early settlement fee, valid for 28 days")
    void addsTheEarlySettlementFee() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        var figure = service.calculate(Fixtures.CUSTOMER_ID, 1L);

        var saved = ArgumentCaptor.forClass(SettlementFigure.class);
        verify(repository).save(saved.capture());
        assertThat(figure.amountPence()).isEqualTo(1_224_000L);
        assertThat(saved.getValue().agreementId()).isEqualTo(1L);
        assertThat(saved.getValue().customerId()).isEqualTo(Fixtures.CUSTOMER_ID);
        assertThat(saved.getValue().validUntil()).isEqualTo(LocalDate.now().plusDays(28));
    }

    @Test
    @DisplayName("Another customer's agreement is refused and nothing is stored")
    void refusesAnotherCustomersAgreement() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 2L)).thenThrow(new SecurityException("no"));

        assertThatThrownBy(() -> service.calculate(Fixtures.CUSTOMER_ID, 2L)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Earlier figures are listed from the repository")
    void listsEarlierFigures() {
        var earlier = new SettlementFigure(5L, 1L, Fixtures.CUSTOMER_ID, 1_000L, Instant.now(), LocalDate.now());
        when(repository.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(List.of(earlier));

        assertThat(service.settlementsFor(Fixtures.CUSTOMER_ID)).containsExactly(earlier);
    }
}
