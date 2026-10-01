package guru.junaid.azadi.bank.dto;

import guru.junaid.azadi.bank.BankDetails;

public record BankDetailsResponse(String accountHolderName, String lastFourAccount, String lastTwoSortCode) {

    public static BankDetailsResponse from(BankDetails d) {
        return new BankDetailsResponse(d.accountHolderName(), d.lastFourAccount(), d.lastTwoSortCode());
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getLastFourAccount() {
        return lastFourAccount;
    }

    public String getLastTwoSortCode() {
        return lastTwoSortCode;
    }
}
