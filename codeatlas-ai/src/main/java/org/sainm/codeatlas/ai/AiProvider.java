package org.sainm.codeatlas.ai;

public interface AiProvider {
    default AiProviderType type() {
        return AiProviderType.CUSTOM;
    }

    AiTextResult complete(AiPrompt prompt, AiRuntimeConfig config);
}
