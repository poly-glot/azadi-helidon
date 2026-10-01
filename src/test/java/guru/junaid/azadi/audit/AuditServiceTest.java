package guru.junaid.azadi.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository repository;

    @Test
    @DisplayName("An event stores the details as JSON and only a hash of the session id")
    void storesHashedSessionAndJsonDetails() {
        new AuditService(repository).log("CUST-1", "PAYMENT_INITIATED", "10.0.0.1", "session-secret", Map.of("amount", "45000"));

        var saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().customerId()).isEqualTo("CUST-1");
        assertThat(saved.getValue().eventType()).isEqualTo("PAYMENT_INITIATED");
        assertThat(saved.getValue().ipAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getValue().details()).isEqualTo("{\"amount\":\"45000\"}");
        assertThat(saved.getValue().sessionIdHash()).hasSize(64).isNotEqualTo("session-secret");
        assertThat(saved.getValue().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("The same session always hashes the same way")
    void hashesDeterministically() {
        assertThat(AuditService.sha256("abc")).isEqualTo(AuditService.sha256("abc")).isNotEqualTo(AuditService.sha256("abd"));
    }
}
