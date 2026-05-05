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

public final class JpaEntityFactMapper {
    private static final String ANALYZER_ID = "jpa-entity";

    private JpaEntityFactMapper() {
    }

    public static JpaEntityFactMapper defaults() {
        return new JpaEntityFactMapper();
    }

    public JavaSourceFactBatch map(JpaEntityAnalysisResult jpa, JpaEntityFactContext context) {
        if (jpa == null) {
            throw new IllegalArgumentException("jpa is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (JpaEntityInfo entity : jpa.entities()) {
            addFact(
                    facts,
                    factKeys,
                    evidenceByKey,
                    context,
                    classId(context, entity.qualifiedName()),
                    dbTableId(context, entity),
                    "BINDS_TO",
                    "jpa-entity-table",
                    entity.location());
            for (JpaColumnMappingInfo column : entity.columns()) {
                addFact(
                        facts,
                        factKeys,
                        evidenceByKey,
                        context,
                        fieldId(context, entity.qualifiedName(), column),
                        dbColumnId(context, entity, column.columnName()),
                        "MAPS_TO_COLUMN",
                        "jpa-field-column",
                        column.location());
            }
        }
        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addFact(
            List<FactRecord> facts,
            Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            JpaEntityFactContext context,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String qualifier,
            SourceLocation location) {
        Evidence evidence = Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.JPA);
        FactRecord fact = FactRecord.create(
                List.of(context.javaSourceRootKey()),
                sourceIdentityId,
                targetIdentityId,
                relationName,
                qualifier,
                context.projectId(),
                context.snapshotId(),
                context.analysisRunId(),
                context.scopeRunId(),
                ANALYZER_ID,
                context.scopeKey(),
                evidence.evidenceKey(),
                Confidence.LIKELY,
                90,
                SourceType.JPA);
        if (!factKeys.add(fact.factKey())) {
            return;
        }
        evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
        facts.add(fact);
    }

    private static String classId(JpaEntityFactContext context, String qualifiedName) {
        return "class://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + qualifiedName;
    }

    private static String fieldId(
            JpaEntityFactContext context,
            String ownerQualifiedName,
            JpaColumnMappingInfo column) {
        return "field://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + ownerQualifiedName + "#"
                + column.fieldName() + ":" + column.fieldTypeDescriptor();
    }

    private static String dbTableId(JpaEntityFactContext context, JpaEntityInfo entity) {
        return "db-table://" + context.projectId() + "/" + context.datasourceKey() + "/"
                + schemaName(context, entity) + "/" + entity.tableName();
    }

    private static String dbColumnId(JpaEntityFactContext context, JpaEntityInfo entity, String columnName) {
        return "db-column://" + context.projectId() + "/" + context.datasourceKey() + "/"
                + schemaName(context, entity) + "/" + entity.tableName() + "#" + columnName;
    }

    private static String schemaName(JpaEntityFactContext context, JpaEntityInfo entity) {
        return entity.schemaName().isBlank() ? context.schemaName() : entity.schemaName();
    }
}
