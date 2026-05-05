package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.List;

import org.sainm.codeatlas.facts.FactRecord;

/**
 * Composite pipeline that applies multiple fact mappers to a single
 * {@link JavaSourceAnalysisResult}, combining their outputs.
 *
 * <p>This ensures native method detection, variable tracing, and other
 * specialized mappers run alongside the primary {@link JavaSourceFactMapper}.
 */
public final class JavaSourcePipeline {
    private final List<Mapper> mappers;

    private JavaSourcePipeline(List<Mapper> mappers) {
        this.mappers = List.copyOf(mappers);
    }

    public static JavaSourcePipeline defaults() {
        return new JavaSourcePipeline(List.of(
                new Mapper.JavaSourceMapper(JavaSourceFactMapper.defaults()),
                new Mapper.NativeMethodMapper(NativeMethodFactMapper.defaults())));
    }

    public JavaSourceFactBatch map(
            JavaSourceAnalysisResult result,
            JavaSourceFactContext context) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        List<FactRecord> allFacts = new ArrayList<>();
        List<org.sainm.codeatlas.facts.Evidence> allEvidence = new ArrayList<>();
        for (Mapper mapper : mappers) {
            JavaSourceFactBatch batch = mapper.map(result, context);
            allFacts.addAll(batch.facts());
            allEvidence.addAll(batch.evidence());
        }
        return new JavaSourceFactBatch(allFacts, allEvidence);
    }

    /**
     * Sealed interface for type-safe mapper dispatch.
     */
    public sealed interface Mapper {
        JavaSourceFactBatch map(JavaSourceAnalysisResult result, JavaSourceFactContext context);

        record JavaSourceMapper(JavaSourceFactMapper mapper) implements Mapper {
            @Override
            public JavaSourceFactBatch map(JavaSourceAnalysisResult result, JavaSourceFactContext context) {
                return mapper.map(result, context);
            }
        }

        record NativeMethodMapper(NativeMethodFactMapper mapper) implements Mapper {
            @Override
            public JavaSourceFactBatch map(JavaSourceAnalysisResult result, JavaSourceFactContext context) {
                return mapper.map(result, context);
            }
        }
    }
}
