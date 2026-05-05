package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluationSampleSetTest {
    @Test
    void calculatesPrecisionRecallAndFScore() {
        ImpactPath tp = new ImpactPath(List.of("a", "b", "c"));
        ImpactPath fp = new ImpactPath(List.of("x", "y"));

        EvaluationSampleSet sampleSet = EvaluationSampleSet.define(
                "test-set",
                "test evaluation",
                List.of(new EvaluationSampleSet.LabeledPath(tp, "expected path", "should find")),
                List.of(new EvaluationSampleSet.LabeledPath(fp, "noise path", "should not find")));

        EvaluationSampleSet.EvaluationResult result = sampleSet.evaluate(List.of(tp, fp));

        assertEquals(2, result.totalReported());
        assertEquals(1, result.truePositive());
        assertEquals(1, result.falsePositive());
        assertEquals(0, result.falseNegative());
        assertTrue(result.summary().contains("precision=0.50"));
    }

    @Test
    void perfectDetectionReturnsFullScore() {
        ImpactPath path = new ImpactPath(List.of("a", "b"));
        EvaluationSampleSet sampleSet = EvaluationSampleSet.define(
                "perfect",
                "all correct",
                List.of(new EvaluationSampleSet.LabeledPath(path, "correct", "")),
                List.of());

        EvaluationSampleSet.EvaluationResult result = sampleSet.evaluate(List.of(path));

        assertEquals(1, result.truePositive());
        assertEquals(0, result.falsePositive());
        assertEquals(0, result.falseNegative());
        assertEquals(1.0, result.precision());
        assertEquals(1.0, result.recall());
    }

    @Test
    void missingTruePositiveIncrementsFalseNegative() {
        ImpactPath expected = new ImpactPath(List.of("a", "b", "c"));
        EvaluationSampleSet sampleSet = EvaluationSampleSet.define(
                "missing",
                "missing path",
                List.of(new EvaluationSampleSet.LabeledPath(expected, "expected", "")),
                List.of());

        EvaluationSampleSet.EvaluationResult result = sampleSet.evaluate(List.of());

        assertEquals(0, result.truePositive());
        assertEquals(1, result.falseNegative());
        assertEquals(0.0, result.recall());
    }
}
