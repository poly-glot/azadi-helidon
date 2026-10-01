package guru.junaid.azadi.payment.dto;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Optional;

public record MakePaymentRequest(Long agreementId, long amountPence) {

    private static final Gson GSON = new Gson();
    private static final long MINIMUM_PENCE = 100;

    public static Optional<MakePaymentRequest> parse(String json) {
        try {
            return Optional.ofNullable(GSON.fromJson(json, MakePaymentRequest.class)).filter(MakePaymentRequest::valid);
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
    }

    private boolean valid() {
        return agreementId != null && amountPence >= MINIMUM_PENCE;
    }
}
