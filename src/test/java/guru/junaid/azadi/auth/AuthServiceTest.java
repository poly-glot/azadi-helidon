package guru.junaid.azadi.auth;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.AgreementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String AGREEMENT = "AGR-001";
    private static final String CREDENTIALS = "25/3/1990|SW1A 1AA";
    private static final String WRONG_DOB = "1/1/1985|SW1A 1AA";

    @Mock
    private AgreementRepository agreements;

    @Mock
    private CustomerRepository customers;

    private final LoginAttemptTracker attempts = new LoginAttemptTracker();
    private final Sessions sessions = new Sessions();
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(agreements, customers, attempts, sessions);
    }

    @Test
    @DisplayName("Matching date of birth and postcode open a session for the customer")
    void signsInWithMatchingCredentials() {
        known();

        var session = auth.login(AGREEMENT, CREDENTIALS);

        assertThat(session).isPresent();
        assertThat(session.get().customerId()).isEqualTo(Fixtures.CUSTOMER_ID);
        assertThat(session.get().name()).isEqualTo("Test Customer");
        assertThat(sessions.get(session.get().id())).isPresent();
    }

    @Test
    @DisplayName("The postcode is compared without spaces or case")
    void normalisesThePostcode() {
        known();

        assertThat(auth.login(AGREEMENT, "25/3/1990|sw1a1aa")).isPresent();
    }

    @Test
    @DisplayName("The wrong date of birth is refused and counted")
    void refusesTheWrongDateOfBirth() {
        known();

        assertThat(auth.login(AGREEMENT, WRONG_DOB)).isEmpty();
        assertThat(attempts.isBlocked(AGREEMENT)).isFalse();
    }

    @Test
    @DisplayName("Malformed credentials, an unparseable date or missing input are refused without a lookup")
    void refusesMalformedInput() {
        assertThat(auth.login(AGREEMENT, "nonsense")).isEmpty();
        assertThat(auth.login(AGREEMENT, "32/13/1990|SW1A 1AA")).isEmpty();
        assertThat(auth.login(null, CREDENTIALS)).isEmpty();
        assertThat(auth.login(AGREEMENT, null)).isEmpty();
    }

    @Test
    @DisplayName("Five failures lock the agreement so even the right credentials are refused")
    void locksOutAfterFiveFailures() {
        known();
        for (var attempt = 0; attempt < 5; attempt++) {
            auth.login(AGREEMENT, WRONG_DOB);
        }

        assertThat(auth.login(AGREEMENT, CREDENTIALS)).isEmpty();
    }

    @Test
    @DisplayName("A success wipes the earlier failures")
    void successClearsFailures() {
        known();
        auth.login(AGREEMENT, WRONG_DOB);
        auth.login(AGREEMENT, CREDENTIALS);
        for (var attempt = 0; attempt < 4; attempt++) {
            auth.login(AGREEMENT, WRONG_DOB);
        }

        assertThat(auth.login(AGREEMENT, CREDENTIALS)).isPresent();
    }

    private void known() {
        when(agreements.findByAgreementNumber(AGREEMENT)).thenReturn(Optional.of(Fixtures.agreement(1L, AGREEMENT, Fixtures.CUSTOMER_ID)));
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.customer(Fixtures.CUSTOMER_ID)));
    }
}
