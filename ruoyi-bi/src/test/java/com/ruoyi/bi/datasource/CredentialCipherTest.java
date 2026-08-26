package com.ruoyi.bi.datasource;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.*;

class CredentialCipherTest {
    @Test
    void encryptsWithRandomNonceAndAuthenticatesCiphertext() {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        CredentialCipher cipher = new CredentialCipher(key);
        String first = cipher.encrypt("s3cret-密码");
        String second = cipher.encrypt("s3cret-密码");
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("s3cret-密码");
        int middle = first.length() / 2;
        String tampered = first.substring(0, middle) + (first.charAt(middle) == 'A' ? 'B' : 'A') + first.substring(middle + 1);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class);
    }
}
