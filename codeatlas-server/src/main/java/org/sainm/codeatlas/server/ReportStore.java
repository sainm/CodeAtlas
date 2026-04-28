package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.impact.ImpactReport;
import java.util.Optional;

public interface ReportStore {
    Optional<ImpactReport> findReport(String reportId);

    void putReport(ImpactReport report);
}
