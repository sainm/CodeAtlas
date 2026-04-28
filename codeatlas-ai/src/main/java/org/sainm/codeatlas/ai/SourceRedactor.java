package org.sainm.codeatlas.ai;

import java.util.List;
import java.util.regex.Pattern;

public final class SourceRedactor {
    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(password\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(secret\\s*[=:]\\s*)([^\\s\"']+)"),
        Pattern.compile("(?i)(token\\s*[=:]\\s*)([^\\s\"']+)")
    );

    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String redacted = text;
        for (Pattern pattern : SECRET_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("$1[REDACTED]");
        }
        return redacted;
    }

    public String redactAndTrim(String text, int maxCharacters) {
        String redacted = redact(text);
        if (maxCharacters <= 0 || redacted.length() <= maxCharacters) {
            return redacted;
        }
        return redacted.substring(0, maxCharacters) + "...[TRUNCATED]";
    }
}
