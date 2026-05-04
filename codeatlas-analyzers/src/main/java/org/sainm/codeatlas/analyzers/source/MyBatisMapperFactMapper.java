package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.Evidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

public final class MyBatisMapperFactMapper {
    private static final String ANALYZER_ID = "mybatis";

    private MyBatisMapperFactMapper() {
    }

    public static MyBatisMapperFactMapper defaults() {
        return new MyBatisMapperFactMapper();
    }

    public JavaSourceFactBatch map(
            MyBatisMapperAnalysisResult mappers,
            MyBatisXmlAnalysisResult xml,
            MyBatisFactContext context) {
        if (mappers == null) {
            throw new IllegalArgumentException("mappers is required");
        }
        if (xml == null) {
            throw new IllegalArgumentException("xml is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        Map<String, List<MyBatisMapperMethodInfo>> methodsByStatementKey = methodsByStatementKey(mappers.methods());
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (MyBatisXmlStatementInfo statement : xml.statements()) {
            List<MyBatisMapperMethodInfo> methods = methodsByStatementKey.get(statementKey(statement.namespace(), statement.id()));
            if (methods == null || methods.size() != 1) {
                continue;
            }
            addBindingFact(facts, factKeys, evidenceByKey, context, methods.get(0), statement);
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static Map<String, List<MyBatisMapperMethodInfo>> methodsByStatementKey(
            List<MyBatisMapperMethodInfo> methods) {
        Map<String, List<MyBatisMapperMethodInfo>> result = new HashMap<>();
        for (MyBatisMapperMethodInfo method : methods) {
            result.computeIfAbsent(statementKey(method.ownerQualifiedName(), method.simpleName()), ignored -> new ArrayList<>())
                    .add(method);
        }
        return result;
    }

    private static void addBindingFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            MyBatisFactContext context,
            MyBatisMapperMethodInfo method,
            MyBatisXmlStatementInfo statement) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                statement.location().relativePath(),
                "line:" + statement.location().line(),
                1,
                SourceType.XML);
        FactRecord fact = FactRecord.create(
                List.of(context.javaSourceRootKey(), context.resourceSourceRootKey()),
                methodId(context, method),
                sqlStatementId(context, statement),
                "BINDS_TO",
                "mybatis-mapper-statement",
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.CERTAIN,
                100,
                SourceType.XML);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String methodId(MyBatisFactContext context, MyBatisMapperMethodInfo method) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + method.ownerQualifiedName() + "#"
                + method.simpleName() + method.signature();
    }

    private static String sqlStatementId(MyBatisFactContext context, MyBatisXmlStatementInfo statement) {
        return "sql-statement://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.resourceSourceRootKey() + "/" + stripSourceRoot(context.resourceSourceRootKey(), statement.path())
                + "#" + statement.namespace() + "." + statement.id();
    }

    private static String statementKey(String namespace, String id) {
        return namespace + "#" + id;
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
