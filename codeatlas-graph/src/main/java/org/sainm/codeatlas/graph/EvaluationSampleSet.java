package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ground-truth evaluation samples for measuring false-positive and
 * false-negative rates of impact analysis paths.
 */
public final class EvaluationSampleSet {
    private final String name;
    private final String description;
    private final List<LabeledPath> truePositives;
    private final List<LabeledPath> falsePositiveCandidates;

    private EvaluationSampleSet(
            String name,
            String description,
            List<LabeledPath> truePositives,
            List<LabeledPath> falsePositiveCandidates) {
        this.name = requireNonBlank(name, "name");
        this.description = requireNonBlank(description, "description");
        this.truePositives = List.copyOf(truePositives);
        this.falsePositiveCandidates = List.copyOf(falsePositiveCandidates);
    }

    public static EvaluationSampleSet define(
            String name,
            String description,
            List<LabeledPath> truePositives,
            List<LabeledPath> falsePositiveCandidates) {
        return new EvaluationSampleSet(name, description, truePositives, falsePositiveCandidates);
    }

    public EvaluationResult evaluate(List<ImpactPath> reportedPaths) {
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        Map<String, ImpactPath> reportedById = new LinkedHashMap<>();
        for (ImpactPath path : reportedPaths) {
            reportedById.put(pathKey(path), path);
        }
        for (LabeledPath expected : truePositives) {
            if (reportedById.containsKey(pathKey(expected.path()))) {
                truePositive++;
            } else {
                falseNegative++;
            }
        }
        for (LabeledPath candidate : falsePositiveCandidates) {
            if (reportedById.containsKey(pathKey(candidate.path()))) {
                falsePositive++;
            }
        }
        int totalReported = reportedPaths.size();
        double precision = totalReported == 0 ? 0.0
                : (double) truePositive / totalReported;
        double recall = truePositives.isEmpty() ? 1.0
                : (double) truePositive / truePositives.size();
        return new EvaluationResult(
                name,
                totalReported,
                truePositive,
                falsePositive,
                falseNegative,
                precision,
                recall);
    }

    private static String pathKey(ImpactPath path) {
        StringBuilder key = new StringBuilder();
        for (String id : path.identityIds()) {
            key.append(id).append('|');
        }
        return key.toString();
    }

    public record LabeledPath(
            ImpactPath path,
            String label,
            String notes) {
        public LabeledPath {
            Objects.requireNonNull(path, "path");
            label = requireNonBlank(label, "label");
            notes = notes == null ? "" : notes;
        }
    }

    public record EvaluationResult(
            String sampleSetName,
            int totalReported,
            int truePositive,
            int falsePositive,
            int falseNegative,
            double precision,
            double recall) {
        public String summary() {
            return String.format(
                    "%s: reported=%d, TP=%d, FP=%d, FN=%d, precision=%.2f, recall=%.2f",
                    sampleSetName,
                    totalReported,
                    truePositive,
                    falsePositive,
                    falseNegative,
                    precision,
                    recall);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
