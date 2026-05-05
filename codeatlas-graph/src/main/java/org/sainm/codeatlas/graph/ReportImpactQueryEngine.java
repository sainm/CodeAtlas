package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class ReportImpactQueryEngine {
    private ReportImpactQueryEngine() {
    }

    public static ReportImpactQueryEngine defaults() {
        return new ReportImpactQueryEngine();
    }

    public ReportImpactResult findAffectedReports(CurrentFactReport report, String dbColumnId) {
        requireReport(report);
        requireNonBlank(dbColumnId, "dbColumnId");
        String tableId = dbTableIdFromColumn(dbColumnId);
        List<FactRecord> columnFacts = report.facts().stream()
                .filter(fact -> isColumnOrTableRelation(fact)
                        && (fact.targetIdentityId().equals(dbColumnId)
                                || fact.targetIdentityId().equals(tableId)))
                .toList();
        Set<String> intermediateIds = new LinkedHashSet<>();
        for (FactRecord fact : columnFacts) {
            intermediateIds.add(fact.sourceIdentityId());
        }
        Set<String> reportDefinitionIds = new LinkedHashSet<>();
        Set<List<String>> pathSet = new LinkedHashSet<>();
        for (FactRecord fact : report.facts()) {
            if (isReportFact(fact) && intermediateIds.contains(fact.sourceIdentityId())) {
                reportDefinitionIds.add(fact.targetIdentityId());
                pathSet.add(List.of(dbColumnId, fact.sourceIdentityId(), fact.targetIdentityId()));
            }
        }
        List<ImpactPath> paths = pathSet.stream().map(ImpactPath::new).toList();
        List<FactRecord> reportFacts = report.facts().stream()
                .filter(fact -> reportDefinitionIds.contains(fact.sourceIdentityId())
                        || reportDefinitionIds.contains(fact.targetIdentityId()))
                .toList();
        return new ReportImpactResult(dbColumnId, columnFacts, reportFacts, paths);
    }

    public ReportImpactResult findAffectedColumns(CurrentFactReport report, String reportDefinitionId) {
        requireReport(report);
        requireNonBlank(reportDefinitionId, "reportDefinitionId");
        List<FactRecord> reportFacts = report.facts().stream()
                .filter(fact -> isReportFact(fact) && fact.targetIdentityId().equals(reportDefinitionId))
                .toList();
        Set<String> sourceIds = new LinkedHashSet<>();
        for (FactRecord fact : reportFacts) {
            sourceIds.add(fact.sourceIdentityId());
        }
        List<FactRecord> columnFacts = new ArrayList<>();
        Set<List<String>> pathSet = new LinkedHashSet<>();
        for (FactRecord fact : report.facts()) {
            if (!isColumnOrTableRelation(fact) || !sourceIds.contains(fact.sourceIdentityId())) {
                continue;
            }
            columnFacts.add(fact);
            pathSet.add(List.of(reportDefinitionId, fact.sourceIdentityId(), fact.targetIdentityId()));
        }
        List<ImpactPath> paths = pathSet.stream().map(ImpactPath::new).toList();
        return new ReportImpactResult(reportDefinitionId, columnFacts, reportFacts, paths);
    }

    private static boolean isColumnOrTableRelation(FactRecord fact) {
        return fact.relationType().name().equals("READS_COLUMN")
                || fact.relationType().name().equals("WRITES_COLUMN")
                || fact.relationType().name().equals("MAPS_TO_COLUMN")
                || fact.relationType().name().equals("READS_TABLE")
                || fact.relationType().name().equals("WRITES_TABLE");
    }

    private static boolean isReportFact(FactRecord fact) {
        return fact.relationType().name().equals("EXPORTS_SYMBOL")
                || fact.relationType().name().equals("REFERENCES_SYMBOL");
    }

    private static String dbTableIdFromColumn(String dbColumnId) {
        if (!dbColumnId.startsWith("db-column://")) {
            return dbColumnId;
        }
        String withoutKind = dbColumnId.substring("db-column://".length());
        int hashIndex = withoutKind.lastIndexOf('#');
        return hashIndex >= 0
                ? "db-table://" + withoutKind.substring(0, hashIndex)
                : "db-table://" + withoutKind;
    }

    private static void requireReport(CurrentFactReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    public record ReportImpactResult(
            String queryId,
            List<FactRecord> columnFacts,
            List<FactRecord> reportFacts,
            List<ImpactPath> reversePaths) {
        public ReportImpactResult {
            requireNonBlank(queryId, "queryId");
            columnFacts = List.copyOf(columnFacts == null ? List.of() : columnFacts);
            reportFacts = List.copyOf(reportFacts == null ? List.of() : reportFacts);
            reversePaths = List.copyOf(reversePaths == null ? List.of() : reversePaths);
        }
    }
}
