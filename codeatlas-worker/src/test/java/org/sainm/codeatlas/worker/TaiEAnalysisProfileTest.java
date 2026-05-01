package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaiEAnalysisProfileTest {
    @Test
    void providesCallGraphAnalysisId() {
        assertEquals(List.of("cg"), TaiEAnalysisProfile.callGraph().analysisOptions());
    }

    @Test
    void providesPointerAnalysisWithTimeLimit() {
        List<String> options = TaiEAnalysisProfile.pointerAnalysis("2-type", Duration.ofSeconds(90)).analysisOptions();

        assertEquals(1, options.size());
        assertEquals("pta=cs:2-type;time-limit:90;", options.getFirst());
    }

    @Test
    void providesTaintAnalysisThroughPointerAnalysisPluginConfig() {
        List<String> options = TaiEAnalysisProfile.taintAnalysis(
            "ci",
            Path.of("D:/codeatlas/taint.yml"),
            Duration.ofSeconds(45)
        ).analysisOptions();

        assertEquals(1, options.size());
        assertTrue(options.getFirst().startsWith("pta=cs:ci;"));
        assertTrue(options.getFirst().contains("taint-config:D:/codeatlas/taint.yml;"));
        assertTrue(options.getFirst().contains("time-limit:45;"));
    }
}
