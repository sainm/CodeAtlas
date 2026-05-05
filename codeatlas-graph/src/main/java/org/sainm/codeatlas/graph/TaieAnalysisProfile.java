package org.sainm.codeatlas.graph;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for Tai-e static analysis profiles.
 *
 * <p>Each profile specifies the analysis type, resource limits,
 * and degradation behavior when analysis fails or times out.
 */
public final class TaieAnalysisProfile {
    private final String name;
    private final AnalysisType analysisType;
    private final Duration timeout;
    private final long maxHeapBytes;
    private final boolean allowDegradation;
    private final List<String> classpathEntries;

    private TaieAnalysisProfile(
            String name,
            AnalysisType analysisType,
            Duration timeout,
            long maxHeapBytes,
            boolean allowDegradation,
            List<String> classpathEntries) {
        this.name = requireNonBlank(name, "name");
        this.analysisType = Objects.requireNonNull(analysisType, "analysisType");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.maxHeapBytes = maxHeapBytes;
        this.allowDegradation = allowDegradation;
        this.classpathEntries = List.copyOf(Objects.requireNonNull(classpathEntries, "classpathEntries"));
    }

    public static TaieAnalysisProfile define(
            String name,
            AnalysisType analysisType,
            Duration timeout,
            long maxHeapBytes,
            boolean allowDegradation,
            List<String> classpathEntries) {
        return new TaieAnalysisProfile(name, analysisType, timeout, maxHeapBytes, allowDegradation, classpathEntries);
    }

    public String name() {
        return name;
    }

    public AnalysisType analysisType() {
        return analysisType;
    }

    public Duration timeout() {
        return timeout;
    }

    public long maxHeapBytes() {
        return maxHeapBytes;
    }

    public boolean allowDegradation() {
        return allowDegradation;
    }

    public List<String> classpathEntries() {
        return classpathEntries;
    }

    public enum AnalysisType {
        CALL_GRAPH,
        POINTER_ANALYSIS,
        TAINT_ANALYSIS,
        COMBINED
    }

    /**
     * Maps a Tai-e method signature to a CodeAtlas SymbolId.
     */
    public record SignatureMapping(
            String taieSignature,
            String codeAtlasSymbolId,
            double confidenceScore) {
        public SignatureMapping {
            requireNonBlank(taieSignature, "taieSignature");
            requireNonBlank(codeAtlasSymbolId, "codeAtlasSymbolId");
            if (confidenceScore < 0.0 || confidenceScore > 1.0) {
                throw new IllegalArgumentException("confidenceScore must be in [0,1]");
            }
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
