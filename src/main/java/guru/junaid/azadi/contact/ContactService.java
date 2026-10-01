package guru.junaid.azadi.contact;

import guru.junaid.azadi.audit.AuditService;
import guru.junaid.azadi.auth.Customer;
import guru.junaid.azadi.auth.CustomerRepository;
import guru.junaid.azadi.contact.dto.UpdateContactCommand;

import java.util.Map;
import java.util.NoSuchElementException;

public final class ContactService {

    private final CustomerRepository customers;
    private final AuditService audit;

    public ContactService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    public Customer customer(String customerId) {
        return customers.findByCustomerId(customerId).orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerId));
    }

    public Customer update(String customerId, UpdateContactCommand command, String ip, String sessionId) {
        var saved = customers.save(customer(customerId).withContact(command.homePhone(), command.mobilePhone(), command.email(),
            command.houseName(), command.postcode()));
        audit.log(customerId, "CONTACT_DETAILS_UPDATED", ip, sessionId, Map.of("email", saved.email()));
        return saved;
    }
}
