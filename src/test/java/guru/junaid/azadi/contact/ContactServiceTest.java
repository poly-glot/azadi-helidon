package guru.junaid.azadi.contact;

import guru.junaid.azadi.Fixtures;
import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.auth.CustomerRepository;
import guru.junaid.azadi.contact.dto.UpdateContactCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private CustomerRepository customers;

    @Mock
    private AuditService audit;

    private ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(customers, audit);
    }

    @Test
    @DisplayName("Submitted fields replace the stored ones and the change is audited with the new email")
    void updatesSubmittedFields() {
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.customer(Fixtures.CUSTOMER_ID)));
        when(customers.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(Fixtures.CUSTOMER_ID, new UpdateContactCommand("02011112222", "07999888777", "new@example.com", "2 New Street", "E1 6AN"),
            "10.0.0.1", "sid");

        var saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(saved.getValue().phone()).isEqualTo("02011112222");
        assertThat(saved.getValue().mobilePhone()).isEqualTo("07999888777");
        assertThat(saved.getValue().email()).isEqualTo("new@example.com");
        assertThat(saved.getValue().addressLine1()).isEqualTo("2 New Street");
        assertThat(saved.getValue().postcode()).isEqualTo("E1 6AN");
        assertThat(saved.getValue().city()).isEqualTo("London");
        verify(audit).log(Fixtures.CUSTOMER_ID, "CONTACT_DETAILS_UPDATED", "10.0.0.1", "sid", Map.of("email", "new@example.com"));
    }

    @Test
    @DisplayName("Blank fields leave the stored values alone")
    void keepsStoredValuesForBlankFields() {
        when(customers.findByCustomerId(Fixtures.CUSTOMER_ID)).thenReturn(Optional.of(Fixtures.customer(Fixtures.CUSTOMER_ID)));
        when(customers.save(any())).thenAnswer(call -> call.getArgument(0));

        var saved = service.update(Fixtures.CUSTOMER_ID, new UpdateContactCommand("", "  ", "kept@example.com", null, ""), "10.0.0.1", "sid");

        assertThat(saved.phone()).isEqualTo("02000000000");
        assertThat(saved.addressLine1()).isEqualTo("1 Test Street");
        assertThat(saved.email()).isEqualTo("kept@example.com");
    }

    @Test
    @DisplayName("An unknown customer is not found")
    void reportsAnUnknownCustomer() {
        when(customers.findByCustomerId("CUST-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.customer("CUST-404")).isInstanceOf(NoSuchElementException.class);
    }
}
