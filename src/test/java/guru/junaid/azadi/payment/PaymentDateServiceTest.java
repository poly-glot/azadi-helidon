package guru.junaid.azadi.payment;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.agreement.AgreementRepository;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentDateServiceTest {

    @Mock
    private AgreementService agreements;

    @Mock
    private AgreementRepository repository;

    @Mock
    private AuditService audit;

    private PaymentDateService service;

    @BeforeEach
    void setUp() {
        service = new PaymentDateService(agreements, repository, audit);
    }

    @Test
    @DisplayName("The current day comes from the next payment date")
    void reportsTheCurrentDay() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));

        assertThat(service.currentPaymentDay(Fixtures.CUSTOMER_ID, 1L)).isEqualTo(14);
        assertThat(service.alreadyChanged(Fixtures.CUSTOMER_ID, 1L)).isFalse();
    }

    @Test
    @DisplayName("A change moves the next payment day, marks the agreement and audits it")
    void changesTheDay() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));

        service.change(Fixtures.CUSTOMER_ID, 1L, 21, "10.0.0.1", "sid");

        var saved = ArgumentCaptor.forClass(Agreement.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().nextPaymentDate().getDayOfMonth()).isEqualTo(21);
        assertThat(saved.getValue().paymentDateChanged()).isTrue();
        verify(audit).log(Fixtures.CUSTOMER_ID, "PAYMENT_DATE_CHANGED", "10.0.0.1", "sid", Map.of("agreementId", "1", "newDay", "21"));
    }

    @Test
    @DisplayName("A second change is refused")
    void refusesASecondChange() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID).withPaymentDay(21));

        assertThatThrownBy(() -> service.change(Fixtures.CUSTOMER_ID, 1L, 7, "10.0.0.1", "sid"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been changed");
        verifyNoInteractions(repository, audit);
    }

    @Test
    @DisplayName("A day outside 1 to 28 is invalid")
    void refusesADayOutsideTheRange() {
        when(agreements.agreement(Fixtures.CUSTOMER_ID, 1L)).thenReturn(Fixtures.agreement(1L, "AGR-001", Fixtures.CUSTOMER_ID));

        assertThatThrownBy(() -> service.change(Fixtures.CUSTOMER_ID, 1L, 31, "10.0.0.1", "sid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.change(Fixtures.CUSTOMER_ID, 1L, 0, "10.0.0.1", "sid")).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
