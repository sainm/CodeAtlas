package org.sainm.codeatlas.graph.impact;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;

public record ImpactReport(
    String reportId,
    String projectId,
    String snapshotId,
    String changeSetId,
    ReportDepth depth,
    Instant createdAt,
    List<ImpactPath> paths,
    List<ImpactEvidence> evidenceList,
    boolean truncated
) {
    public ImpactReport {
        reportId = require(reportId, "reportId");
        projectId = require(projectId, "projectId");
        snapshotId = require(snapshotId, "snapshotId");
        changeSetId = require(changeSetId, "changeSetId");
        depth = depth == null ? ReportDepth.FAST : depth;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        paths = List.copyOf(paths);
        evidenceList = List.copyOf(evidenceList);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public List<ImpactAffectedSymbol> affectedSymbols() {
        Map<String, ImpactAffectedSymbol> affected = new LinkedHashMap<>();
        for (ImpactPath path : paths) {
            for (ImpactPathStep step : path.steps()) {
                ImpactAffectedSymbol symbol = affectedSymbol(step.symbolId());
                affected.putIfAbsent(symbol.category() + "|" + symbol.symbolId().value(), symbol);
            }
        }
        return affected.values().stream()
            .sorted(Comparator
                .comparing(ImpactAffectedSymbol::category)
                .thenComparing(symbol -> symbol.symbolId().value()))
            .toList();
    }

    private ImpactAffectedSymbol affectedSymbol(SymbolId symbolId) {
        return new ImpactAffectedSymbol(category(symbolId), symbolId, displayName(symbolId));
    }

    private String category(SymbolId symbolId) {
        SymbolKind kind = symbolId.kind();
        if (kind == SymbolKind.JSP_PAGE || kind == SymbolKind.JSP_FORM || kind == SymbolKind.JSP_INPUT) {
            return "JSP";
        }
        if (kind == SymbolKind.API_ENDPOINT) {
            return "API";
        }
        if (kind == SymbolKind.ACTION_PATH) {
            return "ACTION";
        }
        if (kind == SymbolKind.SQL_STATEMENT) {
            return "SQL";
        }
        if (kind == SymbolKind.DB_TABLE || kind == SymbolKind.DB_COLUMN) {
            return "TABLE";
        }
        return kind.name();
    }

    private String displayName(SymbolId symbolId) {
        if (symbolId.memberName() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.memberName();
        }
        if (symbolId.localId() != null) {
            return symbolId.ownerQualifiedName() + "#" + symbolId.localId();
        }
        return symbolId.ownerQualifiedName() == null ? symbolId.value() : symbolId.ownerQualifiedName();
    }
}
