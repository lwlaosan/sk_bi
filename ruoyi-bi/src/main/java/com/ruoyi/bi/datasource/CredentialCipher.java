package com.ruoyi.bi.datasource;

import com.ruoyi.bi.config.BiProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class CredentialCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private final byte[] key;
    private final boolean production;

    @Autowired
    public CredentialCipher(BiProperties properties, Environment environment) {
        String configured = properties.datasource().masterKey();
        this.key = configured == null || configured.isBlank() ? null : decodeKey(configured);
        this.production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    CredentialCipher(byte[] key) {
        this.key = key.clone();
        this.production = false;
    }

    @PostConstruct
    void validateConfiguration() {
        if (production && key == null) {
            throw new IllegalStateException("生产环境必须配置 256 位 BI_DATASOURCE_MASTER_KEY");
        }
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("数据源凭据加密失败", ex);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            String[] parts = encoded.split(":", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new GeneralSecurityException("unknown format");
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            if (nonce.length != NONCE_LENGTH) throw new GeneralSecurityException("invalid nonce");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("数据源凭据无法解密", ex);
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("未配置 BI_DATASOURCE_MASTER_KEY");
    }

    private static byte[] decodeKey(String configured) {
        try {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length != 32) throw new IllegalArgumentException("master key must be 32 bytes");
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("BI_DATASOURCE_MASTER_KEY 必须是 Base64 编码的 256 位密钥", ex);
        }
    }
}
