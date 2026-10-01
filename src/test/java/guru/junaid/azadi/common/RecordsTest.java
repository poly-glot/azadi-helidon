package guru.junaid.azadi.common;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.agreement.Agreement;
import guru.junaid.azadi.audit.AuditEvent;
import guru.junaid.azadi.payment.PaymentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordsTest {

    private static final Records<Agreement> AGREEMENTS = Records.of(Agreement.class, "Agreement");
    private static final Records<PaymentRecord> PAYMENTS = Records.of(PaymentRecord.class, "PaymentRecord");
    private static final Key KEY = Key.newBuilder("p", "Agreement", 42L).build();

    @Test
    @DisplayName("A record survives a round trip through an entity, with the key as its id")
    void roundTripsThroughAnEntity() {
        var agreement = Fixtures.agreement(42L, "AGR-042", Fixtures.CUSTOMER_ID).withPaymentDay(21);
        var builder = Entity.newBuilder(KEY);

        AGREEMENTS.fill(builder, agreement);
        var entity = builder.build();

        assertThat(entity.contains("id")).isFalse();
        assertThat(entity.getString("agreementNumber")).isEqualTo("AGR-042");
        assertThat(entity.getLong("originalTermMonths")).isEqualTo(48L);
        assertThat(entity.getBoolean("paymentDateChanged")).isTrue();
        assertThat(AGREEMENTS.from(entity)).isEqualTo(agreement);
        assertThat(AGREEMENTS.id(agreement)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Missing or null properties read as null, zero and false")
    void readsMissingPropertiesAsDefaults() {
        var sparse = Entity.newBuilder(KEY).set("customerId", Fixtures.CUSTOMER_ID).setNull("apr").build();

        var agreement = AGREEMENTS.from(sparse);

        assertThat(agreement.id()).isEqualTo(42L);
        assertThat(agreement.customerId()).isEqualTo(Fixtures.CUSTOMER_ID);
        assertThat(agreement.apr()).isNull();
        assertThat(agreement.agreementNumber()).isNull();
        assertThat(agreement.nextPaymentDate()).isNull();
        assertThat(agreement.balancePence()).isZero();
        assertThat(agreement.paymentsRemaining()).isZero();
        assertThat(agreement.paymentDateChanged()).isFalse();
    }

    @Test
    @DisplayName("Null components are written as null properties and instants keep their precision")
    void writesNullsAndInstants() {
        var at = Instant.parse("2026-10-01T12:34:56.789Z");
        var pending = new PaymentRecord(0, 1L, Fixtures.CUSTOMER_ID, 100L, "pi_1", PaymentRecord.STATUS_PENDING, at, null, null);
        var builder = Entity.newBuilder(Key.newBuilder("p", "PaymentRecord", 7L).build());

        PAYMENTS.fill(builder, pending);
        var entity = builder.build();

        assertThat(entity.isNull("completedAt")).isTrue();
        assertThat(entity.isNull("webhookEventId")).isTrue();
        assertThat(PAYMENTS.from(entity).createdAt()).isEqualTo(at);
        assertThat(PAYMENTS.id(pending)).isZero();
    }

    @Test
    @DisplayName("A record without an id component is always new")
    void recordsWithoutAnIdAreNew() {
        var rows = Records.of(AuditEvent.class, "AuditEvent");

        assertThat(rows.id(new AuditEvent("c", "E", "ip", "hash", "{}", Instant.now()))).isZero();
        assertThat(rows.kind()).isEqualTo("AuditEvent");
    }

    @Test
    @DisplayName("A component type the mapper does not know is refused up front")
    void refusesUnsupportedComponentTypes() {
        record Odd(long id, LocalDate when, Object payload) { }

        assertThatThrownBy(() -> Records.of(Odd.class, "Odd")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("payload");
    }
}
