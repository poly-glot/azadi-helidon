package guru.junaid.azadi.bank;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public final class BankDetailsEncryptor {

    private final TextEncryptor encryptor;

    public BankDetailsEncryptor(String key, String saltHex) {
        this.encryptor = Encryptors.delux(key, saltHex);
    }

    public String encrypt(String plaintext) {
        return encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return encryptor.decrypt(ciphertext);
    }
}
