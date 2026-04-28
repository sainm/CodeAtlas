package org.sainm.codeatlas.ai;

public record AiRuntimeConfig(
    AiProviderType provider,
    String baseUrl,
    String apiKey,
    String model,
    String embeddingModel,
    int timeoutSeconds
) {
    public AiRuntimeConfig {
        provider = provider == null ? AiProviderType.DISABLED : provider;
        baseUrl = trim(baseUrl);
        apiKey = trim(apiKey);
        model = trim(model);
        embeddingModel = trim(embeddingModel);
        timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        if (provider != AiProviderType.DISABLED && model == null) {
            throw new IllegalArgumentException("model is required when AI provider is enabled");
        }
    }

    public static AiRuntimeConfig disabled() {
        return new AiRuntimeConfig(AiProviderType.DISABLED, null, null, null, null, 0);
    }

    public boolean enabled() {
        return provider != AiProviderType.DISABLED;
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
