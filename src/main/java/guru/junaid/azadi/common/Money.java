package guru.junaid.azadi.common;

import java.util.Locale;

public final class Money {

    private Money() {
    }

    public static String pence(long pence) {
        return String.format(Locale.ROOT, "£%,.2f", pence / 100.0);
    }
}
