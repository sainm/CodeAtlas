package org.sainm.codeatlas.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record TaiEAnalysisProfile(
    List<String> analysisOptions
) {
    public TaiEAnalysisProfile {
        if (analysisOptions == null || analysisOptions.isEmpty()) {
            throw new IllegalArgumentException("analysisOptions are required");
        }
        analysisOptions = analysisOptions.stream()
            .filter(option -> option != null && !option.isBlank())
            .map(String::trim)
            .toList();
        if (analysisOptions.isEmpty()) {
            throw new IllegalArgumentException("analysisOptions are required");
        }
    }

    public static TaiEAnalysisProfile callGraph() {
        return new TaiEAnalysisProfile(List.of("cg"));
    }

    public static TaiEAnalysisProfile pointerAnalysis(String contextSensitivity, Duration timeLimit) {
        return new TaiEAnalysisProfile(List.of(pointerOption(contextSensitivity, timeLimit, "")));
    }

    public static TaiEAnalysisProfile taintAnalysis(String contextSensitivity, Path taintConfig, Duration timeLimit) {
        if (taintConfig == null) {
            throw new IllegalArgumentException("taintConfig is required");
        }
        String config = taintConfig.toString().replace('\\', '/');
        return new TaiEAnalysisProfile(List.of(pointerOption(contextSensitivity, timeLimit, "taint-config:" + config + ";")));
    }

    private static String pointerOption(String contextSensitivity, Duration timeLimit, String extraOptions) {
        String cs = contextSensitivity == null || contextSensitivity.isBlank() ? "ci" : contextSensitivity.trim();
        long seconds = Math.max(1L, timeLimit == null ? 60L : timeLimit.toSeconds());
        return "pta=cs:" + cs + ";time-limit:" + seconds + ";" + extraOptions;
    }
}
