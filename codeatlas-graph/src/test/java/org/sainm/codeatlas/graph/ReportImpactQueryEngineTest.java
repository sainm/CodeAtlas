package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.SourceType;

class ReportImpactQueryEngineTest {
    @Test
    void findsReportsAffectedByDbColumnChange() {
        String columnId = "db-column://shop/mainDs/public/users#email";
        String sqlId = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#find";
        String reportDefId = "report-definition://shop/_root/report/user-list";

        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(sqlId, columnId, "READS_COLUMN"),
                fact(sqlId, reportDefId, "EXPORTS_SYMBOL")));

        ReportImpactQueryEngine.ReportImpactResult result = ReportImpactQueryEngine.defaults()
                .findAffectedReports(report, columnId);

        assertEquals(columnId, result.queryId());
        assertEquals(1, result.columnFacts().size());
        assertEquals(1, result.reportFacts().size());
        assertEquals(1, result.reversePaths().size());
        assertTrue(result.reversePaths().getFirst().identityIds().contains(reportDefId));
    }

    @Test
    void findsColumnsAffectingReport() {
        String columnId = "db-column://shop/mainDs/public/users#name";
        String sqlId = "sql-statement://shop/_root/src/main/resources/com/acme/UserMapper.xml#findByName";
        String reportDefId = "report-definition://shop/_root/report/staff-directory";

        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(sqlId, columnId, "READS_COLUMN"),
                fact(sqlId, reportDefId, "REFERENCES_SYMBOL")));

        ReportImpactQueryEngine.ReportImpactResult result = ReportImpactQueryEngine.defaults()
                .findAffectedColumns(report, reportDefId);

        assertEquals(reportDefId, result.queryId());
        assertEquals(1, result.columnFacts().size());
        assertEquals(1, result.reportFacts().size());
        assertEquals(1, result.reversePaths().size());
    }

    @Test
    void deduplicatesReportPaths() {
        String columnId = "db-column://shop/mainDs/public/reports#export_date";
        String sqlId = "sql-statement://shop/_root/src/main/resources/com/acme/ReportMapper.xml#load";
        String reportDefId = "report-definition://shop/_root/report/annual";

        CurrentFactReport report = CurrentFactReport.from("shop", List.of(
                fact(sqlId, columnId, "READS_COLUMN"),
                fact(sqlId, reportDefId, "EXPORTS_SYMBOL"),
                fact(sqlId, reportDefId, "REFERENCES_SYMBOL")));

        ReportImpactQueryEngine.ReportImpactResult result = ReportImpactQueryEngine.defaults()
                .findAffectedReports(report, columnId);

        assertEquals(1, result.reversePaths().size());
    }

    private static FactRecord fact(String source, String target, String relation) {
        return FactRecord.create(
                List.of("src/main/java"),
                source,
                target,
                relation,
                "direct",
                "shop",
                "snapshot-1",
                "analysis-1",
                "scope-1",
                "test-analyzer",
                "src/main/java",
                "evidence-1",
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }
}
