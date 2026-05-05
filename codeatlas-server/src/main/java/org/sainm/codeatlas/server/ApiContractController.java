package org.sainm.codeatlas.server;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApiContractController {
    private static final int MAX_LIMIT = 100;

    @GetMapping("/workspaces")
    public ApiEnvelope<ListResponse<Map<String, String>>> workspaces(
            @RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "name") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of(Map.of("workspaceId", "local", "name", "Local Workspace"))));
    }

    @GetMapping("/projects")
    public ApiEnvelope<ListResponse<Map<String, String>>> projects(
            @RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "projectId") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of(Map.of("projectId", "shop", "name", "shop"))));
    }

    @GetMapping("/import-reviews")
    public ApiEnvelope<ListResponse<Map<String, String>>> importReviews(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/analysis-runs")
    public ApiEnvelope<ListResponse<Map<String, String>>> analysisRuns(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/snapshots")
    public ApiEnvelope<ListResponse<Map<String, String>>> snapshots(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "snapshotId") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of(Map.of("snapshotId", snapshotId, "status", "COMMITTED"))));
    }

    @GetMapping("/projects/overview")
    public ApiEnvelope<Map<String, Object>> projectOverview(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        requireProject(projectId);
        return envelope(snapshotId, Map.of("projectId", projectId, "capabilities", List.of(), "blindSpots", List.of()));
    }

    @GetMapping("/symbols/search")
    public ApiEnvelope<ListResponse<Map<String, String>>> symbolSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "score") String sort) {
        if (q.isBlank()) {
            throw badRequest("MISSING_QUERY", "q is required");
        }
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/graph/callers")
    public ApiEnvelope<Map<String, Object>> callers(@RequestParam String symbolId, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return query(symbolId, snapshotId, "callers");
    }

    @GetMapping("/graph/callees")
    public ApiEnvelope<Map<String, Object>> callees(@RequestParam String symbolId, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return query(symbolId, snapshotId, "callees");
    }

    @GetMapping("/graph/paths")
    public ApiEnvelope<Map<String, Object>> paths(@RequestParam String from, @RequestParam String to, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("from", from, "to", to, "paths", List.of()));
    }

    @PostMapping("/impact/analyze-diff")
    public JobResponse analyzeDiff() {
        return new JobResponse("job-impact-1", "report-impact-1", "QUEUED");
    }

    @GetMapping("/impact/reports")
    public ApiEnvelope<ListResponse<Map<String, String>>> impactReports(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/variable-trace")
    public ApiEnvelope<Map<String, Object>> variableTrace(@RequestParam String symbolId, @RequestParam(defaultValue = "combined") String mode,
            @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("symbolId", symbolId, "mode", mode, "paths", List.of()));
    }

    @GetMapping("/db-impact")
    public ApiEnvelope<Map<String, Object>> dbImpact(@RequestParam String identityId, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("identityId", identityId, "read", List.of(), "write", List.of(), "display", List.of(), "test", List.of()));
    }

    @PostMapping("/features/plan-change")
    public JobResponse planFeatureChange() {
        return new JobResponse("job-feature-change-1", "change-plan-1", "QUEUED");
    }

    @PostMapping("/features/plan-addition")
    public JobResponse planFeatureAddition() {
        return new JobResponse("job-feature-addition-1", "change-plan-2", "QUEUED");
    }

    @GetMapping("/architecture-health")
    public ApiEnvelope<Map<String, Object>> architectureHealth(@RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("hotspots", List.of(), "cycles", List.of(), "dynamicRisks", List.of(), "boundaryRisks", List.of()));
    }

    @GetMapping("/jsp-backend-flow")
    public ApiEnvelope<Map<String, Object>> jspBackendFlow(@RequestParam String identityId, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("identityId", identityId, "paths", List.of()));
    }

    @GetMapping("/sql-table-impact")
    public ApiEnvelope<Map<String, Object>> sqlTableImpact(@RequestParam String identityId, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("identityId", identityId, "paths", List.of()));
    }

    @GetMapping("/reports")
    public ApiEnvelope<ListResponse<Map<String, String>>> reports(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/evidence")
    public ApiEnvelope<Map<String, Object>> evidence(@RequestParam String evidenceKey, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("evidenceKey", evidenceKey));
    }

    @GetMapping("/saved-queries")
    public ApiEnvelope<ListResponse<Map<String, String>>> savedQueries(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "name") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/subscriptions")
    public ApiEnvelope<ListResponse<Map<String, String>>> subscriptions(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/review-threads")
    public ApiEnvelope<ListResponse<Map<String, String>>> reviewThreads(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/policies")
    public ApiEnvelope<ListResponse<Map<String, String>>> policies(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "name") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/ci-checks")
    public ApiEnvelope<ListResponse<Map<String, String>>> ciChecks(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @GetMapping("/exports")
    public ApiEnvelope<ListResponse<Map<String, String>>> exports(@RequestParam(defaultValue = "latest-committed") String snapshotId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return envelope(snapshotId, list(offset, limit, sort, List.of()));
    }

    @DeleteMapping("/admin/projects")
    public Map<String, String> deleteProject(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "false") boolean confirm,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        requireConfirmed(confirm, idempotencyKey);
        return Map.of("projectId", projectId, "status", "accepted");
    }

    @GetMapping("/query/plan")
    public ApiEnvelope<Map<String, Object>> planQuery(@RequestParam String q, @RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("query", q, "plan", List.of()));
    }

    @GetMapping("/result-view-contract")
    public ApiEnvelope<Map<String, Object>> resultViewContract(@RequestParam(defaultValue = "latest-committed") String snapshotId) {
        return envelope(snapshotId, Map.of("views", List.of("summary", "paths", "evidence", "rawJson")));
    }

    private static <T> ApiEnvelope<T> envelope(String snapshotId, T data) {
        return new ApiEnvelope<>(snapshotId, data);
    }

    private static <T> ListResponse<T> list(int offset, int limit, String sort, List<T> items) {
        if (offset < 0 || limit < 0) {
            throw badRequest("INVALID_PAGING", "offset and limit must be non-negative");
        }
        if (limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", "limit exceeds maximum", "maxLimit=" + MAX_LIMIT, false);
        }
        return new ListResponse<>(items, offset, limit, sort);
    }

    private static ApiEnvelope<Map<String, Object>> query(String symbolId, String snapshotId, String mode) {
        return envelope(snapshotId, Map.of("symbolId", symbolId, "mode", mode, "paths", List.of()));
    }

    private static void requireProject(String projectId) {
        if (!"shop".equals(projectId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_FORBIDDEN", "project is not allowed", projectId, false);
        }
    }

    private static void requireConfirmed(boolean confirm, String idempotencyKey) {
        if (!confirm && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONFIRMATION_REQUIRED", "management operation requires confirm=true or Idempotency-Key", "", false);
        }
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, "", false);
    }
}
