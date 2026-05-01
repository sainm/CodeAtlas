package org.sainm.codeatlas.ai;

public record EncryptedAiApiKey(
    String algorithm,
    String iv,
    String cipherText
) {
    public EncryptedAiApiKey {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm is required");
        }
        if (iv == null || iv.isBlank()) {
            throw new IllegalArgumentException("iv is required");
        }
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText is required");
        }
        algorithm = algorithm.trim();
        iv = iv.trim();
        cipherText = cipherText.trim();
    }
}
