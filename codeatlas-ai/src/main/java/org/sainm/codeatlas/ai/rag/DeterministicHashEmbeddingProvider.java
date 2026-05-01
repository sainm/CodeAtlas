package org.sainm.codeatlas.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DeterministicHashEmbeddingProvider implements EmbeddingProvider {
    private final int dimensions;

    public DeterministicHashEmbeddingProvider(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingVector embed(String text) {
        double[] values = new double[dimensions];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), dimensions);
            values[index] += 1.0d;
        }
        return new EmbeddingVector(values);
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                current.append(Character.toLowerCase(ch));
            } else if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }
}
