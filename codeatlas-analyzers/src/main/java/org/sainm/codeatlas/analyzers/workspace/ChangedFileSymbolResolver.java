package org.sainm.codeatlas.analyzers.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sainm.codeatlas.analyzers.source.JavaAnalysisDiagnostic;
import org.sainm.codeatlas.analyzers.source.JavaClassInfo;
import org.sainm.codeatlas.analyzers.source.JavaMethodInfo;
import org.sainm.codeatlas.analyzers.source.JavaSourceAnalysisResult;
import org.sainm.codeatlas.analyzers.source.JavaSourceAnalyzer;
import org.sainm.codeatlas.analyzers.source.JdbcSqlAnalysisResult;
import org.sainm.codeatlas.analyzers.source.JdbcSqlAnalyzer;
import org.sainm.codeatlas.analyzers.source.MyBatisXmlAnalysisResult;
import org.sainm.codeatlas.analyzers.source.MyBatisXmlAnalyzer;
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
        Map<String, ChangedSymbolInfo> symbols = new LinkedHashMap<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        for (GitChangedFile changedFile : changedFiles == null ? List.<GitChangedFile>of() : changedFiles) {
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
                addJavaSymbols(workspaceRoot, file, changedFile, context, symbols, diagnostics);
            } else if (lower.endsWith(".jsp") || lower.endsWith(".jspx")) {
                add(symbols, "jsp-page", jspPageId(context, path), path, firstChangedLine(changedFile));
            } else if (lower.endsWith(".xml")) {
                addMyBatisSymbols(workspaceRoot, file, context, symbols, diagnostics);
            } else if (isConfigFile(lower)) {
                add(symbols, "config-key", configKeyId(context, path), path, firstChangedLine(changedFile));
            }
        }
        return new ChangedSymbolResolution(List.copyOf(symbols.values()), diagnostics);
    }

    private static void addJavaSymbols(
            Path workspaceRoot,
            Path file,
            GitChangedFile changedFile,
            ChangedSymbolContext context,
            Map<String, ChangedSymbolInfo> symbols,
            List<JavaAnalysisDiagnostic> diagnostics) {
        JavaSourceAnalysisResult source = JavaSourceAnalyzer.defaults().analyze(workspaceRoot, List.of(file));
        diagnostics.addAll(source.diagnostics());
        for (JavaClassInfo classInfo : source.classes()) {
            add(symbols, "class", classId(context, classInfo.qualifiedName()), classInfo.location().relativePath(), classInfo.location().line());
        }
        for (JavaMethodInfo method : source.methods()) {
            if (overlapsChangedHunk(method.location().line(), changedFile.hunks())) {
                add(symbols,
                        "method",
                        methodId(context, method.ownerQualifiedName(), method.simpleName(), method.signature()),
                        method.location().relativePath(),
                        method.location().line());
            }
        }
        JdbcSqlAnalysisResult jdbc = JdbcSqlAnalyzer.defaults().analyze(workspaceRoot, List.of(file));
        diagnostics.addAll(jdbc.diagnostics());
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(jdbc.sqlStatementSources());
        addSqlAndTableSymbols(context, context.javaSourceRootKey(), symbols, jdbc.statements().stream()
                .map(statement -> new SqlSymbol(statement.statementId(), statement.location().relativePath(), statement.location().line()))
                .toList(), tables.tableAccesses());
    }

    private static void addMyBatisSymbols(
            Path workspaceRoot,
            Path file,
            ChangedSymbolContext context,
            Map<String, ChangedSymbolInfo> symbols,
            List<JavaAnalysisDiagnostic> diagnostics) {
        MyBatisXmlAnalysisResult xml = MyBatisXmlAnalyzer.defaults().analyze(workspaceRoot, List.of(file));
        diagnostics.addAll(xml.diagnostics());
        SqlTableAnalysisResult tables = SqlTableAnalyzer.defaults().analyze(xml.sqlStatementSources());
        addSqlAndTableSymbols(context, context.resourceSourceRootKey(), symbols, xml.statements().stream()
                .map(statement -> new SqlSymbol(
                        statement.namespace() + "." + statement.id(),
                        statement.location().relativePath(),
                        statement.location().line()))
                .toList(), tables.tableAccesses());
    }

    private static void addSqlAndTableSymbols(
            ChangedSymbolContext context,
            String sqlSourceRootKey,
            Map<String, ChangedSymbolInfo> symbols,
            List<SqlSymbol> sqlSymbols,
            List<SqlTableAccessInfo> tableAccesses) {
        for (SqlSymbol sql : sqlSymbols) {
            add(symbols, "sql-statement", sqlStatementId(context, sqlSourceRootKey, sql), sql.relativePath(), sql.line());
        }
        for (SqlTableAccessInfo access : tableAccesses) {
            add(symbols, "db-table", dbTableId(context, access.tableName()), access.location().relativePath(), access.location().line());
        }
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

    private static boolean overlapsChangedHunk(int line, List<GitChangedHunk> hunks) {
        if (hunks == null || hunks.isEmpty()) {
            return true;
        }
        for (GitChangedHunk hunk : hunks) {
            int start = hunk.newStartLine();
            int end = start + Math.max(1, hunk.newLineCount()) - 1;
            if (line >= start && line <= end) {
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
                + context.schemaName() + "/" + tableName;
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

    private static void add(
            Map<String, ChangedSymbolInfo> symbols,
            String kind,
            String identityId,
            String relativePath,
            int line) {
        symbols.putIfAbsent(identityId, new ChangedSymbolInfo(kind, identityId, relativePath, line));
    }

    private record SqlSymbol(String statementId, String relativePath, int line) {
    }
}
