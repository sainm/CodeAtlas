package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.impact.ImpactReport;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryReportStore implements ReportStore {
    private final Map<String, ImpactReport> reports = new ConcurrentHashMap<>();

    @Override
    public Optional<ImpactReport> findReport(String reportId) {
        return Optional.ofNullable(reports.get(reportId));
    }

    @Override
    public void putReport(ImpactReport report) {
        reports.put(report.reportId(), report);
    }
}
