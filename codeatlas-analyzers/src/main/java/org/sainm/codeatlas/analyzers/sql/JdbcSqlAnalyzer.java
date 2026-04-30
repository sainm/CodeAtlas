package org.sainm.codeatlas.analyzers.sql;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public final class JdbcSqlAnalyzer {
    private static final List<String> JDBC_METHODS = List.of(
        "prepareStatement",
        "prepareCall",
        "executeQuery",
        "executeUpdate",
        "execute",
        "addBatch"
    );

    private final JSqlParserTableExtractor tableExtractor = new JSqlParserTableExtractor();

    public JdbcSqlAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
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
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            analyzeMethod(scope, projectKey, sourceRootKey, symbols, method, nodes, facts);
        }
        return new JdbcSqlAnalysisResult(nodes, facts);
    }

    private void analyzeMethod(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        SpoonSymbolMapper symbols,
        CtMethod<?> method,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        Map<String, SqlText> stringConstants = stringConstants(method);
        SymbolId methodSymbol = symbols.method(method);
        int index = 0;
        for (CtInvocation<?> invocation : method.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() == null || !JDBC_METHODS.contains(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (invocation.getArguments().isEmpty()) {
                continue;
            }
            Optional<SqlText> sqlText = sqlText(invocation.getArguments().getFirst(), stringConstants);
            if (sqlText.isEmpty()) {
                continue;
            }
            String command = commandType(sqlText.get().text());
            if (command == null) {
                continue;
            }
            addSqlFacts(scope, projectKey, sourceRootKey, methodSymbol, invocation, sqlText.get(), command, index++, nodes, facts);
        }
    }

    private Map<String, SqlText> stringConstants(CtMethod<?> method) {
        Map<String, SqlText> constants = new HashMap<>();
        for (CtLocalVariable<?> localVariable : method.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (localVariable.getSimpleName() == null || localVariable.getDefaultExpression() == null) {
                continue;
            }
            sqlText(localVariable.getDefaultExpression(), constants)
                .ifPresent(text -> constants.put(localVariable.getSimpleName(), text));
        }
        return constants;
    }

    private Optional<SqlText> sqlText(CtExpression<?> expression, Map<String, SqlText> stringConstants) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String text) {
            return Optional.of(new SqlText(text, false));
        }
        if (expression instanceof CtVariableRead<?> variableRead && variableRead.getVariable() != null) {
            return Optional.ofNullable(stringConstants.get(variableRead.getVariable().getSimpleName()));
        }
        if (expression instanceof CtBinaryOperator<?> binaryOperator && binaryOperator.getKind() == BinaryOperatorKind.PLUS) {
            Optional<SqlText> left = sqlText(binaryOperator.getLeftHandOperand(), stringConstants);
            Optional<SqlText> right = sqlText(binaryOperator.getRightHandOperand(), stringConstants);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(new SqlText(left.get().text() + right.get().text(), left.get().dynamic() || right.get().dynamic()));
            }
            if (left.isPresent() && commandType(left.get().text()) != null) {
                return Optional.of(new SqlText(left.get().text(), true));
            }
            if (right.isPresent() && commandType(right.get().text()) != null) {
                return Optional.of(new SqlText(right.get().text(), true));
            }
        }
        return Optional.empty();
    }

    private void addSqlFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        SymbolId methodSymbol,
        CtInvocation<?> invocation,
        SqlText sqlText,
        String command,
        int index,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SourcePosition position = invocation.getPosition();
        SymbolId sqlStatement = SymbolId.logicalPath(
            SymbolKind.SQL_STATEMENT,
            projectKey,
            scope.moduleKey(),
            sourceRootKey,
            methodSymbol.value(),
            "jdbc:" + line(position) + ":" + index
        );
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
        nodes.add(GraphNodeFactory.sqlNode(sqlStatement));
        Confidence confidence = sqlText.dynamic() ? Confidence.POSSIBLE : Confidence.LIKELY;
        facts.add(fact(scope, methodSymbol, RelationType.BINDS_TO, sqlStatement, position, "jdbc-sql:" + command, confidence));

        for (SqlTableAccess access : tableExtractor.extract(command, sqlText.text())) {
            SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, scope.moduleKey(), "_database", access.tableName(), null);
            nodes.add(GraphNodeFactory.tableNode(table));
            RelationType relationType = access.accessType() == SqlAccessType.WRITE ? RelationType.WRITES_TABLE : RelationType.READS_TABLE;
            facts.add(fact(scope, sqlStatement, relationType, table, position, "jdbc-table:" + access.tableName(), confidence));
            for (String columnName : access.columnNames()) {
                SymbolId column = SymbolId.logicalPath(SymbolKind.DB_COLUMN, projectKey, scope.moduleKey(), "_database", access.tableName(), columnName);
                nodes.add(GraphNodeFactory.tableNode(column));
                facts.add(fact(scope, sqlStatement, relationType, column, position, "jdbc-column:" + access.tableName() + "." + columnName, confidence));
            }
        }
    }

    private String commandType(String sql) {
        String trimmed = sql == null ? "" : sql.stripLeading().toLowerCase(Locale.ROOT);
        for (String command : List.of("select", "insert", "update", "delete")) {
            if (trimmed.startsWith(command + " ") || trimmed.startsWith(command + "\n") || trimmed.equals(command)) {
                return command;
            }
        }
        return null;
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
            return new EvidenceKey(SourceType.SPOON, "jdbc-sql", "_unknown", 0, 0, qualifier);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "jdbc-sql",
            position.getFile().toPath().toString(),
            line(position),
            Math.max(line(position), position.getEndLine()),
            qualifier
        );
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }

    private record SqlText(String text, boolean dynamic) {
    }
}
