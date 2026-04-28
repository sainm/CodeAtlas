package org.sainm.codeatlas.analyzers.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SimpleSqlTableExtractor {
    private static final Pattern READ_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][\\w.$]*)");
    private static final Pattern INSERT_PATTERN = Pattern.compile("(?i)\\binsert\\s+into\\s+([a-zA-Z_][\\w.$]*)");
    private static final Pattern INSERT_COLUMNS_PATTERN = Pattern.compile("(?is)\\binsert\\s+into\\s+[a-zA-Z_][\\w.$]*\\s*\\((.*?)\\)");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("(?i)\\bupdate\\s+([a-zA-Z_][\\w.$]*)");
    private static final Pattern UPDATE_SET_PATTERN = Pattern.compile("(?is)\\bset\\s+(.*?)(?:\\bwhere\\b|$)");
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?i)\\bdelete\\s+from\\s+([a-zA-Z_][\\w.$]*)");
    private static final Pattern SELECT_COLUMNS_PATTERN = Pattern.compile("(?is)\\bselect\\s+(.*?)\\s+from\\s+");
    private static final Pattern WHERE_PATTERN = Pattern.compile("(?is)\\bwhere\\s+(.*)$");
    private static final Pattern WHERE_COLUMN_PATTERN = Pattern.compile("(?i)(?:^|\\s|\\()([a-zA-Z_][\\w.]*)\\s*(?:=|<>|!=|<|>|<=|>=|\\bin\\b|\\blike\\b)");

    List<SqlTableAccess> extract(String commandType, String sql) {
        String normalizedCommand = commandType.toLowerCase(Locale.ROOT);
        List<SqlTableAccess> accesses = new ArrayList<>();
        if (normalizedCommand.equals("select")) {
            addMatches(accesses, READ_PATTERN, sql, SqlAccessType.READ);
            accesses = withColumns(accesses, selectColumns(sql));
            return accesses;
        }
        if (normalizedCommand.equals("insert")) {
            addMatches(accesses, INSERT_PATTERN, sql, SqlAccessType.WRITE);
            accesses = withColumns(accesses, insertColumns(sql));
            return accesses;
        }
        if (normalizedCommand.equals("update")) {
            addMatches(accesses, UPDATE_PATTERN, sql, SqlAccessType.WRITE);
            addMatches(accesses, READ_PATTERN, sql, SqlAccessType.READ);
            accesses = withColumns(accesses, updateColumns(sql));
            return accesses;
        }
        if (normalizedCommand.equals("delete")) {
            addMatches(accesses, DELETE_PATTERN, sql, SqlAccessType.WRITE);
        }
        return accesses;
    }

    private void addMatches(List<SqlTableAccess> accesses, Pattern pattern, String sql, SqlAccessType type) {
        Set<String> seen = new LinkedHashSet<>(accesses.stream()
            .map(access -> access.accessType() + ":" + access.tableName())
            .toList());
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1);
            String key = type + ":" + table;
            if (seen.add(key)) {
                accesses.add(new SqlTableAccess(table, type));
            }
        }
    }

    private List<SqlTableAccess> withColumns(List<SqlTableAccess> accesses, List<String> columns) {
        if (columns.isEmpty()) {
            return accesses;
        }
        return accesses.stream()
            .map(access -> new SqlTableAccess(access.tableName(), access.accessType(), columns))
            .toList();
    }

    private List<String> selectColumns(String sql) {
        Matcher matcher = SELECT_COLUMNS_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return whereColumns(sql);
        }
        List<String> columns = new ArrayList<>(splitColumns(matcher.group(1)));
        columns.addAll(whereColumns(sql));
        return distinct(columns);
    }

    private List<String> insertColumns(String sql) {
        Matcher matcher = INSERT_COLUMNS_PATTERN.matcher(sql);
        return matcher.find() ? splitColumns(matcher.group(1)) : List.of();
    }

    private List<String> updateColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher matcher = UPDATE_SET_PATTERN.matcher(sql);
        if (matcher.find()) {
            for (String assignment : matcher.group(1).split(",")) {
                int equals = assignment.indexOf('=');
                if (equals > 0) {
                    columns.add(cleanColumn(assignment.substring(0, equals)));
                }
            }
        }
        columns.addAll(whereColumns(sql));
        return distinct(columns);
    }

    private List<String> whereColumns(String sql) {
        Matcher where = WHERE_PATTERN.matcher(sql);
        if (!where.find()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        Matcher matcher = WHERE_COLUMN_PATTERN.matcher(where.group(1));
        while (matcher.find()) {
            columns.add(cleanColumn(matcher.group(1)));
        }
        return distinct(columns);
    }

    private List<String> splitColumns(String text) {
        List<String> columns = new ArrayList<>();
        for (String part : text.split(",")) {
            String column = cleanColumn(part);
            if (!column.isBlank() && !column.equals("*")) {
                columns.add(column);
            }
        }
        return distinct(columns);
    }

    private String cleanColumn(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank() || value.contains("(")) {
            return "";
        }
        value = value.replaceAll("(?i)\\s+as\\s+.+$", "");
        value = value.replaceAll("\\s+.+$", "");
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            value = value.substring(dot + 1);
        }
        return value.replace("`", "").replace("\"", "").trim();
    }

    private List<String> distinct(List<String> columns) {
        return columns.stream()
            .filter(column -> column != null && !column.isBlank())
            .distinct()
            .toList();
    }
}
