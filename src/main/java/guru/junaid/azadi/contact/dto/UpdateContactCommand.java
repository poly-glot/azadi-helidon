package guru.junaid.azadi.contact.dto;

import java.util.Map;

public record UpdateContactCommand(String homePhone, String mobilePhone, String email, String houseName, String postcode) {

    public static UpdateContactCommand from(Map<String, String> form) {
        return new UpdateContactCommand(form.get("homePhone"), form.get("mobilePhone"), form.get("email"), form.get("houseName"),
            form.get("postcode"));
    }
}
