package org.sainm.codeatlas.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class McpContracts {
    private McpContracts() {
    }

    public static final List<String> READ_ONLY_TOOL_NAMES = List.of(
            "symbol.search",
            "graph.findCallers",
            "graph.findCallees",
            "graph.findImpactPaths",
            "impact.analyzeDiff",
            "db.findCodeImpacts",
            "variable.findImpacts",
            "jsp.findBackendFlow",
            "feature.planChange",
            "feature.planAddition",
            "rag.semanticSearch",
            "rag.answerDraft",
            "report.getImpactReport",
            "project.overview");

    public record McpToolDefinition(String name, boolean readOnly, String description) {
        public McpToolDefinition {
            name = requireText(name, "name");
            description = requireText(description, "description");
        }
    }

    public record McpResource(String uri, String title, String description) {
        public McpResource {
            uri = requireText(uri, "uri");
            title = requireText(title, "title");
            description = requireText(description, "description");
        }
    }

    public record McpPrompt(String name, String description, List<String> arguments) {
        public McpPrompt {
            name = requireText(name, "name");
            description = requireText(description, "description");
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }
    }

    public static final class McpCatalog {
        private final Map<String, McpToolDefinition> tools;
        private final List<McpResource> resources;
        private final List<McpPrompt> prompts;

        public McpCatalog(Map<String, McpToolDefinition> tools, List<McpResource> resources, List<McpPrompt> prompts) {
            this.tools = Map.copyOf(Objects.requireNonNull(tools, "tools"));
            this.resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
            this.prompts = List.copyOf(Objects.requireNonNull(prompts, "prompts"));
        }

        public static McpCatalog defaults() {
            Map<String, McpToolDefinition> tools = new LinkedHashMap<>();
            for (String name : READ_ONLY_TOOL_NAMES) {
                tools.put(name, new McpToolDefinition(name, true, "Read-only CodeAtlas tool: " + name));
            }
            return new McpCatalog(
                    tools,
                    List.of(
                            new McpResource("codeatlas://projects", "Projects", "Visible project summaries"),
                            new McpResource("codeatlas://reports", "Reports", "Read-only report artifacts"),
                            new McpResource("codeatlas://evidence", "Evidence", "Evidence snippets and paths")),
                    List.of(
                            new McpPrompt("impact-analysis", "Analyze change impact with cited evidence", List.of("projectId", "snapshotId", "query")),
                            new McpPrompt("db-impact", "Find code impacted by table or column changes", List.of("projectId", "snapshotId", "table", "column")),
                            new McpPrompt("feature-plan", "Plan a feature change or addition", List.of("projectId", "snapshotId", "description"))));
        }

        public Collection<McpToolDefinition> tools() {
            return tools.values();
        }

        public McpToolDefinition requireTool(String name) {
            McpToolDefinition tool = tools.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("unknown MCP tool: " + name);
            }
            return tool;
        }

        public Optional<McpToolDefinition> findTool(String name) {
            return Optional.ofNullable(tools.get(name));
        }

        public List<McpResource> resources() {
            return resources;
        }

        public List<McpPrompt> prompts() {
            return prompts;
        }
    }

    public interface ToolHandler {
        ToolResponse handle(ToolRequest request);
    }

    public record ToolRequest(
            String requestId,
            String principal,
            String projectId,
            String toolName,
            Map<String, String> arguments) {
        public ToolRequest {
            requestId = requireText(requestId, "requestId");
            principal = requireText(principal, "principal");
            projectId = requireText(projectId, "projectId");
            toolName = requireText(toolName, "toolName");
            arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }
    }

    public record ToolResponse(
            boolean accepted,
            String status,
            List<Map<String, String>> results,
            List<AgentEvidence> evidence,
            boolean truncated,
            Optional<String> rejectionReason) {
        public ToolResponse {
            status = requireText(status, "status");
            results = List.copyOf(Objects.requireNonNull(results, "results"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
        }

        public static ToolResponse rejected(String reason) {
            return new ToolResponse(false, "REJECTED", List.of(), List.of(), false, Optional.of(requireText(reason, "reason")));
        }

        public static ToolResponse ok(List<Map<String, String>> results, List<AgentEvidence> evidence, boolean truncated) {
            return new ToolResponse(true, "OK", results, evidence, truncated, Optional.empty());
        }
    }

    public static final class ReadOnlyMcpServer {
        private final McpCatalog catalog;
        private final ToolCallGuard guard;
        private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();

        public ReadOnlyMcpServer(McpCatalog catalog, ToolCallGuard guard) {
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            this.guard = Objects.requireNonNull(guard, "guard");
        }

        public void register(String toolName, ToolHandler handler) {
            McpToolDefinition tool = catalog.requireTool(toolName);
            if (!tool.readOnly()) {
                throw new IllegalArgumentException("only read-only tools can be registered");
            }
            handlers.put(toolName, Objects.requireNonNull(handler, "handler"));
        }

        public ToolResponse dispatch(AgentProfile profile, ToolRequest request) {
            Instant started = guard.clock().instant();
            Optional<McpToolDefinition> tool = catalog.findTool(request.toolName());
            if (tool.isEmpty()) {
                String reason = "unknown MCP tool";
                guard.audit(request, 0, started, Optional.of(reason));
                return ToolResponse.rejected(reason);
            }
            GuardDecision decision = guard.authorize(profile, tool.get(), request);
            if (!decision.allowed()) {
                guard.audit(request, 0, started, Optional.of(decision.reason()));
                return ToolResponse.rejected(decision.reason());
            }
            ToolHandler handler = handlers.get(request.toolName());
            if (handler == null) {
                guard.audit(request, 0, started, Optional.of("tool handler not registered"));
                return ToolResponse.rejected("tool handler not registered");
            }
            ToolResponse response = handler.handle(request);
            guard.audit(request, response.results().size(), started, response.rejectionReason());
            return response;
        }
    }

    public enum AgentType {
        IMPACT_ANALYSIS,
        DB_IMPACT,
        VARIABLE_TRACE,
        VARIABLE_IMPACT,
        FEATURE_CHANGE_PLAN,
        FEATURE_ADDITION_PLAN,
        CODE_QUESTION
    }

    public enum AgentStatus {
        CREATED,
        PLANNING,
        WAITING_FOR_USER,
        RUNNING_FAST,
        FAST_READY,
        RUNNING_DEEP,
        COMPLETED,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public record AgentProfile(AgentType agentType, Set<String> allowedTools, String outputContract) {
        public AgentProfile {
            agentType = Objects.requireNonNull(agentType, "agentType");
            allowedTools = Set.copyOf(Objects.requireNonNull(allowedTools, "allowedTools"));
            outputContract = requireText(outputContract, "outputContract");
        }
    }

    public static final class AgentProfileRegistry {
        private final Map<AgentType, AgentProfile> profiles;

        private AgentProfileRegistry(Map<AgentType, AgentProfile> profiles) {
            this.profiles = Map.copyOf(profiles);
        }

        public static AgentProfileRegistry defaults() {
            Map<AgentType, AgentProfile> profiles = new EnumMap<>(AgentType.class);
            profiles.put(AgentType.IMPACT_ANALYSIS, profile(AgentType.IMPACT_ANALYSIS, "ImpactReport", "symbol.search", "graph.findImpactPaths", "impact.analyzeDiff", "report.getImpactReport"));
            profiles.put(AgentType.DB_IMPACT, profile(AgentType.DB_IMPACT, "DbImpactReport with table fallback and column-confirmed impact", "db.findCodeImpacts", "graph.findImpactPaths", "report.getImpactReport"));
            profiles.put(AgentType.VARIABLE_TRACE, profile(AgentType.VARIABLE_TRACE, "VariableTraceReport", "variable.findImpacts", "symbol.search"));
            profiles.put(AgentType.VARIABLE_IMPACT, profile(AgentType.VARIABLE_IMPACT, "VariableImpactReport with sources, sinks and impact range", "variable.findImpacts", "graph.findImpactPaths"));
            profiles.put(AgentType.FEATURE_CHANGE_PLAN, profile(AgentType.FEATURE_CHANGE_PLAN, "ChangePlanReport", "feature.planChange", "rag.semanticSearch", "rag.answerDraft"));
            profiles.put(AgentType.FEATURE_ADDITION_PLAN, profile(AgentType.FEATURE_ADDITION_PLAN, "FeatureAdditionPlan with references, landing points, risks and tests", "feature.planAddition", "rag.semanticSearch", "rag.answerDraft"));
            profiles.put(AgentType.CODE_QUESTION, profile(AgentType.CODE_QUESTION, "Evidence-backed answer draft", "symbol.search", "rag.semanticSearch", "rag.answerDraft", "project.overview"));
            return new AgentProfileRegistry(profiles);
        }

        public AgentProfile require(AgentType type) {
            return profiles.get(Objects.requireNonNull(type, "type"));
        }

        private static AgentProfile profile(AgentType type, String outputContract, String... tools) {
            return new AgentProfile(type, Set.of(tools), outputContract);
        }
    }

    public record AgentRunState(
            String agentRunId,
            AgentType agentType,
            String projectId,
            String snapshotId,
            String queryId,
            Optional<String> reportArtifactId,
            AgentStatus status,
            String currentStep,
            List<String> pendingQuestions,
            List<String> pendingScopes,
            List<String> deepJobIds,
            List<Map<String, String>> partialResults,
            List<String> warnings,
            List<String> errors,
            double cost,
            Instant createdAt,
            Instant updatedAt) {
        public AgentRunState {
            agentRunId = requireText(agentRunId, "agentRunId");
            agentType = Objects.requireNonNull(agentType, "agentType");
            projectId = requireText(projectId, "projectId");
            snapshotId = requireText(snapshotId, "snapshotId");
            queryId = requireText(queryId, "queryId");
            reportArtifactId = Objects.requireNonNull(reportArtifactId, "reportArtifactId");
            status = Objects.requireNonNull(status, "status");
            currentStep = requireText(currentStep, "currentStep");
            pendingQuestions = List.copyOf(Objects.requireNonNull(pendingQuestions, "pendingQuestions"));
            pendingScopes = List.copyOf(Objects.requireNonNull(pendingScopes, "pendingScopes"));
            deepJobIds = List.copyOf(Objects.requireNonNull(deepJobIds, "deepJobIds"));
            partialResults = List.copyOf(Objects.requireNonNull(partialResults, "partialResults"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record CandidateOption(String projectId, String moduleKey, String datasourceKey, String type, String path, int line, String confidence, String evidenceKey) {
        public CandidateOption {
            projectId = requireText(projectId, "projectId");
            moduleKey = requireText(moduleKey, "moduleKey");
            datasourceKey = requireText(datasourceKey, "datasourceKey");
            type = requireText(type, "type");
            path = requireText(path, "path");
            confidence = requireText(confidence, "confidence");
            evidenceKey = requireText(evidenceKey, "evidenceKey");
        }
    }

    public record CandidateDecision(boolean needsPicker, Optional<String> followUpQuestion, List<CandidateOption> candidates) {
        public CandidateDecision {
            followUpQuestion = Objects.requireNonNull(followUpQuestion, "followUpQuestion");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    public static final class CandidateResolver {
        public CandidateDecision decide(List<CandidateOption> candidates) {
            if (candidates.size() <= 1) {
                return new CandidateDecision(false, Optional.empty(), candidates);
            }
            Set<String> scopes = new LinkedHashSet<>();
            for (CandidateOption candidate : candidates) {
                scopes.add(candidate.projectId() + "/" + candidate.moduleKey() + "/" + candidate.datasourceKey());
            }
            if (scopes.size() > 1) {
                return new CandidateDecision(true, Optional.of("请选择 project/module/datasource 范围后继续。"), candidates);
            }
            return new CandidateDecision(true, Optional.of("Please choose one candidate before continuing."), candidates);
        }
    }

    public record AgentEvidence(String evidenceKey, String path, int line, String confidence, String sourceType) {
        public AgentEvidence {
            evidenceKey = requireText(evidenceKey, "evidenceKey");
            path = requireText(path, "path");
            confidence = requireText(confidence, "confidence");
            sourceType = requireText(sourceType, "sourceType");
        }
    }

    public record AgentOutput(
            List<Map<String, String>> results,
            List<AgentEvidence> evidence,
            String confidence,
            String sourceType,
            boolean truncated,
            String status) {
        public AgentOutput {
            results = List.copyOf(Objects.requireNonNull(results, "results"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            confidence = requireText(confidence, "confidence");
            sourceType = requireText(sourceType, "sourceType");
            status = requireText(status, "status");
        }
    }

    public record DeepSupplement(String reportArtifactId, String supplementsArtifactId, boolean originalReportStale, boolean upgradeAvailable) {
        public DeepSupplement {
            reportArtifactId = requireText(reportArtifactId, "reportArtifactId");
            supplementsArtifactId = requireText(supplementsArtifactId, "supplementsArtifactId");
        }
    }

    public static final class AgentRunPlanner {
        private final Clock clock;

        public AgentRunPlanner(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        public AgentRunState fastReady(AgentType type, String projectId, String snapshotId, String queryId, List<Map<String, String>> partialResults) {
            Instant now = clock.instant();
            return new AgentRunState(
                    "agent-" + queryId,
                    type,
                    projectId,
                    snapshotId,
                    queryId,
                    Optional.of("report-" + queryId),
                    AgentStatus.FAST_READY,
                    "fast report ready; deep supplement queued",
                    List.of(),
                    List.of(),
                    List.of("deep-" + queryId),
                    partialResults,
                    List.of(),
                    List.of(),
                    0.0d,
                    now,
                    now);
        }

        public DeepSupplement deepSupplement(AgentRunState fastState) {
            return new DeepSupplement(fastState.reportArtifactId().orElseThrow(), fastState.reportArtifactId().orElseThrow() + "-deep", true, true);
        }

        public AgentRunState partialFailure(AgentRunState state, String reason) {
            return new AgentRunState(
                    state.agentRunId(),
                    state.agentType(),
                    state.projectId(),
                    state.snapshotId(),
                    state.queryId(),
                    state.reportArtifactId(),
                    AgentStatus.PARTIAL,
                    "partial result available",
                    state.pendingQuestions(),
                    state.pendingScopes(),
                    state.deepJobIds(),
                    state.partialResults(),
                    state.warnings(),
                    append(state.errors(), reason),
                    state.cost(),
                    state.createdAt(),
                    clock.instant());
        }
    }

    public record GuardDecision(boolean allowed, String reason) {
        public GuardDecision {
            reason = Objects.requireNonNull(reason, "reason");
        }

        public static GuardDecision allow() {
            return new GuardDecision(true, "");
        }

        public static GuardDecision reject(String reason) {
            return new GuardDecision(false, requireText(reason, "reason"));
        }
    }

    public static final class ToolCallGuard {
        private static final Pattern RAW_STATEMENT = Pattern.compile("(?is)\\b(select|insert|update|delete|merge|match|create|drop|alter|call)\\b.+");
        private static final Pattern FILE_GLOB = Pattern.compile("(^|[\\\\/\\s])\\*\\*?|\\{[^}]+}");
        private static final Pattern SHELL = Pattern.compile("(?i)\\b(powershell|cmd\\.exe|cmd /c|bash|sh -c|git\\s+|gradle\\s+|npm\\s+)\\b");
        private final Set<String> allowedProjects;
        private final FixedWindowRateLimiter rateLimiter;
        private final RedactedAuditLog auditLog;
        private final Clock clock;

        public ToolCallGuard(Set<String> allowedProjects, FixedWindowRateLimiter rateLimiter, RedactedAuditLog auditLog, Clock clock) {
            this.allowedProjects = Set.copyOf(Objects.requireNonNull(allowedProjects, "allowedProjects"));
            this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
            this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        public GuardDecision authorize(AgentProfile profile, McpToolDefinition tool, ToolRequest request) {
            if (!profile.allowedTools().contains(tool.name())) {
                return GuardDecision.reject("out-of-profile tool");
            }
            if (!tool.readOnly()) {
                return GuardDecision.reject("non-read-only tool");
            }
            if (!allowedProjects.contains(request.projectId())) {
                return GuardDecision.reject("project not allowed");
            }
            if (!rateLimiter.tryAcquire(request.principal(), clock.instant())) {
                return GuardDecision.reject("rate limited");
            }
            for (Map.Entry<String, String> argument : request.arguments().entrySet()) {
                String key = argument.getKey().toLowerCase(Locale.ROOT);
                String value = argument.getValue();
                if (key.contains("cypher") || key.contains("sql") || RAW_STATEMENT.matcher(value).matches()) {
                    return GuardDecision.reject("raw Cypher or SQL is not allowed");
                }
                if (key.contains("glob") || FILE_GLOB.matcher(value).find()) {
                    return GuardDecision.reject("file glob is not allowed");
                }
                if (key.contains("command") || SHELL.matcher(value).find()) {
                    return GuardDecision.reject("shell command is not allowed");
                }
            }
            return GuardDecision.allow();
        }

        public void audit(ToolRequest request, int resultCount, Instant started, Optional<String> rejectionReason) {
            auditLog.record(new AuditEntry(
                    request.requestId(),
                    request.principal(),
                    request.projectId(),
                    request.toolName(),
                    summarizeArguments(request.arguments()),
                    resultCount,
                    "redacted",
                    Duration.between(started, clock.instant()),
                    rejectionReason));
        }

        public Clock clock() {
            return clock;
        }

        private static String summarizeArguments(Map<String, String> arguments) {
            List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, String> argument : arguments.entrySet()) {
                pairs.add(argument.getKey() + "=" + redact(argument.getValue()));
            }
            return String.join(",", pairs);
        }
    }

    public static final class FixedWindowRateLimiter {
        private final int maxRequests;
        private final Duration window;
        private final Map<String, ArrayDeque<Instant>> logs = new LinkedHashMap<>();

        public FixedWindowRateLimiter(int maxRequests, Duration window) {
            if (maxRequests <= 0) {
                throw new IllegalArgumentException("maxRequests must be positive");
            }
            this.maxRequests = maxRequests;
            this.window = Objects.requireNonNull(window, "window");
        }

        public boolean tryAcquire(String principal, Instant now) {
            ArrayDeque<Instant> log = logs.computeIfAbsent(principal, k -> new ArrayDeque<>());
            Instant cutoff = now.minus(window);
            while (!log.isEmpty() && !log.peekFirst().isAfter(cutoff)) {
                log.removeFirst();
            }
            if (log.size() >= maxRequests) {
                return false;
            }
            log.addLast(now);
            return true;
        }
    }

    public record AuditEntry(
            String requestId,
            String principal,
            String projectId,
            String toolName,
            String parameterSummary,
            int resultCount,
            String redactionState,
            Duration duration,
            Optional<String> rejectionReason) {
        public AuditEntry {
            requestId = requireText(requestId, "requestId");
            principal = requireText(principal, "principal");
            projectId = requireText(projectId, "projectId");
            toolName = requireText(toolName, "toolName");
            parameterSummary = Objects.requireNonNull(parameterSummary, "parameterSummary");
            redactionState = requireText(redactionState, "redactionState");
            duration = Objects.requireNonNull(duration, "duration");
            rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
        }
    }

    public static final class RedactedAuditLog {
        private final List<AuditEntry> entries = new ArrayList<>();

        public void record(AuditEntry entry) {
            entries.add(Objects.requireNonNull(entry, "entry"));
        }

        public List<AuditEntry> entries() {
            return List.copyOf(entries);
        }
    }

    private static List<String> append(List<String> values, String value) {
        List<String> output = new ArrayList<>(values);
        output.add(value);
        return output;
    }

    private static String redact(String value) {
        return Objects.requireNonNull(value, "value")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[REDACTED_EMAIL]")
                .replaceAll("(?i)(token|secret|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED_SECRET]");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
