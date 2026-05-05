package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.RelationFamily;
import org.sainm.codeatlas.facts.RelationKindRegistry;
import org.sainm.codeatlas.facts.RelationType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class AiContracts {
    private AiContracts() {
    }

    public interface AiProvider {
        AiResponse complete(AiRequest request);
    }

    public record AiRequest(
            String provider,
            String model,
            List<AiMessage> messages,
            int maxOutputTokens,
            double temperature,
            EvidencePack evidencePack) {
        public AiRequest {
            provider = requireText(provider, "provider");
            model = requireText(model, "model");
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("messages must not be empty");
            }
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
            if (temperature < 0.0d || temperature > 2.0d) {
                throw new IllegalArgumentException("temperature must be between 0 and 2");
            }
            Objects.requireNonNull(evidencePack, "evidencePack");
        }
    }

    public record AiMessage(String role, String content) {
        public AiMessage {
            role = requireText(role, "role");
            content = Objects.requireNonNull(content, "content");
        }
    }

    public record AiResponse(String text, List<String> evidencePaths, boolean fallback, Optional<String> failureReason) {
        public AiResponse {
            text = Objects.requireNonNull(text, "text");
            evidencePaths = List.copyOf(Objects.requireNonNull(evidencePaths, "evidencePaths"));
            failureReason = Objects.requireNonNull(failureReason, "failureReason");
        }
    }

    public static final class DisabledAiProvider implements AiProvider {
        @Override
        public AiResponse complete(AiRequest request) {
            return StaticFallback.answer("AI disabled", request.evidencePack());
        }
    }

    public record SystemAiConfiguration(boolean enabled, String provider, String model, String encryptedApiKey) {
        public SystemAiConfiguration {
            provider = requireText(provider, "provider");
            model = requireText(model, "model");
            encryptedApiKey = Objects.requireNonNull(encryptedApiKey, "encryptedApiKey");
        }
    }

    public record ProjectAiConfiguration(
            String projectId,
            Optional<Boolean> enabled,
            Optional<String> provider,
            Optional<String> model,
            Optional<String> encryptedApiKey) {
        public ProjectAiConfiguration {
            projectId = requireText(projectId, "projectId");
            enabled = Objects.requireNonNull(enabled, "enabled");
            provider = Objects.requireNonNull(provider, "provider");
            model = Objects.requireNonNull(model, "model");
            encryptedApiKey = Objects.requireNonNull(encryptedApiKey, "encryptedApiKey");
        }
    }

    public record EffectiveAiConfiguration(boolean enabled, String provider, String model, String encryptedApiKey) {
        public EffectiveAiConfiguration {
            provider = requireText(provider, "provider");
            model = requireText(model, "model");
            encryptedApiKey = Objects.requireNonNull(encryptedApiKey, "encryptedApiKey");
        }
    }

    public static final class AiConfigurationResolver {
        public EffectiveAiConfiguration resolve(SystemAiConfiguration system, ProjectAiConfiguration project) {
            Objects.requireNonNull(system, "system");
            Objects.requireNonNull(project, "project");
            return new EffectiveAiConfiguration(
                    project.enabled().orElse(system.enabled()),
                    project.provider().orElse(system.provider()),
                    project.model().orElse(system.model()),
                    project.encryptedApiKey().orElse(system.encryptedApiKey()));
        }
    }

    public static final class ApiKeyEncryptor {
        private static final int IV_BYTES = 12;
        private final SecretKeySpec key;
        private final SecureRandom secureRandom;

        public ApiKeyEncryptor(byte[] rawKey) {
            this(rawKey, new SecureRandom());
        }

        ApiKeyEncryptor(byte[] rawKey, SecureRandom secureRandom) {
            byte[] normalized = MessageDigests.sha256(rawKey);
            this.key = new SecretKeySpec(normalized, "AES");
            this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        }

        public String encrypt(String plaintext) {
            try {
                byte[] iv = new byte[IV_BYTES];
                secureRandom.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] ciphertext = cipher.doFinal(requireText(plaintext, "plaintext").getBytes(StandardCharsets.UTF_8));
                ByteBuffer output = ByteBuffer.allocate(iv.length + ciphertext.length);
                output.put(iv).put(ciphertext);
                return Base64.getEncoder().encodeToString(output.array());
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Could not encrypt API key", ex);
            }
        }

        public String decrypt(String encrypted) {
            try {
                byte[] packed = Base64.getDecoder().decode(requireText(encrypted, "encrypted"));
                if (packed.length <= IV_BYTES) {
                    throw new IllegalArgumentException("encrypted value is too short");
                }
                byte[] iv = new byte[IV_BYTES];
                byte[] ciphertext = new byte[packed.length - IV_BYTES];
                System.arraycopy(packed, 0, iv, 0, IV_BYTES);
                System.arraycopy(packed, IV_BYTES, ciphertext, 0, ciphertext.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Could not decrypt API key", ex);
            }
        }
    }

    public static final class Redactor {
        private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        private static final Pattern API_KEY = Pattern.compile("(?i)(api[_-]?key|token|secret)\\s*[:=]\\s*[^\\s,;]+");
        private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");

        public String redact(String input) {
            String redacted = Objects.requireNonNull(input, "input");
            redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
            redacted = API_KEY.matcher(redacted).replaceAll("$1=[REDACTED_SECRET]");
            redacted = BEARER.matcher(redacted).replaceAll("Bearer [REDACTED_SECRET]");
            return redacted;
        }

        public EvidenceSnippet redact(EvidenceSnippet snippet) {
            return new EvidenceSnippet(
                    snippet.evidenceKey(),
                    snippet.path(),
                    snippet.lineStart(),
                    snippet.lineEnd(),
                    snippet.evidenceType(),
                    redact(snippet.snippet()),
                    snippet.metadata());
        }

        public PromptLog redact(PromptLog log) {
            return new PromptLog(log.requestId(), redact(log.prompt()), redact(log.response()));
        }
    }

    public record PromptLog(String requestId, String prompt, String response) {
        public PromptLog {
            requestId = requireText(requestId, "requestId");
            prompt = Objects.requireNonNull(prompt, "prompt");
            response = Objects.requireNonNull(response, "response");
        }
    }

    public record EvidenceSnippet(
            String evidenceKey,
            String path,
            int lineStart,
            int lineEnd,
            String evidenceType,
            String snippet,
            Map<String, String> metadata) {
        public EvidenceSnippet {
            evidenceKey = requireText(evidenceKey, "evidenceKey");
            path = requireText(path, "path");
            if (lineStart < 0 || lineEnd < lineStart) {
                throw new IllegalArgumentException("invalid evidence line range");
            }
            evidenceType = requireText(evidenceType, "evidenceType");
            snippet = Objects.requireNonNull(snippet, "snippet");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    public record EvidencePack(
            String id,
            String projectId,
            String snapshotId,
            int sourceBudgetChars,
            List<EvidenceSnippet> snippets,
            boolean truncated,
            List<String> evidencePaths) {
        public EvidencePack {
            id = requireText(id, "id");
            projectId = requireText(projectId, "projectId");
            snapshotId = requireText(snapshotId, "snapshotId");
            if (sourceBudgetChars <= 0) {
                throw new IllegalArgumentException("sourceBudgetChars must be positive");
            }
            snippets = List.copyOf(Objects.requireNonNull(snippets, "snippets"));
            evidencePaths = List.copyOf(Objects.requireNonNull(evidencePaths, "evidencePaths"));
        }
    }

    public static final class EvidencePackBuilder {
        private final Redactor redactor;

        public EvidencePackBuilder(Redactor redactor) {
            this.redactor = Objects.requireNonNull(redactor, "redactor");
        }

        public EvidencePack build(String projectId, String snapshotId, int sourceBudgetChars, Collection<EvidenceSnippet> input) {
            requireText(projectId, "projectId");
            requireText(snapshotId, "snapshotId");
            if (sourceBudgetChars <= 0) {
                throw new IllegalArgumentException("sourceBudgetChars must be positive");
            }
            int used = 0;
            boolean truncated = false;
            List<EvidenceSnippet> snippets = new ArrayList<>();
            List<String> paths = new ArrayList<>();
            for (EvidenceSnippet snippet : input) {
                EvidenceSnippet redacted = redactor.redact(snippet);
                int length = redacted.snippet().length();
                if (used + length > sourceBudgetChars) {
                    truncated = true;
                    break;
                }
                snippets.add(redacted);
                paths.add(redacted.path() + ":" + redacted.lineStart());
                used += length;
            }
            String id = "ep-" + MessageDigests.shortHash(projectId + "|" + snapshotId + "|" + paths);
            return new EvidencePack(id, projectId, snapshotId, sourceBudgetChars, snippets, truncated, paths);
        }
    }

    public static final class PromptFactory {
        public AiRequest impactSummary(EffectiveAiConfiguration configuration, EvidencePack pack) {
            return request(configuration, pack, "Summarize impact. Cite evidence paths for every claim.");
        }

        public AiRequest riskExplanation(EffectiveAiConfiguration configuration, EvidencePack pack) {
            return request(configuration, pack, "Explain risk. Separate certain facts from possible AI candidates.");
        }

        public AiRequest testSuggestion(EffectiveAiConfiguration configuration, EvidencePack pack) {
            return request(configuration, pack, "Suggest tests. Link each suggested test to evidence paths.");
        }

        private AiRequest request(EffectiveAiConfiguration configuration, EvidencePack pack, String instruction) {
            return new AiRequest(
                    configuration.provider(),
                    configuration.model(),
                    List.of(
                            new AiMessage("system", "You are CodeAtlas. Use only supplied evidence."),
                            new AiMessage("user", instruction + "\nEvidence paths: " + String.join(", ", pack.evidencePaths()))),
                    1200,
                    0.2d,
                    pack);
        }
    }

    public static final class EvidenceBackedAnswerDraft {
        public AiResponse draft(String question, EvidencePack pack) {
            requireText(question, "question");
            if (pack.snippets().isEmpty()) {
                return new AiResponse("No evidence available for: " + question, List.of(), true, Optional.of("empty evidence pack"));
            }
            String text = "Draft answer from " + pack.snippets().size() + " evidence item(s): " + pack.snippets().getFirst().snippet();
            return new AiResponse(text, pack.evidencePaths(), false, Optional.empty());
        }
    }

    public static final class StaticFallback {
        private StaticFallback() {
        }

        public static AiResponse answer(String reason, EvidencePack pack) {
            String text = "Static fallback: AI unavailable. Review the cited evidence paths and graph/static analysis report.";
            return new AiResponse(text, pack.evidencePaths(), true, Optional.of(requireText(reason, "reason")));
        }
    }

    public record AiCandidateRelation(
            String projectId,
            String snapshotId,
            String sourceIdentityId,
            String targetIdentityId,
            String relationName,
            String evidenceKey,
            Confidence confidence,
            String createdFromEvidencePackId,
            Optional<Instant> expiresAt,
            boolean staleAgainstSnapshot) {
        public AiCandidateRelation {
            projectId = requireText(projectId, "projectId");
            snapshotId = requireText(snapshotId, "snapshotId");
            sourceIdentityId = requireText(sourceIdentityId, "sourceIdentityId");
            targetIdentityId = requireText(targetIdentityId, "targetIdentityId");
            relationName = requireText(relationName, "relationName");
            evidenceKey = requireText(evidenceKey, "evidenceKey");
            confidence = Objects.requireNonNull(confidence, "confidence");
            createdFromEvidencePackId = requireText(createdFromEvidencePackId, "createdFromEvidencePackId");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    public record AiCandidateValidationResult(boolean valid, List<String> errors) {
        public AiCandidateValidationResult {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }

    public interface AiCandidateValidationContext {
        boolean identityExists(String projectId, String snapshotId, String identityId);

        boolean evidenceExists(String projectId, String snapshotId, String evidenceKey);

        boolean canAccessProject(String projectId);
    }

    public static final class AiCandidateStagingValidator {
        public AiCandidateValidationResult validate(AiCandidateRelation candidate, AiCandidateValidationContext context) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(context, "context");
            List<String> errors = new ArrayList<>();
            RelationType relationType = null;
            try {
                relationType = RelationKindRegistry.defaults().require(candidate.relationName());
            } catch (IllegalArgumentException ex) {
                errors.add("allowed relation type not registered: " + candidate.relationName());
            }
            if (relationType != null && relationType.family() != RelationFamily.AI_ASSISTED_CANDIDATE) {
                errors.add("AI candidate must use AI_ASSISTED_CANDIDATE relation family");
            }
            if (candidate.confidence() == Confidence.CERTAIN) {
                errors.add("AI candidate confidence cannot be CERTAIN");
            }
            if (!context.canAccessProject(candidate.projectId())) {
                errors.add("project permission denied");
            }
            if (!context.identityExists(candidate.projectId(), candidate.snapshotId(), candidate.sourceIdentityId())) {
                errors.add("source identity missing");
            }
            if (!context.identityExists(candidate.projectId(), candidate.snapshotId(), candidate.targetIdentityId())) {
                errors.add("target identity missing");
            }
            if (!context.evidenceExists(candidate.projectId(), candidate.snapshotId(), candidate.evidenceKey())) {
                errors.add("evidenceKey missing");
            }
            if (candidate.expiresAt().isEmpty() && !candidate.staleAgainstSnapshot()) {
                errors.add("AI candidate must have expiresAt or staleAgainstSnapshot");
            }
            return new AiCandidateValidationResult(errors.isEmpty(), errors);
        }
    }

    public static final class AiCandidateStore {
        private final Clock clock;
        private final List<AiCandidateRelation> candidates = new ArrayList<>();

        public AiCandidateStore(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        public void add(AiCandidateRelation candidate) {
            candidates.add(Objects.requireNonNull(candidate, "candidate"));
        }

        public List<AiCandidateRelation> activeCandidates(String projectId, String snapshotId) {
            Instant now = clock.instant();
            return candidates.stream()
                    .filter(candidate -> candidate.projectId().equals(projectId))
                    .filter(candidate -> candidate.snapshotId().equals(snapshotId))
                    .filter(candidate -> !candidate.staleAgainstSnapshot())
                    .filter(candidate -> candidate.expiresAt().map(expires -> expires.isAfter(now)).orElse(true))
                    .toList();
        }

        public void markStaleForEvidence(String projectId, String snapshotId, String evidenceKey) {
            List<AiCandidateRelation> replacement = candidates.stream()
                    .map(candidate -> candidate.projectId().equals(projectId)
                            && candidate.snapshotId().equals(snapshotId)
                            && candidate.evidenceKey().equals(evidenceKey)
                            ? stale(candidate) : candidate)
                    .toList();
            candidates.clear();
            candidates.addAll(replacement);
        }

        public int tombstoneExpiredAiCandidates() {
            Instant now = clock.instant();
            int before = candidates.size();
            candidates.removeIf(candidate -> candidate.expiresAt().map(expires -> !expires.isAfter(now)).orElse(false));
            return before - candidates.size();
        }

        private static AiCandidateRelation stale(AiCandidateRelation candidate) {
            return new AiCandidateRelation(
                    candidate.projectId(),
                    candidate.snapshotId(),
                    candidate.sourceIdentityId(),
                    candidate.targetIdentityId(),
                    candidate.relationName(),
                    candidate.evidenceKey(),
                    candidate.confidence(),
                    candidate.createdFromEvidencePackId(),
                    candidate.expiresAt(),
                    true);
        }
    }

    public static final class ImpactPathQueryPolicy {
        public boolean includeAiCandidates(boolean userRequestedAiCandidates, boolean featurePlannerRanking) {
            return userRequestedAiCandidates || featurePlannerRanking;
        }
    }

    public interface EmbeddingProvider {
        List<Double> embed(String text);
    }

    public static final class TokenHashEmbeddingProvider implements EmbeddingProvider {
        private final int dimensions;

        public TokenHashEmbeddingProvider(int dimensions) {
            if (dimensions <= 0) {
                throw new IllegalArgumentException("dimensions must be positive");
            }
            this.dimensions = dimensions;
        }

        @Override
        public List<Double> embed(String text) {
            double[] vector = new double[dimensions];
            for (String token : tokenize(text)) {
                int index = Math.floorMod(token.hashCode(), dimensions);
                vector[index] += 1.0d;
            }
            double length = 0.0d;
            for (double value : vector) {
                length += value * value;
            }
            length = Math.sqrt(length);
            List<Double> output = new ArrayList<>(dimensions);
            for (double value : vector) {
                output.add(length == 0.0d ? 0.0d : value / length);
            }
            return output;
        }
    }

    public record VectorDocument(String id, String projectId, String snapshotId, String title, String body, List<Double> embedding) {
        public VectorDocument {
            id = requireText(id, "id");
            projectId = requireText(projectId, "projectId");
            snapshotId = requireText(snapshotId, "snapshotId");
            title = requireText(title, "title");
            body = Objects.requireNonNull(body, "body");
            embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
        }
    }

    public record SearchResult(String id, String title, double score, String source) {
        public SearchResult {
            id = requireText(id, "id");
            title = requireText(title, "title");
            source = requireText(source, "source");
        }
    }

    public static final class Neo4jVectorIndexV1Contract {
        private final Map<String, VectorDocument> documents = new LinkedHashMap<>();

        public String indexName(String projectId, String snapshotId) {
            return "codeatlas_vector_v1_" + sanitize(projectId) + "_" + sanitize(snapshotId);
        }

        public void upsert(VectorDocument document) {
            documents.put(document.id(), document);
        }

        public List<SearchResult> query(String projectId, String snapshotId, List<Double> embedding, int limit) {
            return documents.values().stream()
                    .filter(document -> document.projectId().equals(projectId))
                    .filter(document -> document.snapshotId().equals(snapshotId))
                    .map(document -> new SearchResult(document.id(), document.title(), cosine(embedding, document.embedding()), "vector"))
                    .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    public interface GraphExpansionProvider {
        List<SearchResult> expand(SearchResult seed);
    }

    public static final class HybridSearchEngine {
        private final EmbeddingProvider embeddingProvider;
        private final Neo4jVectorIndexV1Contract vectorIndex;
        private final GraphExpansionProvider graphExpansionProvider;
        private final List<VectorDocument> documents;

        public HybridSearchEngine(
                EmbeddingProvider embeddingProvider,
                Neo4jVectorIndexV1Contract vectorIndex,
                GraphExpansionProvider graphExpansionProvider,
                List<VectorDocument> documents) {
            this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
            this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
            this.graphExpansionProvider = Objects.requireNonNull(graphExpansionProvider, "graphExpansionProvider");
            this.documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        }

        public List<SearchResult> search(String projectId, String snapshotId, String query, int limit) {
            List<SearchResult> exact = exactSearch(projectId, snapshotId, query);
            List<SearchResult> vector = vectorIndex.query(projectId, snapshotId, embeddingProvider.embed(query), limit);
            LinkedHashMap<String, SearchResult> merged = new LinkedHashMap<>();
            addAll(merged, exact);
            addAll(merged, vector);
            ArrayDeque<SearchResult> seeds = new ArrayDeque<>(merged.values());
            while (!seeds.isEmpty() && merged.size() < limit * 2) {
                addAll(merged, graphExpansionProvider.expand(seeds.removeFirst()));
            }
            return merged.values().stream()
                    .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                    .limit(limit)
                    .toList();
        }

        private List<SearchResult> exactSearch(String projectId, String snapshotId, String query) {
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            return documents.stream()
                    .filter(document -> document.projectId().equals(projectId))
                    .filter(document -> document.snapshotId().equals(snapshotId))
                    .filter(document -> document.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                            || document.body().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                    .map(document -> new SearchResult(document.id(), document.title(), 1.0d, "exact"))
                    .toList();
        }

        private static void addAll(Map<String, SearchResult> target, Collection<SearchResult> results) {
            for (SearchResult result : results) {
                target.merge(result.id(), result, (left, right) -> left.score() >= right.score() ? left : right);
            }
        }
    }

    public record HistoricalImpactReportSummary(String id, String projectId, String title, String summary, Instant createdAt) {
        public HistoricalImpactReportSummary {
            id = requireText(id, "id");
            projectId = requireText(projectId, "projectId");
            title = requireText(title, "title");
            summary = Objects.requireNonNull(summary, "summary");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public static final class HistoricalImpactReportStore {
        private final List<HistoricalImpactReportSummary> reports = new ArrayList<>();

        public void add(HistoricalImpactReportSummary summary) {
            reports.add(Objects.requireNonNull(summary, "summary"));
        }

        public List<HistoricalImpactReportSummary> recall(String projectId, String query, int limit) {
            Set<String> queryTokens = new HashSet<>(tokenize(query));
            return reports.stream()
                    .filter(report -> report.projectId().equals(projectId))
                    .map(report -> Map.entry(report, overlap(queryTokens, tokenize(report.title() + " " + report.summary()))))
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<HistoricalImpactReportSummary, Integer>comparingByValue().reversed()
                            .thenComparing(entry -> entry.getKey().createdAt(), Comparator.reverseOrder()))
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : Objects.requireNonNull(text, "text").toLowerCase(Locale.ROOT).split("[^a-z0-9_#./-]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    private static int overlap(Set<String> left, List<String> right) {
        int count = 0;
        for (String token : right) {
            if (left.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private static String sanitize(String input) {
        return input.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        double leftLength = 0.0d;
        double rightLength = 0.0d;
        for (int i = 0; i < size; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftLength += leftValue * leftValue;
            rightLength += rightValue * rightValue;
        }
        if (leftLength == 0.0d || rightLength == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftLength) * Math.sqrt(rightLength));
    }

    private static final class MessageDigests {
        private MessageDigests() {
        }

        static byte[] sha256(byte[] input) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(input, "input"));
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("SHA-256 unavailable", ex);
            }
        }

        static String shortHash(String input) {
            byte[] digest = sha256(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 12);
        }
    }
}
