package guru.junaid.azadi.payment;

import com.stripe.Stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class StripeEvents {

    private StripeEvents() {
    }

    public static String event(String eventId, String type, String intentId) {
        return ("{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"type\":\"%s\","
            + "\"data\":{\"object\":{\"id\":\"%s\",\"object\":\"payment_intent\",\"amount\":45000,"
            + "\"currency\":\"gbp\",\"status\":\"succeeded\"}}}")
            .formatted(eventId, Stripe.API_VERSION, type, intentId);
    }

    public static String signature(String payload, String secret) {
        var timestamp = epochSeconds();
        return "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload, secret);
    }

    public static String forgedSignature() {
        return "t=" + epochSeconds() + ",v1=deadbeef";
    }

    private static String hmac(String signed, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long epochSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}
