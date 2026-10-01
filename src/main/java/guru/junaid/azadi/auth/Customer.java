package guru.junaid.azadi.auth;

import java.time.LocalDate;

public record Customer(long id, String customerId, String fullName, String email, LocalDate dob, String postcode, String phone,
                       String mobilePhone, String addressLine1, String addressLine2, String city) {

    public Customer withContact(String newPhone, String newMobilePhone, String newEmail, String newAddressLine1, String newPostcode) {
        return new Customer(id, customerId, fullName, given(newEmail, email), dob, given(newPostcode, postcode), given(newPhone, phone),
            given(newMobilePhone, mobilePhone), given(newAddressLine1, addressLine1), addressLine2, city);
    }

    private static String given(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
