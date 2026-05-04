package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.Evidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

public final class SqlTableFactMapper {
    private static final String ANALYZER_ID = "sql-table";

    private SqlTableFactMapper() {
    }

    public static SqlTableFactMapper defaults() {
        return new SqlTableFactMapper();
    }

    public JavaSourceFactBatch map(SqlTableAnalysisResult tables, SqlTableFactContext context) {
        if (tables == null) {
            throw new IllegalArgumentException("tables is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (SqlTableAccessInfo access : tables.tableAccesses()) {
            addFact(facts, factKeys, evidenceByKey, context, access);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            SqlTableFactContext context,
            SqlTableAccessInfo access) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                access.location().relativePath(),
                "line:" + access.location().line(),
                1,
                SourceType.SQL);
        FactRecord fact = FactRecord.create(
                List.of(context.sourceRootKey()),
                sqlStatementId(context, access),
                dbTableId(context, access.tableName()),
                relationName(access.kind()),
                access.conservativeFallback() ? "sql-table-conservative-fallback" : "sql-table",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                access.conservativeFallback() ? Confidence.POSSIBLE : Confidence.CERTAIN,
                access.conservativeFallback() ? 70 : 100,
                SourceType.SQL);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String relationName(SqlTableAccessKind kind) {
        return switch (kind) {
            case READ -> "READS_TABLE";
            case WRITE -> "WRITES_TABLE";
        };
    }

    private static String sqlStatementId(SqlTableFactContext context, SqlTableAccessInfo access) {
        return "sql-statement://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.sourceRootKey() + "/" + stripSourceRoot(context.sourceRootKey(), access.location().relativePath())
                + "#" + access.statementId();
    }

    private static String dbTableId(SqlTableFactContext context, String tableName) {
        return "db-table://" + context.projectId() + "/" + context.datasourceKey() + "/"
                + context.schemaName() + "/" + tableName;
    }

    private static String stripSourceRoot(String sourceRoot, String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        String prefix = sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }
}
