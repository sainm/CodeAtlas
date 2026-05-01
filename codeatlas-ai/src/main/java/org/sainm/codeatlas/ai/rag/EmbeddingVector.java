package org.sainm.codeatlas.ai.rag;

import java.util.Arrays;

public record EmbeddingVector(double[] values) {
    public EmbeddingVector {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values are required");
        }
        values = Arrays.copyOf(values, values.length);
    }

    @Override
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public double cosineSimilarity(EmbeddingVector other) {
        if (other == null) {
            return 0.0d;
        }
        double[] right = other.values;
        int dimensions = Math.min(values.length, right.length);
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int i = 0; i < dimensions; i++) {
            dot += values[i] * right[i];
            leftMagnitude += values[i] * values[i];
            rightMagnitude += right[i] * right[i];
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }
}
