package org.sainm.codeatlas.server;

import java.util.List;
import java.util.Map;

public record QueryPlan(
    String intent,
    String endpoint,
    String method,
    String summary,
    List<String> requiredParameters,
    Map<String, String> defaultParameters,
    List<String> relationTypes,
    String resultView
) {
    public QueryPlan {
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("intent is required");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        method = method == null || method.isBlank() ? "GET" : method.trim();
        summary = summary == null ? "" : summary.trim();
        requiredParameters = List.copyOf(requiredParameters);
        defaultParameters = Map.copyOf(defaultParameters);
        relationTypes = List.copyOf(relationTypes);
        resultView = resultView == null || resultView.isBlank() ? "DETAIL" : resultView.trim();
    }
}
