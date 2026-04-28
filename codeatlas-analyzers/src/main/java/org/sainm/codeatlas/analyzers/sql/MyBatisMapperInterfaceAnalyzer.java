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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public final class MyBatisMapperInterfaceAnalyzer {
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Set<String> SQL_ANNOTATIONS = Set.of("Select", "Insert", "Update", "Delete");
    private static final Set<String> PROVIDER_ANNOTATIONS = Set.of("SelectProvider", "InsertProvider", "UpdateProvider", "DeleteProvider");
    private final SimpleSqlTableExtractor tableExtractor = new SimpleSqlTableExtractor();

    public MyBatisMapperInterfaceAnalysisResult analyze(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        List<Path> sourceFiles
    ) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<MyBatisMapperMethod> methods = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        for (CtType<?> type : model.getAllTypes()) {
            if (!isMapperInterface(type)) {
                continue;
            }
            SymbolId mapperType = symbols.type(type);
            nodes.add(GraphNodeFactory.classNode(mapperType, NodeRole.MAPPER));

            Map<String, Long> methodNameCounts = methodNameCounts(type);
            for (CtMethod<?> method : type.getMethods()) {
                SymbolId exactMethod = symbols.method(method);
                SymbolId statementMethod = SymbolId.method(
                    projectKey,
                    scope.moduleKey(),
                    sourceRootKey,
                    type.getQualifiedName(),
                    method.getSimpleName(),
                    "_unknown"
                );
                Confidence bridgeConfidence = methodNameCounts.getOrDefault(method.getSimpleName(), 0L) == 1
                    ? Confidence.CERTAIN
                    : Confidence.LIKELY;
                nodes.add(GraphNodeFactory.methodNode(exactMethod, NodeRole.MAPPER));
                nodes.add(GraphNodeFactory.methodNode(statementMethod, NodeRole.MAPPER));
                facts.add(fact(scope, mapperType, RelationType.DECLARES, exactMethod, method.getPosition(), "mapper-method", Confidence.CERTAIN));
                facts.add(fact(scope, exactMethod, RelationType.BRIDGES_TO, statementMethod, method.getPosition(), "mybatis-statement-id", bridgeConfidence));

                Optional<SqlAnnotation> sqlAnnotation = sqlAnnotation(method);
                sqlAnnotation.ifPresent(annotation -> addAnnotationSqlFacts(
                    scope,
                    projectKey,
                    sourceRootKey,
                    method,
                    exactMethod,
                    annotation,
                    nodes,
                    facts
                ));
                methods.add(new MyBatisMapperMethod(
                    type.getQualifiedName(),
                    method.getSimpleName(),
                    exactMethod.descriptor(),
                    sqlAnnotation.isPresent(),
                    bridgeConfidence,
                    line(method.getPosition())
                ));
            }
        }

        return new MyBatisMapperInterfaceAnalysisResult(methods, nodes, facts);
    }

    private boolean isMapperInterface(CtType<?> type) {
        if (!type.isInterface()) {
            return false;
        }
        if (type.getQualifiedName().endsWith("Mapper")) {
            return true;
        }
        if (type.getAnnotations().stream().map(this::annotationName).anyMatch(name -> name.equals("Mapper"))) {
            return true;
        }
        return type.getMethods().stream().anyMatch(method -> sqlAnnotation(method).isPresent() || hasProviderAnnotation(method));
    }

    private Map<String, Long> methodNameCounts(CtType<?> type) {
        Map<String, Long> counts = new HashMap<>();
        for (CtMethod<?> method : type.getMethods()) {
            counts.merge(method.getSimpleName(), 1L, Long::sum);
        }
        return counts;
    }

    private Optional<SqlAnnotation> sqlAnnotation(CtMethod<?> method) {
        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            String name = annotationName(annotation);
            if (SQL_ANNOTATIONS.contains(name)) {
                Matcher matcher = STRING_LITERAL.matcher(annotation.toString());
                if (matcher.find()) {
                    return Optional.of(new SqlAnnotation(name.toLowerCase(), matcher.group(1)));
                }
            }
        }
        return Optional.empty();
    }

    private boolean hasProviderAnnotation(CtMethod<?> method) {
        return method.getAnnotations().stream()
            .map(this::annotationName)
            .anyMatch(PROVIDER_ANNOTATIONS::contains);
    }

    private void addAnnotationSqlFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        CtMethod<?> method,
        SymbolId exactMethod,
        SqlAnnotation sqlAnnotation,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId sqlStatement = SymbolId.logicalPath(
            SymbolKind.SQL_STATEMENT,
            projectKey,
            scope.moduleKey(),
            sourceRootKey,
            method.getDeclaringType().getQualifiedName(),
            method.getSimpleName() + ":" + sqlAnnotation.command()
        );
        nodes.add(GraphNodeFactory.sqlNode(sqlStatement));
        facts.add(fact(scope, exactMethod, RelationType.BINDS_TO, sqlStatement, method.getPosition(), "mybatis-annotation", Confidence.CERTAIN));

        for (SqlTableAccess access : tableExtractor.extract(sqlAnnotation.command(), sqlAnnotation.sql())) {
            SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, scope.moduleKey(), "_database", access.tableName(), null);
            nodes.add(GraphNodeFactory.tableNode(table));
            RelationType relationType = access.accessType() == SqlAccessType.WRITE ? RelationType.WRITES_TABLE : RelationType.READS_TABLE;
            facts.add(fact(scope, sqlStatement, relationType, table, method.getPosition(), access.tableName(), Confidence.LIKELY));
            for (String columnName : access.columnNames()) {
                SymbolId column = SymbolId.logicalPath(SymbolKind.DB_COLUMN, projectKey, scope.moduleKey(), "_database", access.tableName(), columnName);
                nodes.add(GraphNodeFactory.tableNode(column));
                facts.add(fact(scope, sqlStatement, relationType, column, method.getPosition(), access.tableName() + "." + columnName, Confidence.LIKELY));
            }
        }
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        SourcePosition position,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            evidence(position, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.SPOON
        );
    }

    private EvidenceKey evidence(SourcePosition position, String qualifier) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "mybatis-mapper-interface", "_unknown", 0, 0, qualifier);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "mybatis-mapper-interface",
            position.getFile().toPath().toString(),
            line(position),
            Math.max(line(position), position.getEndLine()),
            qualifier
        );
    }

    private String annotationName(CtAnnotation<?> annotation) {
        if (annotation.getAnnotationType() == null) {
            return "";
        }
        return annotation.getAnnotationType().getSimpleName();
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }

    private record SqlAnnotation(String command, String sql) {
    }
}
