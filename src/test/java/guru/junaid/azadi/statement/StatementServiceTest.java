package guru.junaid.azadi.statement;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementService;
import guru.junaid.azadi.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private AgreementService agreements;

    @Mock
    private StatementRepository repository;

    @Mock
    private AuditService audit;

    private StatementService service;

    @BeforeEach
    void setUp() {
        service = new StatementService(agreements, repository, audit);
    }

    @Test
    @DisplayName("A request is stored pending against the chosen agreement and audited")
    void recordsAPendingRequest() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        var request = service.request(Fixtures.CUSTOMER_ID, Optional.of(3L), "10.0.0.1", "sid").orElseThrow();

        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.agreementId()).isEqualTo(3L);
        verify(audit).log(Fixtures.CUSTOMER_ID, "STATEMENT_REQUESTED", "10.0.0.1", "sid", Map.of("agreementId", "3"));
    }

    @Test
    @DisplayName("With no agreement chosen the customer's first agreement is used")
    void fallsBackToTheFirstAgreement() {
        when(agreements.agreementsFor(Fixtures.CUSTOMER_ID)).thenReturn(List.of(Fixtures.agreement(8L, "AGR-008", Fixtures.CUSTOMER_ID)));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.request(Fixtures.CUSTOMER_ID, Optional.empty(), "10.0.0.1", "sid");

        var saved = ArgumentCaptor.forClass(StatementRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().agreementId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("A customer without agreements gets no request and no audit entry")
    void doesNothingWithoutAgreements() {
        when(agreements.agreementsFor(Fixtures.CUSTOMER_ID)).thenReturn(List.of());

        assertThat(service.request(Fixtures.CUSTOMER_ID, Optional.empty(), "10.0.0.1", "sid")).isEmpty();
        verifyNoInteractions(repository, audit);
    }
}
