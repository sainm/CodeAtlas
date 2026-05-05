package org.sainm.codeatlas.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class DbImpactQueryEngine {
    private DbImpactQueryEngine() {
    }

    public static DbImpactQueryEngine defaults() {
        return new DbImpactQueryEngine();
    }

    public DbImpactQueryResult tableImpact(CurrentFactReport report, String dbTableId) {
        requireReport(report);
        requireNonBlank(dbTableId, "dbTableId");
        String columnPrefix = dbTableId.replace("db-table://", "db-column://") + "#";
        List<FactRecord> databaseFacts = report.facts().stream()
                .filter(DbImpactQueryEngine::isDatabaseImpactFact)
                .filter(fact -> fact.targetIdentityId().equals(dbTableId)
                        || fact.targetIdentityId().startsWith(columnPrefix))
                .toList();
        return new DbImpactQueryResult(dbTableId, databaseFacts, upstreamBindingFacts(report, databaseFacts));
    }

    public DbImpactQueryResult columnImpact(CurrentFactReport report, String dbColumnId) {
        requireReport(report);
        requireNonBlank(dbColumnId, "dbColumnId");
        List<FactRecord> databaseFacts = report.facts().stream()
                .filter(fact -> fact.targetIdentityId().equals(dbColumnId))
                .filter(fact -> fact.relationType().name().equals("READS_COLUMN")
                        || fact.relationType().name().equals("WRITES_COLUMN")
                        || fact.relationType().name().equals("MAPS_TO_COLUMN"))
                .toList();
        return new DbImpactQueryResult(dbColumnId, databaseFacts, upstreamBindingFacts(report, databaseFacts));
    }

    private static List<FactRecord> upstreamBindingFacts(
            CurrentFactReport report,
            List<FactRecord> databaseFacts) {
        Set<String> sqlStatementIds = new LinkedHashSet<>();
        for (FactRecord fact : databaseFacts) {
            if (fact.sourceIdentityId().startsWith("sql-statement://")) {
                sqlStatementIds.add(fact.sourceIdentityId());
            }
        }
        return report.facts().stream()
                .filter(fact -> fact.relationType().name().equals("BINDS_TO"))
                .filter(fact -> sqlStatementIds.contains(fact.targetIdentityId()))
                .toList();
    }

    private static boolean isDatabaseImpactFact(FactRecord fact) {
        return switch (fact.relationType().name()) {
            case "READS_TABLE", "WRITES_TABLE", "READS_COLUMN", "WRITES_COLUMN", "MAPS_TO_COLUMN" -> true;
            default -> false;
        };
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
}
