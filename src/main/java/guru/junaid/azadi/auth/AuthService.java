package guru.junaid.azadi.auth;

import guru.junaid.azadi.agreement.AgreementRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class AuthService {

    private static final DateTimeFormatter DOB = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final AgreementRepository agreements;
    private final CustomerRepository customers;
    private final LoginAttemptTracker attempts;
    private final Sessions sessions;

    public AuthService(AgreementRepository agreements, CustomerRepository customers, LoginAttemptTracker attempts, Sessions sessions) {
        this.agreements = agreements;
        this.customers = customers;
        this.attempts = attempts;
        this.sessions = sessions;
    }

    public Optional<Session> login(String agreementNumber, String credentials) {
        if (agreementNumber == null || credentials == null || attempts.isBlocked(agreementNumber)) {
            return Optional.empty();
        }
        var session = customer(agreementNumber, credentials).map(c -> sessions.create(c.customerId(), c.fullName()));
        session.ifPresentOrElse(s -> attempts.recordSuccess(agreementNumber), () -> attempts.recordFailure(agreementNumber));
        return session;
    }

    private Optional<Customer> customer(String agreementNumber, String credentials) {
        var parts = credentials.split("\\|", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        return dateOfBirth(parts[0]).flatMap(dob -> agreements.findByAgreementNumber(agreementNumber)
            .flatMap(agreement -> customers.findByCustomerId(agreement.customerId()))
            .filter(c -> dob.equals(c.dob()) && postcode(parts[1]).equals(postcode(c.postcode()))));
    }

    private static Optional<LocalDate> dateOfBirth(String text) {
        try {
            return Optional.of(LocalDate.parse(text, DOB));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String postcode(String p) {
        return p == null ? "" : p.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
