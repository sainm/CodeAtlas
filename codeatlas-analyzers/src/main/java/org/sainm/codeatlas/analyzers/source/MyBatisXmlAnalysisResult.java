package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record MyBatisXmlAnalysisResult(
        List<MyBatisXmlMapperInfo> mappers,
        List<MyBatisXmlStatementInfo> statements,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public MyBatisXmlAnalysisResult {
        mappers = List.copyOf(mappers == null ? List.of() : mappers);
        statements = List.copyOf(statements == null ? List.of() : statements);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<SqlStatementSourceInfo> sqlStatementSources() {
        return statements.stream()
                .filter(statement -> !statement.sql().isBlank())
                .map(statement -> new SqlStatementSourceInfo(
                        statement.namespace() + "." + statement.id(),
                        statement.sql(),
                        statement.location()))
                .toList();
    }
}
