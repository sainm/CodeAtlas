package org.sainm.codeatlas.ai;

public record AiProjectPolicy(
    boolean enabled,
    boolean allowSourceSnippets,
    int maxSnippetCharacters
) {
    public AiProjectPolicy {
        maxSnippetCharacters = maxSnippetCharacters <= 0 ? 800 : maxSnippetCharacters;
    }

    public static AiProjectPolicy disabled() {
        return new AiProjectPolicy(false, false, 800);
    }
}
