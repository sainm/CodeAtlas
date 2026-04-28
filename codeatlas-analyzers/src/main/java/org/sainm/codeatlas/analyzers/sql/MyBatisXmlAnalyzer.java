package org.sainm.codeatlas.analyzers.sql;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class MyBatisXmlAnalyzer {
    private static final List<String> COMMANDS = List.of("select", "insert", "update", "delete");

    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();
    private final SimpleSqlTableExtractor tableExtractor = new SimpleSqlTableExtractor();

    public MyBatisXmlAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path mapperXml) {
        return analyze(scope, projectKey, sourceRootKey, "src/main/java", mapperXml);
    }

    public MyBatisXmlAnalysisResult analyze(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        String mapperSourceRootKey,
        Path mapperXml
    ) {
        try {
            Document document = xmlLoader.load(mapperXml);
            Element mapper = document.getDocumentElement();
            String namespace = mapper.getAttribute("namespace");

            List<MyBatisStatement> statements = new ArrayList<>();
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();
            for (String command : COMMANDS) {
                NodeList elements = mapper.getElementsByTagName(command);
                for (int i = 0; i < elements.getLength(); i++) {
                    Element element = (Element) elements.item(i);
                    MyBatisStatement statement = statement(namespace, command, element);
                    statements.add(statement);
                    addGraphFacts(scope, projectKey, sourceRootKey, mapperSourceRootKey, mapperXml, statement, nodes, facts);
                }
            }
            return new MyBatisXmlAnalysisResult(statements, nodes, facts);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to analyze MyBatis XML: " + mapperXml, exception);
        }
    }

    private MyBatisStatement statement(String namespace, String command, Element element) {
        String id = element.getAttribute("id");
        String sql = element.getTextContent();
        List<SqlTableAccess> tableAccesses = tableExtractor.extract(command, sql);
        return new MyBatisStatement(namespace, id, command, sql, 0, hasDynamicChildren(element), tableAccesses);
    }

    private void addGraphFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        String mapperSourceRootKey,
        Path mapperXml,
        MyBatisStatement statement,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId mapperMethod = SymbolId.method(projectKey, scope.moduleKey(), mapperSourceRootKey, statement.namespace(), statement.id(), "_unknown");
        SymbolId sqlStatement = SymbolId.logicalPath(
            SymbolKind.SQL_STATEMENT,
            projectKey,
            scope.moduleKey(),
            sourceRootKey,
            mapperXml.toString(),
            statement.namespace() + "." + statement.id()
        );
        nodes.add(GraphNodeFactory.methodNode(mapperMethod, org.sainm.codeatlas.graph.model.NodeRole.MAPPER));
        nodes.add(GraphNodeFactory.sqlNode(sqlStatement));
        facts.add(fact(scope, mapperMethod, RelationType.BINDS_TO, sqlStatement, mapperXml, "mybatis-statement", Confidence.CERTAIN));

        for (SqlTableAccess access : statement.tableAccesses()) {
            SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, scope.moduleKey(), "_database", access.tableName(), null);
            nodes.add(GraphNodeFactory.tableNode(table));
            RelationType relationType = access.accessType() == SqlAccessType.WRITE ? RelationType.WRITES_TABLE : RelationType.READS_TABLE;
            Confidence confidence = statement.dynamic() ? Confidence.POSSIBLE : Confidence.LIKELY;
            facts.add(fact(scope, sqlStatement, relationType, table, mapperXml, access.tableName(), confidence));
            for (String columnName : access.columnNames()) {
                SymbolId column = SymbolId.logicalPath(SymbolKind.DB_COLUMN, projectKey, scope.moduleKey(), "_database", access.tableName(), columnName);
                nodes.add(GraphNodeFactory.tableNode(column));
                facts.add(fact(scope, sqlStatement, relationType, column, mapperXml, access.tableName() + "." + columnName, confidence));
            }
        }
    }

    private boolean hasDynamicChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path mapperXml,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.XML, "mybatis-xml", mapperXml.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.XML
        );
    }
}
