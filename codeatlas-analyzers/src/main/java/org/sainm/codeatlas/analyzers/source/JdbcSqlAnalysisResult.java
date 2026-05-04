package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record JdbcSqlAnalysisResult(
        List<JdbcSqlStatementInfo> statements,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public JdbcSqlAnalysisResult {
        statements = List.copyOf(statements == null ? List.of() : statements);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<SqlStatementSourceInfo> sqlStatementSources() {
        return statements.stream()
                .map(statement -> new SqlStatementSourceInfo(
                        statement.statementId(),
                        statement.sql(),
                        statement.location()))
                .toList();
    }
}
