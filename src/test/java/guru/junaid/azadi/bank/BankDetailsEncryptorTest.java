package guru.junaid.azadi.bank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankDetailsEncryptorTest {

    private static final String KEY = "dev-only-key-change-in-prod-32ch";
    private static final String SALT = "a1b2c3d4e5f6a7b8";

    private final BankDetailsEncryptor encryptor = new BankDetailsEncryptor(KEY, SALT);

    @Test
    @DisplayName("Ciphertext decrypts back to the account number")
    void roundTripsAccountNumber() {
        assertThat(encryptor.decrypt(encryptor.encrypt("12345678"))).isEqualTo("12345678");
    }

    @Test
    @DisplayName("The same plaintext encrypts to different ciphertexts")
    void usesAFreshInitialisationVector() {
        assertThat(encryptor.encrypt("12345678")).isNotEqualTo(encryptor.encrypt("12345678"));
    }

    @Test
    @DisplayName("Another key cannot read the ciphertext")
    void refusesTheWrongKey() {
        var ciphertext = encryptor.encrypt("12345678");

        assertThatThrownBy(() -> new BankDetailsEncryptor("a-different-key-also-32-chars-ok", SALT).decrypt(ciphertext))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Two instances with the same key and salt read each other's ciphertext")
    void keyAndSaltAreEnoughToDecrypt() {
        assertThat(new BankDetailsEncryptor(KEY, SALT).decrypt(encryptor.encrypt("11-22-33"))).isEqualTo("11-22-33");
    }
}
