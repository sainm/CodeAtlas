package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;
import org.sainm.codeatlas.facts.FactStore;
import org.sainm.codeatlas.facts.InMemoryFactStore;
import org.sainm.codeatlas.facts.SourceType;

/**
 * Small fixture benchmark (~10 methods across Controller/Service/DAO layers
 * with SQL table/column bindings).
 *
 * <p>Exercises: scan time → graph write → JVM cache heap → impact report latency.
 * Results are recorded against the "small-fixture" BenchmarkProfile.
 */
class SmallFixtureBenchmarkTest {

    private static final String PROJ = "benchmark-shop";
    private static final String SNAP = "snap-1";
    private static final String RUN = "run-1";
    private static final String SCOPE = "scope-1";
    private static final String SRC = "src/main/java";

    private static String m(String name) {
        return "method://" + PROJ + "/_root/" + SRC + "/com/acme/" + name + "#run()V";
    }

    private static String sql(String name) {
        return "sql-statement://" + PROJ + "/_root/src/main/resources/mapper.xml#" + name;
    }

    private static String dbTable(String name) {
        return "db-table://" + PROJ + "/mainDs/public/" + name;
    }

    private static String dbColumn(String table, String col) {
        return "db-column://" + PROJ + "/mainDs/public/" + table + "#" + col;
    }

    private static FactRecord call(String src, String tgt) {
        return FactRecord.create(List.of(SRC), src, tgt, "CALLS", "direct",
                PROJ, SNAP, RUN, SCOPE, "spoon", SRC,
                "ev-call-" + src.hashCode() + "-" + tgt.hashCode(),
                Confidence.CERTAIN, 100, SourceType.SPOON);
    }

    private static FactRecord readsTable(String src, String tbl) {
        return FactRecord.create(List.of("src/main/resources"), src, tbl, "READS_TABLE", "sql",
                PROJ, SNAP, RUN, SCOPE, "sql-table", "src/main/resources",
                "ev-rt-" + src.hashCode(), Confidence.CERTAIN, 100, SourceType.SQL);
    }

    private static FactRecord writesTable(String src, String tbl) {
        return FactRecord.create(List.of("src/main/resources"), src, tbl, "WRITES_TABLE", "sql",
                PROJ, SNAP, RUN, SCOPE, "sql-table", "src/main/resources",
                "ev-wt-" + src.hashCode(), Confidence.CERTAIN, 100, SourceType.SQL);
    }

    private static FactRecord readsColumn(String src, String table, String col) {
        return FactRecord.create(List.of("src/main/resources"),
                src, dbColumn(table, col), "READS_COLUMN", "sql-col",
                PROJ, SNAP, RUN, SCOPE, "sql-table", "src/main/resources",
                "ev-rc-" + src.hashCode() + "-" + col,
                Confidence.CERTAIN, 100, SourceType.SQL);
    }

    private static FactRecord bindsTo(String method, String sqlStmt) {
        return FactRecord.create(List.of(SRC), method, sqlStmt, "BINDS_TO", "mybatis",
                PROJ, SNAP, RUN, SCOPE, "mybatis", SRC,
                "ev-bind-" + method.hashCode(), Confidence.CERTAIN, 100, SourceType.XML);
    }

    private List<FactRecord> buildFixture() {
        String ctl = m("OrderController");
        String svc = m("OrderService");
        String dao = m("OrderDao");
        String sqlSel = sql("selectOrders");
        String sqlIns = sql("insertAudit");
        String tbl1 = dbTable("orders");
        String tbl2 = dbTable("audit_log");

        List<FactRecord> facts = new ArrayList<>();
        facts.add(call(ctl, svc));
        facts.add(call(svc, dao));
        facts.add(bindsTo(dao, sqlSel));
        facts.add(bindsTo(dao, sqlIns));
        facts.add(readsTable(sqlSel, tbl1));
        facts.add(writesTable(sqlIns, tbl2));
        facts.add(readsColumn(sqlSel, "orders", "id"));
        facts.add(readsColumn(sqlSel, "orders", "status"));
        facts.add(readsColumn(sqlIns, "audit_log", "id"));
        facts.add(readsColumn(sqlIns, "audit_log", "entry"));
        return facts;
    }

