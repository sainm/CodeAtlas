package org.sainm.codeatlas.ai.rag;

public interface EmbeddingProvider {
    EmbeddingVector embed(String text);
}
