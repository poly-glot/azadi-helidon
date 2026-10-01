package guru.junaid.azadi.contact.dto;

import guru.junaid.azadi.auth.Customer;

public record ContactDetailsResponse(String addressLine1, String email, String mobilePhone, String phone, String postcode) {

    public static ContactDetailsResponse from(Customer c) {
        return new ContactDetailsResponse(c.addressLine1(), c.email(), c.mobilePhone(), c.phone(), c.postcode());
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getEmail() {
        return email;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public String getPhone() {
        return phone;
    }

    public String getPostcode() {
        return postcode;
    }
}
