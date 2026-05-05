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

public final class JdbcSqlFactMapper {
    private static final String ANALYZER_ID = "jdbc-sql";

    private JdbcSqlFactMapper() {
    }

    public static JdbcSqlFactMapper defaults() {
        return new JdbcSqlFactMapper();
    }

    public JavaSourceFactBatch map(JdbcSqlAnalysisResult jdbc, JdbcSqlFactContext context) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (JdbcSqlStatementInfo statement : jdbc.statements()) {
            if (statement.ownerQualifiedName().isBlank()
                    || statement.ownerMethodName().isBlank()
                    || statement.ownerSignature().isBlank()) {
                continue;
            }
            addFact(facts, factKeys, evidenceByKey, context, statement);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JdbcSqlFactContext context,
            JdbcSqlStatementInfo statement) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                statement.location().relativePath(),
                "line:" + statement.location().line(),
                1,
                SourceType.SQL);
        FactRecord fact = FactRecord.create(
                List.of(context.javaSourceRootKey()),
                methodId(context, statement),
                sqlStatementId(context, statement),
                "BINDS_TO",
                "jdbc-sql-statement",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SQL);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
        for (JdbcSqlParameterBindingInfo parameter : statement.parameters()) {
            addParameterFact(facts, factKeys, evidenceByKey, context, statement, parameter);
        }
    }

    private static void addParameterFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JdbcSqlFactContext context,
            JdbcSqlStatementInfo statement,
            JdbcSqlParameterBindingInfo parameter) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                parameter.location().relativePath(),
                "line:" + parameter.location().line(),
                1,
                SourceType.SQL);
        FactRecord fact = FactRecord.create(
                List.of(context.javaSourceRootKey()),
                sqlStatementId(context, statement),
                sqlParameterId(context, statement, parameter),
                "HAS_PARAM",
                parameter.binderMethodName(),
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.SQL);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String methodId(JdbcSqlFactContext context, JdbcSqlStatementInfo statement) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + statement.ownerQualifiedName() + "#"
                + statement.ownerMethodName() + statement.ownerSignature();
    }

    private static String sqlStatementId(JdbcSqlFactContext context, JdbcSqlStatementInfo statement) {
        return "sql-statement://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + stripSourceRoot(context.javaSourceRootKey(), statement.location().relativePath())
                + "#" + statement.statementId();
    }

    private static String sqlParameterId(
            JdbcSqlFactContext context,
            JdbcSqlStatementInfo statement,
            JdbcSqlParameterBindingInfo parameter) {
        return "sql-param://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + stripSourceRoot(context.javaSourceRootKey(), statement.location().relativePath())
                + "#" + statement.statementId() + ":param[" + parameter.index() + "]";
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
