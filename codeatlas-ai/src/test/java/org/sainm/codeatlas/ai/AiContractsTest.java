package org.sainm.codeatlas.ai;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiContractsTest {
    @Test
    void resolvesProjectConfigurationAndEncryptsApiKeys() {
        AiContracts.ApiKeyEncryptor encryptor = new AiContracts.ApiKeyEncryptor("local-master-key".getBytes());
        String encrypted = encryptor.encrypt("sk-test-secret");

        AiContracts.SystemAiConfiguration system = new AiContracts.SystemAiConfiguration(true, "system-provider", "system-model", encrypted);
        AiContracts.ProjectAiConfiguration project = new AiContracts.ProjectAiConfiguration(
                "shop",
                Optional.of(false),
                Optional.of("project-provider"),
                Optional.empty(),
                Optional.empty());

        AiContracts.EffectiveAiConfiguration resolved = new AiContracts.AiConfigurationResolver().resolve(system, project);

        assertFalse(resolved.enabled());
        assertEquals("project-provider", resolved.provider());
        assertEquals("system-model", resolved.model());
        assertNotEquals("sk-test-secret", resolved.encryptedApiKey());
        assertEquals("sk-test-secret", encryptor.decrypt(resolved.encryptedApiKey()));
    }

    @Test
    void redactsEvidenceAndPromptLogsBeforeBuildingEvidencePack() {
        AiContracts.Redactor redactor = new AiContracts.Redactor();
        AiContracts.EvidenceSnippet snippet = new AiContracts.EvidenceSnippet(
                "ev-1",
                "src/User.java",
                10,
                11,
                "CALLS",
                "email=a@example.com apiKey=secret-token",
                Map.of("symbol", "UserService"));

        AiContracts.EvidencePack pack = new AiContracts.EvidencePackBuilder(redactor)
                .build("shop", "snap-1", 100, List.of(snippet));
        AiContracts.PromptLog log = redactor.redact(new AiContracts.PromptLog("req-1", "Bearer abc.def", "token: x"));

        assertEquals(1, pack.snippets().size());
        assertTrue(pack.snippets().getFirst().snippet().contains("[REDACTED_EMAIL]"));
        assertTrue(pack.snippets().getFirst().snippet().contains("[REDACTED_SECRET]"));
        assertTrue(log.prompt().contains("[REDACTED_SECRET]"));
        assertTrue(log.response().contains("[REDACTED_SECRET]"));
        assertFalse(pack.truncated());
    }

    @Test
    void evidencePackHonorsSourceBudgetAndPromptsCiteEvidencePaths() {
        AiContracts.EvidenceSnippet first = snippet("ev-1", "src/A.java", "short");
        AiContracts.EvidenceSnippet second = snippet("ev-2", "src/B.java", "this snippet is too long for the remaining source budget");

        AiContracts.EvidencePack pack = new AiContracts.EvidencePackBuilder(new AiContracts.Redactor())
                .build("shop", "snap-1", 12, List.of(first, second));
        AiContracts.EffectiveAiConfiguration config = new AiContracts.EffectiveAiConfiguration(true, "local", "stub", "encrypted");
        AiContracts.AiRequest request = new AiContracts.PromptFactory().impactSummary(config, pack);

        assertEquals(1, pack.snippets().size());
        assertTrue(pack.truncated());
        assertTrue(request.messages().getLast().content().contains("src/A.java:1"));
    }

    @Test
    void aiCandidateStagingRequiresAiFamilyEvidenceIdentityAndExpiryBoundary() {
        AiContracts.AiCandidateRelation valid = candidate("AI_SUGGESTS_RELATION", Confidence.LIKELY, Optional.of(Instant.parse("2026-01-02T00:00:00Z")), false);
        AiContracts.AiCandidateRelation invalid = candidate("CALLS", Confidence.CERTAIN, Optional.empty(), false);
        AiContracts.AiCandidateValidationContext context = context(Set.of("src", "dst"), Set.of("ev-1"), true);
        AiContracts.AiCandidateStagingValidator validator = new AiContracts.AiCandidateStagingValidator();

        assertTrue(validator.validate(valid, context).valid());

        AiContracts.AiCandidateValidationResult result = validator.validate(invalid, context);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("AI candidate must use AI_ASSISTED_CANDIDATE relation family"));
        assertTrue(result.errors().contains("AI candidate confidence cannot be CERTAIN"));
        assertTrue(result.errors().contains("AI candidate must have expiresAt or staleAgainstSnapshot"));
    }

    @Test
    void aiCandidatesAreExcludedByDefaultAndStaleCandidatesDoNotStayActive() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AiContracts.AiCandidateStore store = new AiContracts.AiCandidateStore(clock);
        store.add(candidate("AI_SUGGESTS_RELATION", Confidence.POSSIBLE, Optional.of(Instant.parse("2026-01-02T00:00:00Z")), false));

        assertFalse(new AiContracts.ImpactPathQueryPolicy().includeAiCandidates(false, false));
        assertTrue(new AiContracts.ImpactPathQueryPolicy().includeAiCandidates(true, false));
        assertEquals(1, store.activeCandidates("shop", "snap-1").size());

        store.markStaleForEvidence("ev-1");
        assertEquals(0, store.activeCandidates("shop", "snap-1").size());
    }

    @Test
    void disabledAiUsesStaticFallbackWithEvidenceCitations() {
        AiContracts.EvidencePack pack = new AiContracts.EvidencePackBuilder(new AiContracts.Redactor())
                .build("shop", "snap-1", 200, List.of(snippet("ev-1", "src/A.java", "calls mapper")));
        AiContracts.AiRequest request = new AiContracts.PromptFactory()
                .riskExplanation(new AiContracts.EffectiveAiConfiguration(false, "disabled", "none", ""), pack);

        AiContracts.AiResponse response = new AiContracts.DisabledAiProvider().complete(request);

        assertTrue(response.fallback());
        assertEquals(List.of("src/A.java:1"), response.evidencePaths());
        assertTrue(response.failureReason().orElseThrow().contains("AI disabled"));
    }

    @Test
    void hybridSearchUsesExactVectorAndGraphExpansionAndRecallsHistoricalReports() {
        AiContracts.TokenHashEmbeddingProvider embeddings = new AiContracts.TokenHashEmbeddingProvider(16);
        AiContracts.VectorDocument exact = doc(embeddings, "method-1", "User email update", "writes users.email");
        AiContracts.VectorDocument vector = doc(embeddings, "mapper-1", "Mapper", "update email SQL");
        AiContracts.Neo4jVectorIndexV1Contract index = new AiContracts.Neo4jVectorIndexV1Contract();
        index.upsert(exact);
        index.upsert(vector);

        AiContracts.HybridSearchEngine search = new AiContracts.HybridSearchEngine(
                embeddings,
                index,
                seed -> seed.id().equals("method-1") ? List.of(new AiContracts.SearchResult("dao-1", "UserDao.updateEmail", 0.8d, "graph")) : List.of(),
                List.of(exact));

        List<AiContracts.SearchResult> results = search.search("shop", "snap-1", "email", 5);

        assertTrue(results.stream().anyMatch(result -> result.source().equals("exact")));
        assertTrue(results.stream().anyMatch(result -> result.source().equals("vector")));
        assertTrue(results.stream().anyMatch(result -> result.id().equals("dao-1")));
        assertEquals("codeatlas_vector_v1_shop_snap_1", index.indexName("shop", "snap-1"));

        AiContracts.HistoricalImpactReportStore reports = new AiContracts.HistoricalImpactReportStore();
        reports.add(new AiContracts.HistoricalImpactReportSummary("r1", "shop", "Email rename impact", "users.email migration", Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals("r1", reports.recall("shop", "email migration", 1).getFirst().id());
    }

    @Test
    void evidenceBackedAnswerDraftReferencesEvidence() {
        AiContracts.EvidencePack pack = new AiContracts.EvidencePackBuilder(new AiContracts.Redactor())
                .build("shop", "snap-1", 200, List.of(snippet("ev-1", "src/A.java", "UserService calls UserMapper")));

        AiContracts.AiResponse response = new AiContracts.EvidenceBackedAnswerDraft().draft("what is impacted?", pack);

        assertFalse(response.fallback());
        assertEquals(List.of("src/A.java:1"), response.evidencePaths());
        assertTrue(response.text().contains("UserService"));
    }

    private static AiContracts.EvidenceSnippet snippet(String key, String path, String text) {
        return new AiContracts.EvidenceSnippet(key, path, 1, 1, "STATIC", text, Map.of());
    }

    private static AiContracts.AiCandidateRelation candidate(
            String relationName,
            Confidence confidence,
            Optional<Instant> expiresAt,
            boolean stale) {
        return new AiContracts.AiCandidateRelation(
                "shop",
                "snap-1",
                "src",
                "dst",
                relationName,
                "ev-1",
                confidence,
                "ep-1",
                expiresAt,
                stale);
    }

    private static AiContracts.AiCandidateValidationContext context(Set<String> identities, Set<String> evidenceKeys, boolean allowed) {
        return new AiContracts.AiCandidateValidationContext() {
            @Override
            public boolean identityExists(String projectId, String snapshotId, String identityId) {
                return identities.contains(identityId);
            }

            @Override
            public boolean evidenceExists(String projectId, String snapshotId, String evidenceKey) {
                return evidenceKeys.contains(evidenceKey);
            }

            @Override
            public boolean canAccessProject(String projectId) {
                return allowed;
            }
        };
    }

    private static AiContracts.VectorDocument doc(AiContracts.EmbeddingProvider embeddings, String id, String title, String body) {
        return new AiContracts.VectorDocument(id, "shop", "snap-1", title, body, embeddings.embed(title + " " + body));
    }
}
