package org.sainm.codeatlas.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.search.SymbolSearchIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;

public final class BusinessQueryResolver {
    public List<BusinessQueryCandidate> resolve(
        String query,
        QueryPlan plan,
        String projectKey,
        String moduleKey,
        String snapshotId,
        SymbolSearchIndex symbolSearchIndex,
        List<ActiveFact> activeFacts,
        int limit
    ) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        String suggestedParameter = suggestedParameter(plan);
        Map<String, BusinessQueryCandidate> candidates = new LinkedHashMap<>();

        addSearchCandidates(candidates, symbolSearchIndex, query, suggestedParameter, limit);
        addActiveFactCandidates(candidates, activeFacts, normalizedQuery, suggestedParameter);
        addHeuristicCandidates(candidates, query, projectKey, moduleKey, suggestedParameter);

        return candidates.values().stream()
            .sorted(java.util.Comparator.comparingInt(BusinessQueryCandidate::score).reversed()
                .thenComparing(candidate -> candidate.symbolId().value()))
            .limit(Math.max(1, limit))
            .toList();
    }

    private void addSearchCandidates(
        Map<String, BusinessQueryCandidate> candidates,
        SymbolSearchIndex index,
        String query,
        String suggestedParameter,
        int limit
    ) {
        for (var result : index.search(query, Math.max(limit, 20))) {
            add(candidates, new BusinessQueryCandidate(
                result.symbolId(),
                result.kind(),
                result.displayName(),
                result.score() + 10,
                suggestedParameter,
                "命中已建立的符号索引"
            ));
        }
    }

    private void addActiveFactCandidates(
        Map<String, BusinessQueryCandidate> candidates,
        List<ActiveFact> activeFacts,
        String normalizedQuery,
        String suggestedParameter
    ) {
        Set<SymbolId> symbols = new LinkedHashSet<>();
        for (ActiveFact fact : activeFacts) {
            symbols.add(fact.factKey().source());
            symbols.add(fact.factKey().target());
        }
        for (SymbolId symbol : symbols) {
            int score = activeFactScore(symbol, normalizedQuery);
            if (score > 0) {
                add(candidates, new BusinessQueryCandidate(
                    symbol,
                    symbol.kind(),
                    displayName(symbol),
                    score,
                    suggestedParameter,
                    "命中当前版本的分析事实"
                ));
            }
        }
    }

    private int activeFactScore(SymbolId symbol, String query) {
        String value = normalize(symbol.value());
        String owner = normalize(symbol.ownerQualifiedName());
        String member = normalize(symbol.memberName());
        String localId = normalize(symbol.localId());
        if (value.equals(query) || owner.equals(query) || member.equals(query) || localId.equals(query)) {
            return 95;
        }
        for (String token : tokens(query)) {
            if (token.length() < 2) {
                continue;
            }
            if (owner.equals(token) || member.equals(token) || localId.equals(token)) {
                return 90;
            }
            if (owner.endsWith("/" + token) || owner.endsWith("." + token)) {
                return 86;
            }
            if (value.contains(token) || owner.contains(token) || member.contains(token) || localId.contains(token)) {
                return 72;
            }
        }
        return 0;
    }

    private void addHeuristicCandidates(
        Map<String, BusinessQueryCandidate> candidates,
        String query,
        String projectKey,
        String moduleKey,
        String suggestedParameter
    ) {
        for (String token : tokens(query)) {
            if (token.length() < 2) {
                continue;
            }
            if (looksLikeJsp(token)) {
                add(candidates, candidate(
                    SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, moduleKey, "src/main/webapp", token, null),
                    68,
                    suggestedParameter,
                    "按页面路径推断"
                ));
            }
            if (looksLikeAction(token)) {
                add(candidates, candidate(
                    SymbolId.logicalPath(SymbolKind.ACTION_PATH, projectKey, moduleKey, "src/main/webapp", stripActionSuffix(token), null),
                    66,
                    suggestedParameter,
                    "按 Struts action 路径推断"
                ));
            }
            if (looksLikeTableQuestion(query, token)) {
                add(candidates, candidate(
                    SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, moduleKey, "_database", token, null),
                    64,
                    suggestedParameter,
                    "按数据表名称推断"
                ));
            }
            if (looksLikeParameterQuestion(query, token)) {
                add(candidates, candidate(
                    SymbolId.logicalPath(SymbolKind.REQUEST_PARAMETER, projectKey, moduleKey, "_request", token, null),
                    64,
                    suggestedParameter,
                    "按输入参数名称推断"
                ));
            }
        }
    }

    private BusinessQueryCandidate candidate(SymbolId symbolId, int score, String suggestedParameter, String reason) {
        return new BusinessQueryCandidate(symbolId, symbolId.kind(), displayName(symbolId), score, suggestedParameter, reason);
    }

    private void add(Map<String, BusinessQueryCandidate> candidates, BusinessQueryCandidate candidate) {
        candidates.merge(candidate.symbolId().value(), candidate, (left, right) -> left.score() >= right.score() ? left : right);
    }

    private String suggestedParameter(QueryPlan plan) {
        if (plan.requiredParameters().contains("changedSymbol")) {
            return "changedSymbol";
        }
        if (plan.requiredParameters().contains("diffText")) {
            return "diffText";
        }
        return "symbolId";
    }

    private boolean looksLikeJsp(String token) {
        return token.endsWith(".jsp") || token.endsWith(".jspx");
    }

    private boolean looksLikeAction(String token) {
        return token.endsWith(".do") || token.startsWith("/");
    }

    private String stripActionSuffix(String token) {
        String value = token.startsWith("/") ? token.substring(1) : token;
        return value.endsWith(".do") ? value.substring(0, value.length() - 3) : value;
    }

    private boolean looksLikeTableQuestion(String query, String token) {
        return containsAny(query, " table", "数据表", "表", "database", "db", "sql")
            && !token.endsWith(".jsp")
            && !token.endsWith(".do")
            && !token.contains("#");
    }

    private boolean looksLikeParameterQuestion(String query, String token) {
        return containsAny(query, "parameter", "param", "variable", "变量", "参数", "输入值", "从哪里", "到哪里", "去哪里", "去了哪里", "来源", "流向", "流到")
            && !token.endsWith(".jsp")
            && !token.endsWith(".do")
            && !token.contains("/");
    }

    private boolean containsAny(String value, String... needles) {
        String normalized = normalize(value);
        for (String needle : needles) {
            if (normalized.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private List<String> tokens(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char current = query.charAt(i);
            if (Character.isLetterOrDigit(current) || current == '_' || current == '/' || current == '.' || current == '#') {
                token.append(current);
            } else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .filter(value -> !stopWords().contains(normalize(value)))
            .distinct()
            .toList();
    }

    private Set<String> stopWords() {
        return Set.of("the", "and", "or", "from", "where", "what", "which", "who", "does", "this", "that", "看看", "哪些", "什么");
    }

    private String displayName(SymbolId symbol) {
        if (symbol.memberName() != null) {
            return symbol.ownerQualifiedName() + "#" + symbol.memberName();
        }
        if (symbol.localId() != null) {
            return symbol.ownerQualifiedName() + "#" + symbol.localId();
        }
        return symbol.ownerQualifiedName() == null ? symbol.value() : symbol.ownerQualifiedName();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
