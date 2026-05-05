package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkdownImpactReportExporterTest {
    @Test
    void exportsFastImpactReportAsMarkdown() {
        FastImpactReport report = new FastImpactReport(
                "shop",
                "snapshot-1",
                List.of("method://shop/_root/src/main/java/com.acme.UserService#load()V"),
                List.of(new ImpactPath(List.of("a", "b"))),
                List.of(new DbImpactQueryResult("db-table://shop/mainDs/public/users", List.of(), List.of())),
                false);

        String markdown = MarkdownImpactReportExporter.defaults().export(report);

        assertTrue(markdown.startsWith("# Fast Impact Report"));
        assertTrue(markdown.contains("## Changed Symbols"));
        assertTrue(markdown.contains("## Affected Symbols"));
        assertTrue(markdown.contains("## Impact Paths"));
        assertTrue(markdown.contains("## DB Impacts"));
        assertTrue(markdown.contains("## Suggested Tests"));
        assertTrue(markdown.contains("db-table://shop/mainDs/public/users"));
    }
}
