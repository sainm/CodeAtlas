package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Pattern XML_TAG = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern SELECT_TABLE = Pattern.compile(
            "(?i)\\b(?:from|(?:right|left|cross|full|inner|natural)?\\s*(?:outer\\s+)?join)\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern UPDATE_TABLE = Pattern.compile("(?i)\\bupdate\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern INSERT_TABLE = Pattern.compile("(?i)\\binsert\\s+into\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern DELETE_TABLE = Pattern.compile("(?i)\\bdelete\\s+from\\s+([A-Za-z_][A-Za-z0-9_.$]*)");
    private static final Pattern TABLE_ALIAS = Pattern.compile(
            "(?i)\\b(?:from|(?:right|left|cross|full|inner|natural)?\\s*(?:outer\\s+)?join)\\s+([A-Za-z_][A-Za-z0-9_.$]*)(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*))?");
    private static final Pattern QUALIFIED_COLUMN = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern SELECT_LIST = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+");
    private static final Pattern WHERE_CLAUSE = Pattern.compile(
            "(?is)\\bwhere\\b(.*?)(?:\\bgroup\\s+by\\b|\\border\\s+by\\b|\\bhaving\\b|\\blimit\\b|$)");
    private static final Pattern UNQUALIFIED_PREDICATE_COLUMN = Pattern.compile(
            "(?i)\\b([A-Za-z_][A-Za-z0-9_]*)\\b\\s*(?:=|<>|!=|<=|>=|<|>|\\bin\\b|\\blike\\b|\\bis\\b)");
    private static final Pattern INSERT_COLUMNS = Pattern.compile(
            "(?is)^\\s*insert\\s+into\\s+[A-Za-z_][A-Za-z0-9_.$]*\\s*\\((.*?)\\)");
    private static final Pattern UPDATE_SET = Pattern.compile(
            "(?is)^\\s*update\\s+([A-Za-z_][A-Za-z0-9_.$]*)\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s+)?set\\s+(.*?)(?:\\s+where\\s+|$)");

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
        List<SqlColumnAccessInfo> columnAccesses = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        for (SqlStatementSourceInfo source : statements) {
            String normalizedSql = normalizeSql(source.sql());
            try {
                Statement statement = CCJSqlParserUtil.parse(normalizedSql);
                tableAccesses.addAll(tableAccesses(source, statement, normalizedSql));
                columnAccesses.addAll(columnAccesses(source, statement, normalizedSql));
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
        return new SqlTableAnalysisResult(tableAccesses, columnAccesses, diagnostics);
    }

    private static List<SqlTableAccessInfo> tableAccesses(
            SqlStatementSourceInfo source,
            Statement statement,
            String normalizedSql) {
        if (statement instanceof Select) {
            return tableAccesses(source, tableNames(statement), SqlTableAccessKind.READ, source.conservativeFallback());
        }
        if (statement instanceof Insert insert) {
            List<SqlTableAccessInfo> result = new ArrayList<>();
            result.addAll(tableAccesses(
                    source,
                    tableNames(insert.getTable()),
                    SqlTableAccessKind.WRITE,
                    source.conservativeFallback()));
            if (insert.getSelect() != null) {
                result.addAll(tableAccesses(
                        source,
                        tableNames(insert.getSelect()),
                        SqlTableAccessKind.READ,
                        source.conservativeFallback()));
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

    private static List<SqlColumnAccessInfo> columnAccesses(
            SqlStatementSourceInfo source,
            Statement statement,
            String normalizedSql) {
        if (source.conservativeFallback()) {
            return List.of();
        }
        if (statement instanceof Select) {
            return readColumnAccesses(source, normalizedSql);
        }
        if (statement instanceof Insert insert) {
            List<SqlColumnAccessInfo> result = new ArrayList<>(
                    insertColumnAccesses(source, normalizedSql, tableNames(insert.getTable())));
            if (insert.getSelect() != null) {
                result.addAll(readColumnAccesses(source, normalizedSql));
            }
            return result;
        }
        if (statement instanceof Update update) {
            List<SqlColumnAccessInfo> result = new ArrayList<>(
                    updateColumnAccesses(source, normalizedSql, tableNames(update.getTable())));
            result.addAll(readColumnAccesses(source, normalizedSql));
            return result;
        }
        if (statement instanceof Delete) {
            return readColumnAccesses(source, normalizedSql);
        }
        return List.of();
    }

    private static List<SqlColumnAccessInfo> readColumnAccesses(SqlStatementSourceInfo source, String sql) {
        Map<String, String> aliases = tableAliases(sql);
        List<String> singleTable = aliases.values().stream().distinct().toList();
        Set<ColumnKey> columns = new LinkedHashSet<>();
        Matcher qualifiedMatcher = QUALIFIED_COLUMN.matcher(sql);
        while (qualifiedMatcher.find()) {
            String qualifier = qualifiedMatcher.group(1);
            String tableName = aliases.get(qualifier);
            if (tableName != null) {
                columns.add(new ColumnKey(tableName, qualifiedMatcher.group(2)));
            }
        }
        Matcher selectMatcher = SELECT_LIST.matcher(sql);
        if (selectMatcher.find() && singleTable.size() == 1) {
            for (String columnName : unqualifiedColumns(selectMatcher.group(1))) {
                columns.add(new ColumnKey(singleTable.getFirst(), columnName));
            }
        }
        if (singleTable.size() == 1) {
            for (String columnName : unqualifiedPredicateColumns(sql)) {
                columns.add(new ColumnKey(singleTable.getFirst(), columnName));
            }
        }
        return columnAccesses(source, columns, SqlTableAccessKind.READ);
    }

    private static List<SqlColumnAccessInfo> insertColumnAccesses(
            SqlStatementSourceInfo source,
            String sql,
            List<String> writtenTables) {
        if (writtenTables.size() != 1) {
            return List.of();
        }
        Matcher matcher = INSERT_COLUMNS.matcher(sql);
        if (!matcher.find()) {
            return List.of();
        }
        Set<ColumnKey> columns = new LinkedHashSet<>();
        for (String columnName : commaSeparatedColumns(matcher.group(1))) {
            columns.add(new ColumnKey(writtenTables.getFirst(), columnName));
        }
        return columnAccesses(source, columns, SqlTableAccessKind.WRITE);
    }

    private static List<SqlColumnAccessInfo> updateColumnAccesses(
            SqlStatementSourceInfo source,
            String sql,
            List<String> writtenTables) {
        if (writtenTables.size() != 1) {
            return List.of();
        }
        Matcher matcher = UPDATE_SET.matcher(sql);
        if (!matcher.find()) {
            return List.of();
        }
        Set<ColumnKey> columns = new LinkedHashSet<>();
        for (String assignment : matcher.group(2).split(",")) {
            int equals = assignment.indexOf('=');
            String left = equals >= 0 ? assignment.substring(0, equals) : assignment;
            String columnName = stripQualifier(left.trim());
            if (!columnName.isBlank()) {
                columns.add(new ColumnKey(writtenTables.getFirst(), columnName));
            }
        }
        return columnAccesses(source, columns, SqlTableAccessKind.WRITE);
    }

    private static Map<String, String> tableAliases(String sql) {
        Map<String, String> aliases = new LinkedHashMap<>();
        Matcher matcher = TABLE_ALIAS.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            addSelfAlias(aliases, tableName);
            if (matcher.group(2) != null && !matcher.group(2).isBlank()) {
                aliases.put(matcher.group(2), tableName);
            }
        }
        matches(UPDATE_TABLE, sql).forEach(tableName -> addSelfAlias(aliases, tableName));
        matches(DELETE_TABLE, sql).forEach(tableName -> addSelfAlias(aliases, tableName));
        matches(INSERT_TABLE, sql).forEach(tableName -> addSelfAlias(aliases, tableName));
        return aliases;
    }

    private static void addSelfAlias(Map<String, String> aliases, String tableName) {
        aliases.put(simpleName(tableName), tableName);
    }

    private static List<String> unqualifiedColumns(String selectList) {
        if (selectList.contains("*")) {
            return List.of();
        }
        return commaSeparatedColumns(selectList).stream()
                .map(SqlTableAnalyzer::stripAlias)
                .map(SqlTableAnalyzer::stripQualifier)
                .filter(column -> !column.isBlank())
                .toList();
    }

    private static List<String> unqualifiedPredicateColumns(String sql) {
        Set<String> result = new LinkedHashSet<>();
        Matcher whereMatcher = WHERE_CLAUSE.matcher(sql);
        while (whereMatcher.find()) {
            Matcher columnMatcher = UNQUALIFIED_PREDICATE_COLUMN.matcher(whereMatcher.group(1));
            while (columnMatcher.find()) {
                String columnName = columnMatcher.group(1);
                if (!isSqlKeyword(columnName)) {
                    result.add(columnName);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean isSqlKeyword(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("and")
                || lower.equals("or")
                || lower.equals("not")
                || lower.equals("null")
                || lower.equals("exists")
                || lower.equals("select")
                || lower.equals("from")
                || lower.equals("where");
    }

    private static List<String> commaSeparatedColumns(String value) {
        List<String> result = new ArrayList<>();
        for (String token : value.split(",")) {
            String columnName = stripQualifier(token.trim());
            if (!columnName.isBlank() && columnName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                result.add(columnName);
            }
        }
        return result;
    }

    private static String stripAlias(String expression) {
        return expression.replaceFirst("(?i)\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*$", "")
                .replaceFirst("(?i)\\s+[A-Za-z_][A-Za-z0-9_]*$", "");
    }

    private static String stripQualifier(String value) {
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1).trim() : value.trim();
    }

    private static String simpleName(String tableName) {
        int dot = tableName.lastIndexOf('.');
        return dot >= 0 ? tableName.substring(dot + 1) : tableName;
    }

    private static List<SqlColumnAccessInfo> columnAccesses(
            SqlStatementSourceInfo source,
            Iterable<ColumnKey> columns,
            SqlTableAccessKind kind) {
        List<SqlColumnAccessInfo> result = new ArrayList<>();
        for (ColumnKey column : columns) {
            result.add(new SqlColumnAccessInfo(
                    source.statementId(),
                    column.tableName(),
                    column.columnName(),
                    kind,
                    source.location()));
        }
        return result;
    }

    private static List<SqlTableAccessInfo> writeTableAccesses(
            SqlStatementSourceInfo source,
            String normalizedSql,
            List<String> writtenTables,
            Statement statement) {
        List<SqlTableAccessInfo> result = new ArrayList<>(
                tableAccesses(source, writtenTables, SqlTableAccessKind.WRITE, source.conservativeFallback()));
        Set<String> readTables = new LinkedHashSet<>(tableNames(statement));
        readTables.addAll(matches(SELECT_TABLE, normalizedSql));
        readTables.removeAll(writtenTables);
        result.addAll(tableAccesses(source, readTables, SqlTableAccessKind.READ, source.conservativeFallback()));
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

    private record ColumnKey(String tableName, String columnName) {
    }
}
