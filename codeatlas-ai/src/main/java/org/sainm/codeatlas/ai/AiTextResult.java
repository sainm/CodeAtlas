package org.sainm.codeatlas.ai;

public record AiTextResult(
    boolean success,
    String text,
    String errorMessage
) {
    public AiTextResult {
        text = text == null ? "" : text.trim();
        errorMessage = errorMessage == null ? "" : errorMessage.trim();
    }

    public static AiTextResult success(String text) {
        return new AiTextResult(true, text, "");
    }

    public static AiTextResult failure(String errorMessage) {
        return new AiTextResult(false, "", errorMessage);
    }
}
