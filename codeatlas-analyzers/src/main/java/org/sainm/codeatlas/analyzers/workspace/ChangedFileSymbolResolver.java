package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sainm.codeatlas.analyzers.source.JavaAnalysisDiagnostic;
import org.sainm.codeatlas.analyzers.source.JavaClassInfo;
import org.sainm.codeatlas.analyzers.source.JavaMethodInfo;
import org.sainm.codeatlas.analyzers.source.JavaSourceAnalysisResult;
import org.sainm.codeatlas.analyzers.source.JavaSourceAnalyzer;
import org.sainm.codeatlas.analyzers.source.JdbcSqlAnalysisResult;
import org.sainm.codeatlas.analyzers.source.JdbcSqlAnalyzer;
import org.sainm.codeatlas.analyzers.source.MyBatisXmlAnalysisResult;
import org.sainm.codeatlas.analyzers.source.MyBatisXmlAnalyzer;
import org.sainm.codeatlas.analyzers.source.MyBatisXmlStatementInfo;
import org.sainm.codeatlas.analyzers.source.SqlColumnAccessInfo;
import org.sainm.codeatlas.analyzers.source.SqlStatementSourceInfo;
import org.sainm.codeatlas.analyzers.source.SqlTableAccessInfo;
import org.sainm.codeatlas.analyzers.source.SqlTableAnalysisResult;
import org.sainm.codeatlas.analyzers.source.SqlTableAnalyzer;

public final class ChangedFileSymbolResolver {
    private ChangedFileSymbolResolver() {
    }

    public static ChangedFileSymbolResolver defaults() {
        return new ChangedFileSymbolResolver();
    }

