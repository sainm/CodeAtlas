package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.SpoonSymbolMapper;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;

public final class JpaEntityAnalyzer {
    private static final Set<String> ENTITY_ANNOTATIONS = Set.of("Entity");
    private static final Set<String> PERSISTENT_FIELD_ANNOTATIONS = Set.of("Column", "Id", "EmbeddedId", "Basic", "Version", "JoinColumn");

    public JpaEntityAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (CtType<?> type : model.getAllTypes()) {
            if (!hasAnnotation(type.getAnnotations(), ENTITY_ANNOTATIONS)) {
                continue;
            }
            addEntityFacts(scope, projectKey, sourceRootKey, symbols, type, nodes, facts);
        }
        return new JpaEntityAnalysisResult(nodes, facts);
    }

    private void addEntityFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        SpoonSymbolMapper symbols,
        CtType<?> type,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId entityClass = symbols.type(type);
        String tableName = tableName(type);
        SymbolId table = tableSymbol(projectKey, scope.moduleKey(), tableName);
        nodes.add(GraphNodeFactory.classNode(entityClass, NodeRole.CODE_TYPE));
        nodes.add(GraphNodeFactory.tableNode(table));
        facts.add(fact(scope, entityClass, RelationType.BINDS_TO, table, type.getPosition(), sourceRootKey, "jpa-entity-table:" + tableName, Confidence.LIKELY));

        for (CtField<?> field : type.getFields()) {
            if (!persistentField(field)) {
                continue;
            }
            String columnName = columnName(field);
            SymbolId fieldSymbol = symbols.field(field);
            SymbolId column = SymbolId.logicalPath(SymbolKind.DB_COLUMN, projectKey, scope.moduleKey(), "_database", tableName, columnName);
            nodes.add(GraphNodeFactory.fieldNode(fieldSymbol, NodeRole.CODE_MEMBER));
            nodes.add(GraphNodeFactory.tableNode(column));
            facts.add(fact(
                scope,
                fieldSymbol,
                RelationType.BINDS_TO,
                column,
                field.getPosition(),
                sourceRootKey,
                "jpa-field-column:" + tableName + "." + columnName,
                columnConfidence(field)
            ));
        }
    }

    private boolean persistentField(CtField<?> field) {
        if (field.getModifiers().contains(ModifierKind.STATIC) || field.getModifiers().contains(ModifierKind.TRANSIENT)) {
            return false;
        }
        if (hasAnnotation(field.getAnnotations(), Set.of("Transient"))) {
            return false;
        }
        return true;
    }

    private Confidence columnConfidence(CtField<?> field) {
        return hasAnnotation(field.getAnnotations(), PERSISTENT_FIELD_ANNOTATIONS) ? Confidence.LIKELY : Confidence.POSSIBLE;
    }

    private String tableName(CtType<?> type) {
        return annotationValue(type.getAnnotations(), "Table", "name")
            .or(() -> annotationValue(type.getAnnotations(), "Table", "value"))
            .orElseGet(type::getSimpleName);
    }

    private String columnName(CtField<?> field) {
        return annotationValue(field.getAnnotations(), "Column", "name")
            .or(() -> annotationValue(field.getAnnotations(), "Column", "value"))
            .or(() -> annotationValue(field.getAnnotations(), "JoinColumn", "name"))
            .orElseGet(field::getSimpleName);
    }

    private boolean hasAnnotation(List<CtAnnotation<?>> annotations, Set<String> names) {
        return annotations.stream()
            .map(this::annotationName)
            .anyMatch(names::contains);
    }

    private Optional<String> annotationValue(List<CtAnnotation<?>> annotations, String annotationName, String key) {
        for (CtAnnotation<?> annotation : annotations) {
            if (!annotationName(annotation).equals(annotationName)) {
                continue;
            }
            Map<String, spoon.reflect.code.CtExpression> values = annotation.getValues();
            spoon.reflect.code.CtExpression expression = values.get(key);
            if (expression == null && "value".equals(key)) {
                expression = values.get("");
            }
            String value = expressionValue(expression);
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private String expressionValue(spoon.reflect.code.CtExpression expression) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() != null) {
            return literal.getValue().toString().trim();
        }
        String value = expression == null ? "" : expression.toString().trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String annotationName(CtAnnotation<?> annotation) {
        if (annotation.getAnnotationType() == null) {
            return "";
        }
        String qualifiedName = annotation.getAnnotationType().getQualifiedName();
        int lastDot = qualifiedName == null ? -1 : qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? annotation.getAnnotationType().getSimpleName() : qualifiedName.substring(lastDot + 1);
    }

    private SymbolId tableSymbol(String projectKey, String moduleKey, String tableName) {
        return SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, moduleKey, "_database", tableName, null);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        SourcePosition position,
        String sourceRootKey,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            evidence(position, sourceRootKey, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.SPOON
        );
    }

    private EvidenceKey evidence(SourcePosition position, String sourceRootKey, String qualifier) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "jpa-entity", "_unknown", 0, 0, qualifier);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "jpa-entity",
            position.getFile().toPath().toString(),
            line(position),
            Math.max(line(position), position.getEndLine()),
            sourceRootKey + ":" + qualifier
        );
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }
}
