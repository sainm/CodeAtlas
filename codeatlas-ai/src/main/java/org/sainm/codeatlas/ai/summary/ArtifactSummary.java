package org.sainm.codeatlas.ai.summary;

import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.List;

public record ArtifactSummary(
    SummaryKind kind,
    SymbolId symbolId,
    String title,
    String text,
    List<String> evidenceKeys
) {
    public ArtifactSummary {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        title = title == null || title.isBlank() ? symbolId.value() : title.trim();
        text = text == null ? "" : text.trim();
        evidenceKeys = List.copyOf(evidenceKeys);
    }
}