    public ChangedSymbolResolution resolve(
            Path workspaceRoot,
            List<GitChangedFile> changedFiles,
            ChangedSymbolContext context) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<GitChangedFile> files = changedFiles == null ? List.of() : changedFiles;
        List<Path> javaPaths = new ArrayList<>();
        List<Path> xmlPaths = new ArrayList<>();
        Map<String, ChangedSymbolInfo> symbols = new LinkedHashMap<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        for (GitChangedFile changedFile : files) {
            String path = changedFile.effectivePath();
            Path file = workspaceRoot.resolve(path);
            if (!Files.isRegularFile(file)) {
                if (isDeleted(changedFile)) {
                    addDeletedPathSymbols(context, symbols, changedFile);
                }
                continue;
            }
            String lower = path.toLowerCase();
            if (lower.endsWith(".java")) {
                javaPaths.add(file);
            } else if (lower.endsWith(".jsp") || lower.endsWith(".jspx")) {
                add(symbols, "jsp-page", jspPageId(context, path), path, firstChangedLine(changedFile));
            } else if (lower.endsWith(".xml")) {
                xmlPaths.add(file);
            } else if (isConfigFile(lower)) {
                add(symbols, "config-key", configKeyId(context, path), path, firstChangedLine(changedFile));
            }
        }
        JavaSourceAnalysisResult javaSource = !javaPaths.isEmpty()
                ? JavaSourceAnalyzer.defaults().analyze(workspaceRoot, javaPaths)
                : new JavaSourceAnalysisResult(false, List.of(), List.of(), List.of(), List.of(), List.of());
        diagnostics.addAll(javaSource.diagnostics());
        JdbcSqlAnalysisResult jdbc = !javaPaths.isEmpty()
                ? JdbcSqlAnalyzer.defaults().analyze(workspaceRoot, javaPaths)
                : new JdbcSqlAnalysisResult(List.of(), List.of());
        diagnostics.addAll(jdbc.diagnostics());
        MyBatisXmlAnalysisResult myBatisXml = !xmlPaths.isEmpty()
                ? MyBatisXmlAnalyzer.defaults().analyze(workspaceRoot, xmlPaths)
                : new MyBatisXmlAnalysisResult(List.of(), List.of(), List.of());
        diagnostics.addAll(myBatisXml.diagnostics());
        List<SqlStatementSourceInfo> allSqlSources = new ArrayList<>();
        allSqlSources.addAll(jdbc.sqlStatementSources());
        allSqlSources.addAll(myBatisXml.sqlStatementSources());
        SqlTableAnalysisResult sqlTables = !allSqlSources.isEmpty()
                ? SqlTableAnalyzer.defaults().analyze(allSqlSources)
                : new SqlTableAnalysisResult(List.of(), List.of());
        for (GitChangedFile changedFile : files) {
            String path = changedFile.effectivePath();
            Path file = workspaceRoot.resolve(path);
            String lower = path.toLowerCase();
            if (lower.endsWith(".java")) {
                addJavaSymbolsFromBatch(changedFile, context, javaSource, jdbc, sqlTables, symbols);
            } else if (lower.endsWith(".xml")) {
                addMyBatisSymbolsFromBatch(file, changedFile, context, myBatisXml, sqlTables, symbols);
            }
        }
        return new ChangedSymbolResolution(List.copyOf(symbols.values()), diagnostics);
    }

    private static void addJavaSymbolsFromBatch(
            GitChangedFile changedFile,
            ChangedSymbolContext context,
            JavaSourceAnalysisResult javaSource,
            JdbcSqlAnalysisResult jdbc,
            SqlTableAnalysisResult sqlTables,
            Map<String, ChangedSymbolInfo> symbols) {
        String path = changedFile.effectivePath();
        for (JavaClassInfo classInfo : javaSource.classes()) {
            if (classInfo.location().relativePath().equals(path)) {
                add(symbols, "class", classId(context, classInfo.qualifiedName()), classInfo.location().relativePath(), classInfo.location().line());
            }
        }
        for (JavaMethodInfo method : javaSource.methods()) {
            if (method.location().relativePath().equals(path)
                    && overlapsChangedHunk(method.location().line(), method.endLine(), changedFile.hunks())) {
                add(symbols,
                        "method",
                        methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature()),
                        method.location().relativePath(),
                        method.location().line());
            }
        }
        List<SqlSymbol> changedSqlSymbols = jdbc.statements().stream()
                .filter(statement -> statement.location().relativePath().equals(path)
                        && overlapsChangedHunk(statement.location().line(), endLine(statement.location().line(), statement.sql()), changedFile.hunks()))
                .map(statement -> new SqlSymbol(statement.statementId(), statement.location().relativePath(), statement.location().line(), endLine(statement.location().line(), statement.sql())))
                .toList();
        Set<String> changedStatementIds = statementIds(changedSqlSymbols);
        SqlTableAnalysisResult filteredTables = filterTablesByStatementIds(sqlTables, changedStatementIds);
        addSqlAndTableSymbols(context, context.javaSourceRootKey(), symbols, changedSqlSymbols,
                filteredTables.tableAccesses(),
                filteredTables.columnAccesses());
    }

    private static void addMyBatisSymbolsFromBatch(
            Path file,
            GitChangedFile changedFile,
            ChangedSymbolContext context,
            MyBatisXmlAnalysisResult xml,
            SqlTableAnalysisResult sqlTables,
            Map<String, ChangedSymbolInfo> symbols) {
        String path = changedFile.effectivePath();
        List<SqlSymbol> changedSqlSymbols = xml.statements().stream()
                .filter(statement -> statement.location().relativePath().equals(path)
                        && overlapsChangedHunk(statement.location().line(), myBatisStatementEndLine(file, statement), changedFile.hunks()))
                .map(statement -> new SqlSymbol(
                        statement.namespace() + "." + statement.id(),
                        statement.location().relativePath(),
                        statement.location().line(),
                        myBatisStatementEndLine(file, statement)))
                .toList();
        Set<String> changedStatementIds = statementIds(changedSqlSymbols);
        SqlTableAnalysisResult filteredTables = filterTablesByStatementIds(sqlTables, changedStatementIds);
        addSqlAndTableSymbols(context, context.resourceSourceRootKey(), symbols, changedSqlSymbols,
                filteredTables.tableAccesses(),
                filteredTables.columnAccesses());
    }

    private static void addSqlAndTableSymbols(
            ChangedSymbolContext context,
            String sqlSourceRootKey,
            Map<String, ChangedSymbolInfo> symbols,
            List<SqlSymbol> sqlSymbols,
            List<SqlTableAccessInfo> tableAccesses,
            List<SqlColumnAccessInfo> columnAccesses) {
        for (SqlSymbol sql : sqlSymbols) {
            add(symbols, "sql-statement", sqlStatementId(context, sqlSourceRootKey, sql), sql.relativePath(), sql.line());
        }
        for (SqlTableAccessInfo access : tableAccesses) {
            add(symbols, "db-table", dbTableId(context, access.tableName()), access.location().relativePath(), access.location().line());
        }
        for (SqlColumnAccessInfo access : columnAccesses) {
            add(symbols, "db-column", dbColumnId(context, access.tableName(), access.columnName()), access.location().relativePath(), access.location().line());
        }
    }

    private static SqlTableAnalysisResult filterTablesByStatementIds(SqlTableAnalysisResult tables, Set<String> statementIds) {
        return new SqlTableAnalysisResult(
                tables.tableAccesses().stream()
                        .filter(access -> statementIds.contains(access.statementId()))
                        .toList(),
                tables.columnAccesses().stream()
                        .filter(access -> statementIds.contains(access.statementId()))
                        .toList(),
                tables.diagnostics());
    }

    private static Set<String> statementIds(List<SqlSymbol> sqlSymbols) {
        Set<String> result = new LinkedHashSet<>();
        for (SqlSymbol sqlSymbol : sqlSymbols) {
            result.add(sqlSymbol.statementId());
        }
        return result;
    }

    private static boolean isDeleted(GitChangedFile changedFile) {
        return "DELETE".equalsIgnoreCase(changedFile.changeType())
                || (!changedFile.oldPath().isBlank() && changedFile.newPath().isBlank());
    }

    private static void addDeletedPathSymbols(
            ChangedSymbolContext context,
            Map<String, ChangedSymbolInfo> symbols,
            GitChangedFile changedFile) {
        String path = changedFile.effectivePath();
        String lower = path.toLowerCase();
        int line = firstDeletedLine(changedFile);
        add(symbols, "source-file", sourceFileId(context, sourceRootForPath(context, path), path), path, line);
        if (lower.endsWith(".java")) {
            String qualifiedName = javaTypeName(context, path);
            if (!qualifiedName.isBlank()) {
                add(symbols, "class", classId(context, qualifiedName), path, line);
            }
        } else if (lower.endsWith(".jsp") || lower.endsWith(".jspx")) {
            add(symbols, "jsp-page", jspPageId(context, path), path, line);
        } else if (isConfigFile(lower)) {
            add(symbols, "config-key", configKeyId(context, path), path, line);
        } else if (lower.endsWith(".xml")) {
            add(symbols, "source-file", sourceFileId(context, context.resourceSourceRootKey(), path), path, line);
        }
    }

    private static boolean overlapsChangedHunk(int startLine, int endLine, List<GitChangedHunk> hunks) {
        if (hunks == null || hunks.isEmpty()) {
            return true;
        }
        int normalizedEndLine = Math.max(startLine, endLine);
        for (GitChangedHunk hunk : hunks) {
            int start = hunk.newStartLine();
            int end = start + Math.max(1, hunk.newLineCount()) - 1;
            if (startLine <= end && normalizedEndLine >= start) {
                return true;
            }
        }
        return false;
    }

    private static int firstChangedLine(GitChangedFile changedFile) {
        return changedFile.hunks().stream()
                .mapToInt(GitChangedHunk::newStartLine)
                .filter(line -> line > 0)
                .findFirst()
                .orElse(0);
    }

    private static int firstDeletedLine(GitChangedFile changedFile) {
        return changedFile.hunks().stream()
                .mapToInt(GitChangedHunk::oldStartLine)
                .filter(line -> line > 0)
                .findFirst()
                .orElse(0);
    }

    private static boolean isConfigFile(String lowerPath) {
        return lowerPath.endsWith(".properties") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml");
    }

    private static String classId(ChangedSymbolContext context, String qualifiedName) {
        return "class://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + qualifiedName;
    }

    private static String methodId(
            ChangedSymbolContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return "method://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.javaSourceRootKey() + "/" + ownerQualifiedName + "#" + methodName + signature;
    }

    private static String jspPageId(ChangedSymbolContext context, String path) {
        return "jsp-page://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.webSourceRootKey() + "/" + stripSourceRoot(context.webSourceRootKey(), path);
    }

    private static String configKeyId(ChangedSymbolContext context, String path) {
        return "config-key://" + context.projectId() + "/" + context.moduleKey() + "/"
                + context.resourceSourceRootKey() + "/" + stripSourceRoot(context.resourceSourceRootKey(), path)
                + "#" + stripSourceRoot(context.resourceSourceRootKey(), path);
    }

    private static String sqlStatementId(ChangedSymbolContext context, String sourceRootKey, SqlSymbol sql) {
        return "sql-statement://" + context.projectId() + "/" + context.moduleKey() + "/"
                + sourceRootKey + "/" + stripSourceRoot(sourceRootKey, sql.relativePath())
                + "#" + sql.statementId();
    }

    private static String sourceFileId(ChangedSymbolContext context, String sourceRootKey, String path) {
        return "source-file://" + context.projectId() + "/" + context.moduleKey() + "/"
                + sourceRootKey + "/" + stripSourceRoot(sourceRootKey, path);
    }

    private static String dbTableId(ChangedSymbolContext context, String tableName) {
        return "db-table://" + context.projectId() + "/" + context.datasourceKey() + "/"
                + context.schemaName() + "/" + tableNameWithoutContextSchema(context, tableName);
    }

    private static String dbColumnId(ChangedSymbolContext context, String tableName, String columnName) {
        return "db-column://" + context.projectId() + "/" + context.datasourceKey() + "/"
                + context.schemaName() + "/" + tableNameWithoutContextSchema(context, tableName) + "#" + columnName;
    }

    private static String stripSourceRoot(String sourceRoot, String path) {
        String normalized = FileInventoryEntry.normalizeRelativePath(path);
        String prefix = sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/";
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : normalized;
    }

    private static String sourceRootForPath(ChangedSymbolContext context, String path) {
        String normalized = FileInventoryEntry.normalizeRelativePath(path);
        if (normalized.startsWith(context.javaSourceRootKey() + "/")) {
            return context.javaSourceRootKey();
        }
        if (normalized.startsWith(context.webSourceRootKey() + "/")) {
            return context.webSourceRootKey();
        }
        return context.resourceSourceRootKey();
    }

    private static String javaTypeName(ChangedSymbolContext context, String path) {
        String ownerPath = stripSourceRoot(context.javaSourceRootKey(), path);
        if (!ownerPath.endsWith(".java")) {
            return "";
        }
        return ownerPath.substring(0, ownerPath.length() - ".java".length()).replace('/', '.');
    }

    private static int endLine(int startLine, String text) {
        return startLine + (int) text.chars().filter(character -> character == '\n').count();
    }

    private static int myBatisStatementEndLine(Path file, MyBatisXmlStatementInfo statement) {
        int fallback = endLine(statement.location().line(), statement.sql());
        try {
            List<String> lines = Files.readAllLines(file);
            String closingTag = "</" + statement.kind().name().toLowerCase() + ">";
            int startIndex = Math.max(0, statement.location().line() - 1);
            for (int index = startIndex; index < lines.size(); index++) {
                if (lines.get(index).toLowerCase().contains(closingTag)) {
                    return index + 1;
                }
            }
        } catch (IOException ignored) {
            return fallback;
        }
        return fallback;
    }

    private static String tableNameWithoutContextSchema(ChangedSymbolContext context, String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        String prefix = context.schemaName() + ".";
        if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    private static void add(
            Map<String, ChangedSymbolInfo> symbols,
            String kind,
            String identityId,
            String relativePath,
            int line) {
        symbols.putIfAbsent(identityId, new ChangedSymbolInfo(kind, identityId, relativePath, line));
    }

    private record SqlSymbol(String statementId, String relativePath, int line, int endLine) {
    }
}
