package org.sainm.codeatlas.ai;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AiApiKeyProtector {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom random;

    private AiApiKeyProtector(SecretKeySpec key, SecureRandom random) {
        this.key = key;
        this.random = random;
    }

    public static AiApiKeyProtector fromMasterSecret(String masterSecret) {
        if (masterSecret == null || masterSecret.isBlank()) {
            throw new IllegalArgumentException("masterSecret is required");
        }
        return new AiApiKeyProtector(new SecretKeySpec(deriveKey(masterSecret), "AES"), new SecureRandom());
    }

    public EncryptedAiApiKey encrypt(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            return new EncryptedAiApiKey(
                ALGORITHM,
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encrypted)
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt AI API key", ex);
        }
    }

    public String decrypt(EncryptedAiApiKey encrypted) {
        if (encrypted == null) {
            throw new IllegalArgumentException("encrypted API key is required");
        }
        if (!ALGORITHM.equals(encrypted.algorithm())) {
            throw new IllegalArgumentException("Unsupported encrypted API key algorithm: " + encrypted.algorithm());
        }
        try {
            byte[] iv = Base64.getDecoder().decode(encrypted.iv());
            byte[] cipherText = Base64.getDecoder().decode(encrypted.cipherText());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt AI API key", ex);
        }
    }

    private static byte[] deriveKey(String masterSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(masterSecret.trim().getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 32);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to derive AI API key encryption key", ex);
        }
    }
}