    @Test
    void smallFixtureMeets5SecondScanAndWriteTarget() {
        BenchmarkProfile profile = BenchmarkRegistry.defaults().require("small-fixture");

        long scanStart = System.nanoTime();
        List<FactRecord> facts = buildFixture();
        long scanNanos = System.nanoTime() - scanStart;

        long writeStart = System.nanoTime();
        FactStore store = InMemoryFactStore.defaults();
        store.insertAll(facts);
        long writeNanos = System.nanoTime() - writeStart;

        long reportStart = System.nanoTime();
        ImpactAnalysisService service = ImpactAnalysisService.using(store);
        FastImpactReport report = service.analyzeDiff(
                PROJ, SNAP, List.of(m("OrderDao")), 10, 100);
        long reportNanos = System.nanoTime() - reportStart;

        profile.record(new BenchmarkProfile.BenchmarkMeasurement(
                System.currentTimeMillis(),
                Duration.ofNanos(reportNanos),
                Duration.ofNanos((scanNanos + writeNanos + reportNanos) / 3),
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                facts.size(),
                (int) facts.stream().filter(f -> f.relationType().name().equals("CALLS")).count(),
                report.paths().size(),
                "local-test"));

        assertTrue(profile.meetsTarget(),
                "Small fixture must meet 5s target. "
                        + "Scan: " + Duration.ofNanos(scanNanos).toMillis() + "ms, "
                        + "Write: " + Duration.ofNanos(writeNanos).toMillis() + "ms, "
                        + "Report: " + Duration.ofNanos(reportNanos).toMillis() + "ms");
        assertFalse(report.paths().isEmpty(), "Should find at least one impact path");
    }

    @Test
    void impactReportLatencyMeets30SecondGuard() {
        BenchmarkProfile guard = BenchmarkRegistry.defaults().require("impact-report");
        List<FactRecord> facts = buildFixture();
        FactStore store = InMemoryFactStore.defaults();
        store.insertAll(facts);

        long start = System.nanoTime();
        ImpactAnalysisService service = ImpactAnalysisService.using(store);
        FastImpactReport report = service.analyzeDiff(
                PROJ, SNAP, List.of(m("OrderDao")), 10, 100);
        long elapsed = System.nanoTime() - start;

        guard.record(new BenchmarkProfile.BenchmarkMeasurement(
                System.currentTimeMillis(),
                Duration.ofNanos(elapsed),
                Duration.ofNanos(elapsed),
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                facts.size(), 2, report.paths().size(), "local-test"));

        assertTrue(guard.meetsTarget(),
                "Impact report must meet 30s guard. Elapsed: "
                        + Duration.ofNanos(elapsed).toMillis() + "ms");
    }

    @Test
    void downstreamTraversalFollowsBindAndReadEdges() {
        FactStore store = InMemoryFactStore.defaults();
        store.insertAll(buildFixture());
        ImpactAnalysisService service = ImpactAnalysisService.using(store);

        FastImpactReport report = service.analyzeDiff(
                PROJ, SNAP, List.of(m("OrderDao")), 10, 100);

        assertFalse(report.paths().isEmpty(),
                "Downstream from OrderDao should find bind→sql→table/column edges");
        assertTrue(report.paths().stream()
                .anyMatch(p -> p.identityIds().stream()
                        .anyMatch(id -> id.startsWith("sql-statement://"))),
                "Should find sql-statement nodes via BINDS_TO edge");
        assertTrue(report.paths().stream()
                .anyMatch(p -> p.identityIds().stream()
                        .anyMatch(id -> id.startsWith("db-table://"))),
                "Should find db-table nodes via READS_TABLE edge");
    }
}
