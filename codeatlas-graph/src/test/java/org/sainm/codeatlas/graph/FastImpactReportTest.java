package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.SourceType;

class FastImpactReportTest {
    @Test
    void rendersStableJsonContract() {
        FastImpactReport report = new FastImpactReport(
                "shop",
                "snapshot-1",
                List.of("method://shop/_root/src/main/java/com.acme.UserService#load()V"),
                List.of(new ImpactPath(List.of(
                        "method://shop/_root/src/main/java/com.acme.UserService#load()V",
                        "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#find"))),
                List.of(new ImpactPathDetail(
                        new ImpactPath(List.of("a", "b")),
                        "MEDIUM",
                        Confidence.LIKELY,
                        SourceType.SQL,
                        List.of("evidence-1"))),
                List.of(new DbImpactQueryResult("db-table://shop/mainDs/public/users", List.of(), List.of())),
                List.of("db-table://shop/mainDs/public/users"),
                List.of("Run mapper integration tests"),
                false,
                true);

        String json = report.toJson();

        assertTrue(json.contains("\"projectId\":\"shop\""));
        assertTrue(json.contains("\"snapshotId\":\"snapshot-1\""));
        assertTrue(json.contains("\"changedSymbols\""));
        assertTrue(json.contains("\"affectedSymbols\""));
        assertTrue(json.contains("\"paths\""));
        assertTrue(json.contains("\"pathDetails\""));
        assertTrue(json.contains("\"risk\":\"MEDIUM\""));
        assertTrue(json.contains("\"confidence\":\"LIKELY\""));
        assertTrue(json.contains("\"sourceType\":\"SQL\""));
        assertTrue(json.contains("\"evidenceKeys\""));
        assertTrue(json.contains("\"dbImpacts\""));
        assertTrue(json.contains("\"suggestedTests\""));
        assertTrue(json.contains("\"aiEnabled\":false"));
        assertTrue(json.contains("\"truncated\":true"));
    }
}
