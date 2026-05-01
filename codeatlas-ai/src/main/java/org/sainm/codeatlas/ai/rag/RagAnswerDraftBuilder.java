package org.sainm.codeatlas.ai.rag;

import java.util.List;
import java.util.stream.Collectors;

public final class RagAnswerDraftBuilder {
    public String build(String question, List<RagSearchResult> results) {
        String safeQuestion = question == null || question.isBlank() ? "(empty question)" : question.trim();
        if (results == null || results.isEmpty()) {
            return "Question: " + safeQuestion + "\nNo static evidence was found. Try a more specific class, method, JSP, table, or parameter name.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append("Question: ").append(safeQuestion).append('\n');
        answer.append("Static answer draft based on ").append(results.size()).append(" evidence-backed result(s):\n");
        int index = 1;
        for (RagSearchResult result : results) {
            answer.append(index++).append(". ")
                .append(result.displayName())
                .append(" [")
                .append(result.symbolId().kind())
                .append("] score=")
                .append("%.2f".formatted(result.score()))
                .append(" matches=")
                .append(result.matchKinds())
                .append('\n');
            if (!result.summary().isBlank()) {
                answer.append("   Summary: ").append(result.summary()).append('\n');
            }
            if (!result.evidenceKeys().isEmpty()) {
                answer.append("   Evidence: ")
                    .append(result.evidenceKeys().stream().limit(3).collect(Collectors.joining("; ")))
                    .append('\n');
            }
        }
        return answer.toString().trim();
    }
}
