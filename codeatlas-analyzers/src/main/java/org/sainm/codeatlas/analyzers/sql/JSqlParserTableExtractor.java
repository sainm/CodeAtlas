package org.sainm.codeatlas.analyzers.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.util.TablesNamesFinder;

final class JSqlParserTableExtractor {
    private final SimpleSqlTableExtractor fallbackExtractor = new SimpleSqlTableExtractor();

    List<SqlTableAccess> extract(String commandType, String sql) {
        try {
            Set<String> tableNames = TablesNamesFinder.findTables(sql);
            if (tableNames.isEmpty()) {
                return fallbackExtractor.extract(commandType, sql);
            }
            SqlAccessType accessType = accessType(commandType);
            List<String> fallbackColumns = fallbackExtractor.extract(commandType, sql).stream()
                .flatMap(access -> access.columnNames().stream())
                .distinct()
                .toList();
            List<SqlTableAccess> accesses = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String tableName : tableNames) {
                String normalized = normalizeTable(tableName);
                if (!normalized.isBlank() && seen.add(accessType + ":" + normalized)) {
                    accesses.add(new SqlTableAccess(normalized, accessType, fallbackColumns));
                }
            }
            return accesses.isEmpty() ? fallbackExtractor.extract(commandType, sql) : accesses;
        } catch (Exception exception) {
            return fallbackExtractor.extract(commandType, sql);
        }
    }

    private SqlAccessType accessType(String commandType) {
        String command = commandType == null ? "" : commandType.toLowerCase(Locale.ROOT);
        return command.equals("select") ? SqlAccessType.READ : SqlAccessType.WRITE;
    }

    private String normalizeTable(String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
            || (normalized.startsWith("`") && normalized.endsWith("`"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
