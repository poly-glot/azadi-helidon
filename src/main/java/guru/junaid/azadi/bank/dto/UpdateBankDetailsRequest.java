package guru.junaid.azadi.bank.dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record UpdateBankDetailsRequest(String accountHolderName, String accountNumber, String sortCode) {

    private static final int MAX_HOLDER_LENGTH = 70;
    private static final int LAST_FOUR = 4;
    private static final int LAST_TWO = 2;
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{8}");
    private static final Pattern SORT_CODE = Pattern.compile("\\d{2}-\\d{2}-\\d{2}");

    public static UpdateBankDetailsRequest from(Map<String, String> form) {
        return new UpdateBankDetailsRequest(form.get("accountHolderName"), form.get("accountNumber"), form.get("sortCode"));
    }

    public List<String> validate() {
        return Stream.of(
                check(accountHolderName, "Account holder name", h -> h.length() <= MAX_HOLDER_LENGTH,
                    "must not exceed " + MAX_HOLDER_LENGTH + " characters"),
                check(accountNumber, "Account number", ACCOUNT_NUMBER.asMatchPredicate(), "must be exactly 8 digits"),
                check(sortCode, "Sort code", SORT_CODE.asMatchPredicate(), "must be in XX-XX-XX format"))
            .flatMap(Optional::stream)
            .toList();
    }

    public String lastFourAccount() {
        return accountNumber.substring(accountNumber.length() - LAST_FOUR);
    }

    public String lastTwoSortCode() {
        return sortCode.substring(sortCode.length() - LAST_TWO);
    }

    private static Optional<String> check(String value, String label, Predicate<String> wellFormed, String requirement) {
        if (value == null || value.isBlank()) {
            return Optional.of(label + " is required");
        }
        return wellFormed.test(value) ? Optional.empty() : Optional.of(label + " " + requirement);
    }
}
