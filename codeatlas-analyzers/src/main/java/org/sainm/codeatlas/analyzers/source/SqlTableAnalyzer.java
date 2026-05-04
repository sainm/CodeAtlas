package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

public final class SqlTableAnalyzer {
    private static final Pattern MYBATIS_PARAMETER = Pattern.compile("[#$]\\{[^}]+}");
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SELECT_TABLE = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern UPDATE_TABLE = Pattern.compile("(?i)\\bupdate\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern INSERT_TABLE = Pattern.compile("(?i)\\binsert\\s+into\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern DELETE_TABLE = Pattern.compile("(?i)\\bdelete\\s+from\\s+([A-Za-z_][A-Za-z0-9_.$]*)");

    private SqlTableAnalyzer() {
    }

    public static SqlTableAnalyzer defaults() {
        return new SqlTableAnalyzer();
    }

    public SqlTableAnalysisResult analyze(List<SqlStatementSourceInfo> statements) {
        if (statements == null || statements.isEmpty()) {
            return new SqlTableAnalysisResult(List.of(), List.of());
        }
        List<SqlTableAccessInfo> tableAccesses = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        for (SqlStatementSourceInfo source : statements) {
            String normalizedSql = normalizeSql(source.sql());
            try {
                Statement statement = CCJSqlParserUtil.parse(normalizedSql);
                tableAccesses.addAll(tableAccesses(source, statement, normalizedSql));
            } catch (JSQLParserException exception) {
                List<SqlTableAccessInfo> fallbackTables = fallbackTableAccesses(source, normalizedSql);
                if (fallbackTables.isEmpty()) {
                    diagnostics.add(new JavaAnalysisDiagnostic(
                            "SQL_PARSE_FAILED",
                            source.statementId() + ": " + exception.getMessage()));
                } else {
                    tableAccesses.addAll(fallbackTables);
                }
            }
        }
        return new SqlTableAnalysisResult(tableAccesses, diagnostics);
    }

    private static List<SqlTableAccessInfo> tableAccesses(
            SqlStatementSourceInfo source,
            Statement statement,
            String normalizedSql) {
        if (statement instanceof Select) {
            return tableAccesses(source, tableNames(statement), SqlTableAccessKind.READ, false);
        }
        if (statement instanceof Insert insert) {
            List<SqlTableAccessInfo> result = new ArrayList<>();
            result.addAll(tableAccesses(source, tableNames(insert.getTable()), SqlTableAccessKind.WRITE, false));
            if (insert.getSelect() != null) {
                result.addAll(tableAccesses(source, tableNames(insert.getSelect()), SqlTableAccessKind.READ, false));
            }
            return result;
        }
        if (statement instanceof Update update) {
            return writeTableAccesses(source, normalizedSql, tableNames(update.getTable()), statement);
        }
        if (statement instanceof Delete delete) {
            return writeTableAccesses(source, normalizedSql, tableNames(delete.getTable()), statement);
        }
        return List.of();
    }

    private static List<SqlTableAccessInfo> writeTableAccesses(
            SqlStatementSourceInfo source,
            String normalizedSql,
            List<String> writtenTables,
            Statement statement) {
        List<SqlTableAccessInfo> result = new ArrayList<>(
                tableAccesses(source, writtenTables, SqlTableAccessKind.WRITE, false));
        Set<String> readTables = new LinkedHashSet<>(tableNames(statement));
        readTables.addAll(matches(SELECT_TABLE, normalizedSql));
        readTables.removeAll(writtenTables);
        result.addAll(tableAccesses(source, readTables, SqlTableAccessKind.READ, false));
        return result;
    }

    private static List<SqlTableAccessInfo> tableAccesses(
            SqlStatementSourceInfo source,
            Iterable<String> tableNames,
            SqlTableAccessKind kind,
            boolean conservativeFallback) {
        List<SqlTableAccessInfo> result = new ArrayList<>();
        for (String tableName : tableNames) {
            result.add(new SqlTableAccessInfo(
                    source.statementId(),
                    tableName,
                    kind,
                    conservativeFallback,
                    source.location()));
        }
        return result;
    }

    private static List<String> tableNames(Statement statement) {
        Set<String> names = new TablesNamesFinder<>().getTables(statement);
        return tableNames(names);
    }

    private static List<String> tableNames(Table table) {
        if (table == null) {
            return List.of();
        }
        return tableNames(List.of(table.getFullyQualifiedName()));
    }

    private static List<String> tableNames(Iterable<String> names) {
        Set<String> uniqueNames = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                uniqueNames.add(name);
            }
        }
        return List.copyOf(uniqueNames);
    }

    private static String normalizeSql(String sql) {
        String withoutTags = XML_TAG.matcher(sql).replaceAll(" ");
        String withoutMyBatisParameters = MYBATIS_PARAMETER.matcher(withoutTags).replaceAll("?");
        return withoutMyBatisParameters.replaceAll("\\s+", " ").trim();
    }

    private static List<SqlTableAccessInfo> fallbackTableAccesses(SqlStatementSourceInfo source, String sql) {
        SqlTableAccessKind kind = fallbackKind(sql);
        if (kind == null) {
            return List.of();
        }
        Set<String> tableNames = switch (kind) {
            case READ -> matches(SELECT_TABLE, sql);
            case WRITE -> writeTables(sql);
        };
        return tableAccesses(source, tableNames, kind, true);
    }

    private static SqlTableAccessKind fallbackKind(String sql) {
        String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("select")) {
            return SqlTableAccessKind.READ;
        }
        if (trimmed.startsWith("insert") || trimmed.startsWith("update") || trimmed.startsWith("delete")) {
            return SqlTableAccessKind.WRITE;
        }
        return null;
    }

    private static Set<String> writeTables(String sql) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(matches(UPDATE_TABLE, sql));
        result.addAll(matches(INSERT_TABLE, sql));
        result.addAll(matches(DELETE_TABLE, sql));
        return result;
    }

    private static Set<String> matches(Pattern pattern, String sql) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (tableName != null && !tableName.isBlank()) {
                result.add(tableName);
            }
        }
        return result;
    }
}
