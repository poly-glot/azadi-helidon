package guru.junaid.azadi.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionsTest {

    private static final String CUSTOMER = "CUST-1";

    private final Sessions sessions = new Sessions();

    @Test
    @DisplayName("A new session is retrievable by its id and carries the customer")
    void createsRetrievableSession() {
        var session = sessions.create(CUSTOMER, "Test Customer");

        assertThat(sessions.get(session.id())).containsSame(session);
        assertThat(session.customerId()).isEqualTo(CUSTOMER);
        assertThat(session.name()).isEqualTo("Test Customer");
    }

    @Test
    @DisplayName("Logging in again replaces the previous session for that customer")
    void oneSessionPerCustomer() {
        var first = sessions.create(CUSTOMER, "Test Customer");
        var second = sessions.create(CUSTOMER, "Test Customer");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(sessions.get(first.id())).isEmpty();
        assertThat(sessions.get(second.id())).containsSame(second);
    }

    @Test
    @DisplayName("Two customers hold independent sessions")
    void keepsCustomersApart() {
        var one = sessions.create(CUSTOMER, "One");
        var two = sessions.create("CUST-2", "Two");

        assertThat(sessions.get(one.id())).containsSame(one);
        assertThat(sessions.get(two.id())).containsSame(two);
    }

    @Test
    @DisplayName("Invalidating a session drops it and frees the customer")
    void invalidateDropsSession() {
        var session = sessions.create(CUSTOMER, "Test Customer");

        sessions.invalidate(session.id());

        assertThat(sessions.get(session.id())).isEmpty();
        assertThat(sessions.create(CUSTOMER, "Test Customer").id()).isNotEqualTo(session.id());
    }

    @Test
    @DisplayName("An unknown or absent cookie yields no session")
    void unknownIdYieldsNothing() {
        assertThat(sessions.get(null)).isEmpty();
        assertThat(sessions.get("not-a-session")).isEmpty();
    }

    @Test
    @DisplayName("Invalidating an unknown id is harmless")
    void invalidateToleratesUnknownId() {
        sessions.invalidate(null);
        sessions.invalidate("not-a-session");

        assertThat(sessions.get("not-a-session")).isEmpty();
    }

    @Test
    @DisplayName("Tokens are unguessable and unique")
    void issuesUniqueTokens() {
        assertThat(Sessions.token()).hasSizeGreaterThanOrEqualTo(43).isNotEqualTo(Sessions.token());
    }

    @Test
    @DisplayName("Flash messages are handed out once")
    void carriesFlashMessagesOnce() {
        var session = sessions.create(CUSTOMER, "Test Customer");

        session.flash("success", "Saved.");

        assertThat(session.takeFlash()).containsEntry("success", "Saved.");
        assertThat(session.takeFlash()).isEmpty();
    }
}
