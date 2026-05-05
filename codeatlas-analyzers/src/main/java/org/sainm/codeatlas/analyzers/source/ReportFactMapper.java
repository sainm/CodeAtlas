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

/**
 * Converts {@link ReportDefinitionParseResult} entries into {@link FactRecord} facts
 * and {@link Evidence} records for the CodeAtlas graph.
 *
 * <p>Emits:
 * <ul>
 *   <li>{@code ReportDefinition -[:CONTAINS]-> ReportField}</li>
 *   <li>{@code ReportDefinition -[:READS_TABLE|WRITES_TABLE]-> DbTable}</li>
 *   <li>{@code ReportDefinition -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn}</li>
 *   <li>{@code ReportField -[:MAPS_TO_COLUMN]-> DbColumn}</li>
 *   <li>{@code ReportDefinition -[:USES_CONFIG]-> ConfigKey}</li>
 * </ul>
 */
public final class ReportFactMapper {
    private static final String ANALYZER_ID = "report-definition";

    private ReportFactMapper() {
    }

    public static ReportFactMapper defaults() {
        return new ReportFactMapper();
    }

    public JavaSourceFactBatch map(ReportDefinitionParseResult parseResult,
                                    ReportPluginAdapter adapter,
                                    ReportFactContext context) {
        if (parseResult == null) {
            throw new IllegalArgumentException("parseResult is required");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("adapter is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> facts = new ArrayList<>();
        Set<String> factKeys = new HashSet<>();
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();

        String reportId = reportDefinitionId(context, parseResult.reportName());
        String reportName = parseResult.reportName();
        String qualifier = adapter.formatName();

        for (ReportFieldDefinition field : parseResult.fields()) {
            String fieldId = reportFieldId(context, reportName, field.fieldName());
            // ReportDefinition -[:CONTAINS]-> ReportField
            addFact(facts, factKeys, evidenceByKey, context, qualifier,
                    reportId, fieldId, "CONTAINS", field.location(), "report-field");
            // ReportField -[:MAPS_TO_COLUMN]-> DbColumn
            if (!field.sourceColumn().isBlank()) {
                String tableName = field.sourceTable().isBlank() ? "unknown" : field.sourceTable();
                String columnId = dbColumnId(context, tableName, field.sourceColumn());
                addFact(facts, factKeys, evidenceByKey, context, qualifier,
                        fieldId, columnId, "MAPS_TO_COLUMN", field.location(), "report-field-column");
            }
            // ReportDefinition -[:READS_TABLE]-> DbTable
            if (!field.sourceTable().isBlank()) {
                String tableId = dbTableId(context, field.sourceTable());
                addFact(facts, factKeys, evidenceByKey, context, qualifier,
                        reportId, tableId, "READS_TABLE", field.location(), "report-table");
            }
            // ReportDefinition -[:READS_COLUMN]-> DbColumn
            if (!field.sourceColumn().isBlank()) {
                String tableName = field.sourceTable().isBlank() ? "unknown" : field.sourceTable();
                String columnId = dbColumnId(context, tableName, field.sourceColumn());
                addFact(facts, factKeys, evidenceByKey, context, qualifier,
                        reportId, columnId, "READS_COLUMN", field.location(), "report-column-read");
            }
        }

        for (ReportParameterDefinition param : parseResult.parameters()) {
            String paramId = reportParameterId(context, reportName, param.parameterName());
            addFact(facts, factKeys, evidenceByKey, context, qualifier,
                    reportId, paramId, "CONTAINS", param.location(), "report-parameter");
        }

        return new JavaSourceFactBatch(facts, List.copyOf(evidenceByKey.values()));
    }

    private static void addFact(
            List<FactRecord> facts, Set<String> factKeys,
            Map<String, Evidence> evidenceByKey,
            ReportFactContext context, String qualifier,
            String sourceId, String targetId, String relation,
            SourceLocation location, String evidenceSubType) {
        Evidence evidence = createEvidence(context, location, evidenceSubType);
        FactRecord fact = contextFact(context, qualifier,
                sourceId, targetId, relation, evidence.evidenceKey());
        if (factKeys.add(fact.factKey())) {
            evidenceByKey.putIfAbsent(evidence.evidenceKey(), evidence);
            facts.add(fact);
        }
    }

    private static FactRecord contextFact(ReportFactContext ctx, String qualifier,
                                           String sourceId, String targetId,
                                           String relation, String evidenceKey) {
        return FactRecord.create(
                List.of(ctx.sourceRootKey()),
                sourceId, targetId, relation, qualifier,
                ctx.projectId(), ctx.snapshotId(),
                ctx.analysisRunId(), ctx.scopeRunId(),
                ANALYZER_ID, ctx.scopeKey(),
                evidenceKey, Confidence.POSSIBLE, 50, SourceType.XML);
    }

    private static Evidence createEvidence(ReportFactContext context,
                                            SourceLocation location,
                                            String subType) {
        return Evidence.create(
                ANALYZER_ID,
                context.scopeKey(),
                location.relativePath(),
                "line:" + location.line(),
                1,
                SourceType.XML);
    }

    // -- Identity helpers

    static String reportDefinitionId(ReportFactContext context, String reportName) {
        return "report-definition://" + context.projectId() + "/" + context.moduleKey()
                + "/" + context.sourceRootKey() + "/" + safeName(reportName);
    }

    static String reportFieldId(ReportFactContext context, String reportName, String fieldName) {
        return "report-field://" + context.projectId() + "/" + context.moduleKey()
                + "/" + context.sourceRootKey() + "/" + safeName(reportName) + "#" + safeName(fieldName);
    }

    static String reportParameterId(ReportFactContext context, String reportName, String paramName) {
        return "report-parameter://" + context.projectId() + "/" + context.moduleKey()
                + "/" + context.sourceRootKey() + "/" + safeName(reportName) + "#" + safeName(paramName);
    }

    static String dbTableId(ReportFactContext context, String tableName) {
        return "db-table://" + context.projectId() + "/default/default/" + safeName(tableName);
    }

    static String dbColumnId(ReportFactContext context, String tableName, String columnName) {
        return "db-column://" + context.projectId() + "/default/default/"
                + safeName(tableName) + "#" + safeName(columnName);
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.replace(' ', '_').replace('/', '_');
    }
}
