package org.sainm.codeatlas.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiApiKeyProtectorTest {
    @Test
    void encryptsApiKeyForStorageAndDecryptsForRuntimeUse() {
        AiApiKeyProtector protector = AiApiKeyProtector.fromMasterSecret("local-master-secret");

        EncryptedAiApiKey encrypted = protector.encrypt("sk-live-secret-value");

        assertEquals("AES/GCM/NoPadding", encrypted.algorithm());
        assertFalse(encrypted.cipherText().contains("sk-live-secret-value"));
        assertEquals("sk-live-secret-value", protector.decrypt(encrypted));
    }

    @Test
    void rejectsBlankMasterSecretAndBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> AiApiKeyProtector.fromMasterSecret(" "));

        AiApiKeyProtector protector = AiApiKeyProtector.fromMasterSecret("local-master-secret");
        assertThrows(IllegalArgumentException.class, () -> protector.encrypt(" "));
    }
}
