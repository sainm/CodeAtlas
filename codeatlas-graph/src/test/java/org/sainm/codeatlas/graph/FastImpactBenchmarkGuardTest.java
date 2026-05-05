package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class FastImpactBenchmarkGuardTest {
    @Test
    void fastCallerReportStaysWithinTenSecondBenchmarkGuard() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            int methodCount = 750;
            List<FactRecord> facts = new ArrayList<>();
            for (int i = 1; i < methodCount; i++) {
                facts.add(call(method(i), method(i - 1)));
            }
            CurrentFactReport report = CurrentFactReport.from("shop", facts);
            String changedMethod = method(0);

            CallerTraversalResult result = CallerTraversalEngine.defaults()
                    .findCallers(report, changedMethod, methodCount, methodCount);

            assertFalse(result.callerPaths().isEmpty());
        });
    }

    private static FactRecord call(String source, String target) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                "CALLS",
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static String method(int index) {
        return "method://shop/_root/src/main/java/com.acme.Service" + index + "#run()V";
    }
}
