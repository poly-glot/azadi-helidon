package guru.junaid.azadi.common;

public final class Ordinal {

    public static final Ordinal INSTANCE = new Ordinal();

    private static final int TEENS_START = 11;
    private static final int TEENS_END = 13;

    public String dayWithSuffix(int day) {
        return day + suffix(day);
    }

    public static String suffix(int day) {
        if (day >= TEENS_START && day <= TEENS_END) {
            return "th";
        }
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
