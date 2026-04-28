package org.sainm.codeatlas.ai.summary;

import org.sainm.codeatlas.ai.EvidencePack;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ArtifactSummaryBuilder {
    public List<ArtifactSummary> build(EvidencePack evidencePack) {
        Map<SymbolId, List<GraphFact>> bySource = evidencePack.facts().stream()
            .collect(Collectors.groupingBy(fact -> fact.factKey().source()));
        return bySource.entrySet().stream()
            .map(entry -> summary(entry.getKey(), entry.getValue()))
            .sorted(java.util.Comparator.comparing(summary -> summary.symbolId().value()))
            .toList();
    }

    private ArtifactSummary summary(SymbolId symbolId, List<GraphFact> facts) {
        SummaryKind kind = kind(symbolId.kind());
        String relations = facts.stream()
            .map(fact -> fact.factKey().relationType().name())
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
        String text = "Static evidence count: " + facts.size() + "; relations: " + relations + ".";
        List<String> evidenceKeys = facts.stream()
            .map(fact -> fact.evidenceKey().value())
            .distinct()
            .sorted()
            .toList();
        return new ArtifactSummary(kind, symbolId, title(symbolId), text, evidenceKeys);
    }

    private SummaryKind kind(SymbolKind kind) {
        return switch (kind) {
            case METHOD -> SummaryKind.METHOD;
            case CLASS, INTERFACE, ENUM, ANNOTATION -> SummaryKind.CLASS;
            case JSP_PAGE, JSP_FORM, JSP_INPUT -> SummaryKind.JSP_PAGE;
            case SQL_STATEMENT -> SummaryKind.SQL_STATEMENT;
            default -> SummaryKind.IMPACT_REPORT;
        };
    }

    private String title(SymbolId symbolId) {
        if (symbolId.memberName() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.memberName();
        }
        if (symbolId.ownerQualifiedName() != null) {
            return symbolId.ownerQualifiedName();
        }
        return symbolId.value();
    }
}
