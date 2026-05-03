package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.symbols.IdentityType;

class FactStagingStoreTest {
    @Test
    void stagesFactsAndEvidenceWithoutPublishingActiveView() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL).start();
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(RunStatus.STAGED, batch.analysisRun().status());
        assertEquals(List.of(runningScope.stage()), batch.scopeRuns());
        assertEquals(List.of(fact), batch.facts());
        assertEquals(List.of(evidence), batch.evidence());
        assertEquals(List.of(fact), store.stagedFacts("analysis-1"));
        assertEquals(List.of(evidence), store.stagedEvidence("analysis-1"));
        assertTrue(store.activeFacts("shop").isEmpty());
    }

    @Test
    void rejectsFactsOutsideDeclaredScopeBoundaries() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL).start();
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord wrongScopeFact = callFact(
                "analysis-1",
                "scope-2",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());

        assertThrows(IllegalArgumentException.class,
                () -> store.stage(running, List.of(runningScope), List.of(wrongScopeFact), List.of(evidence)));
    }

    @Test
    void rejectsEvidenceOutsideDeclaredScopeBoundaries() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL).start();
        Evidence wrongScopeEvidence = callEvidence("jasper", "src/main/webapp/WEB-INF/jsp/edit.jsp");
        FactRecord fact = callFact(
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                wrongScopeEvidence.evidenceKey());

        assertThrows(IllegalArgumentException.class,
                () -> store.stage(running, List.of(runningScope), List.of(fact), List.of(wrongScopeEvidence)));
    }

    @Test
    void rejectsFactsThatReferenceEvidenceFromAnotherScope() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun firstScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL).start();
        ScopeRun secondScope = ScopeRun.planned(
                "scope-2",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/B.java",
                RelationFamily.CALL).start();
        Evidence firstScopeEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        Evidence secondScopeEvidence = callEvidence("spoon", "src/main/java/com/foo/B.java");
        FactRecord wrongEvidenceFact = callFact(
                "analysis-1",
                "scope-2",
                "spoon",
                "src/main/java/com/foo/B.java",
                firstScopeEvidence.evidenceKey());

        assertThrows(IllegalArgumentException.class,
                () -> store.stage(
                        running,
                        List.of(firstScope, secondScope),
                        List.of(wrongEvidenceFact),
                        List.of(firstScopeEvidence, secondScopeEvidence)));
    }

    @Test
    void infersFlowIdentityTypeForFlowScopes() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "impact-flow",
                "src/main/java/com/foo/A.java",
                RelationFamily.FLOW).start();
        Evidence evidence = Evidence.create(
                "impact-flow",
                "src/main/java/com/foo/A.java",
                "src/main/java/com/foo/A.java",
                "line:12-14",
                1,
                SourceType.IMPACT_FLOW);
        FactRecord fact = requestParamFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "impact-flow",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(List.of(fact), batch.facts());
    }

    @Test
    void infersSymbolIdentityTypeForPlanningScopesWithSymbolTargets() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "feature-planner",
                "feature:checkout",
                RelationFamily.PLANNING).start();
        Evidence evidence = Evidence.create(
                "feature-planner",
                "feature:checkout",
                "feature-seed://shop/run-20260502-001/checkout",
                "seed:checkout",
                1,
                SourceType.USER_CONFIRMATION);
        FactRecord fact = planningFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "feature-planner",
                "feature:checkout",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(List.of(fact), batch.facts());
    }

    @Test
    void acceptsArtifactIdentityTypeForPlanningScopesWithArtifactTargets() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "feature-planner",
                "feature:checkout",
                RelationFamily.PLANNING).start();
        Evidence evidence = Evidence.create(
                "feature-planner",
                "feature:checkout",
                "feature-seed://shop/run-20260502-001/checkout",
                "scope-target:checkout",
                1,
                SourceType.USER_CONFIRMATION);
        FactRecord fact = planningArtifactFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "feature-planner",
                "feature:checkout",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(List.of(fact), batch.facts());
    }

    @Test
    void infersSymbolIdentityTypeForWorkflowScopesWithSymbolTargets() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "workflow",
                "saved-query:checkout-watch",
                RelationFamily.WORKFLOW).start();
        Evidence evidence = Evidence.create(
                "workflow",
                "saved-query:checkout-watch",
                "saved-query://shop/_root/checkout-watch",
                "watch-target:checkout-service",
                1,
                SourceType.USER_CONFIRMATION);
        FactRecord fact = workflowWatchFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "workflow",
                "saved-query:checkout-watch",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(List.of(fact), batch.facts());
    }

    @Test
    void acceptsArtifactIdentityTypeForWorkflowScopesWithArtifactTargets() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "workflow",
                "export:checkout-plan",
                RelationFamily.WORKFLOW).start();
        Evidence evidence = Evidence.create(
                "workflow",
                "export:checkout-plan",
                "export-artifact://shop/run-20260502-001/checkout-plan-md",
                "export-target:checkout-plan",
                1,
                SourceType.USER_CONFIRMATION);
        FactRecord fact = workflowArtifactFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "workflow",
                "export:checkout-plan",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));

        assertEquals(List.of(fact), batch.facts());
    }

    @Test
    void stagesBoundarySymbolRelationsInBoundaryScopes() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun running = AnalysisRun.planned("analysis-1", "shop", "snapshot-2").start();
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "boundary",
                "src/main/resources/native/libpayment.so",
                RelationFamily.BOUNDARY).start();
        Evidence evidence = Evidence.create(
                "boundary",
                "src/main/resources/native/libpayment.so",
                "src/main/resources/native/libpayment.so",
                "exports:capturePayment",
                1,
                SourceType.BOUNDARY_SYMBOL);
        FactRecord exportFact = boundaryExportFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "boundary",
                "src/main/resources/native/libpayment.so",
                evidence.evidenceKey());
        FactRecord referenceFact = boundaryReferenceFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "boundary",
                "src/main/resources/native/libpayment.so",
                evidence.evidenceKey());

        StagedFactBatch batch = store.stage(
                running,
                List.of(runningScope),
                List.of(exportFact, referenceFact),
                List.of(evidence));

        assertEquals(List.of(exportFact, referenceFact), batch.facts());
    }

    @Test
    void rejectsNonRunningRunsBeforeStaging() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        AnalysisRun planned = AnalysisRun.planned("analysis-1", "shop", "snapshot-2");
        ScopeRun runningScope = ScopeRun.planned(
                "scope-1",
                "analysis-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                RelationFamily.CALL).start();
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());

        assertThrows(IllegalStateException.class,
                () -> store.stage(planned, List.of(runningScope), List.of(fact), List.of(evidence)));
    }

    @Test
    void commitsStagedFactsIntoActiveView() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", evidence, fact);

        CommittedFactBatch committed = store.commit("analysis-1");

        assertEquals(RunStatus.COMMITTED, committed.analysisRun().status());
        assertEquals(RunStatus.COMMITTED, committed.scopeRuns().getFirst().status());
        assertEquals(List.of(fact), committed.facts());
        assertEquals(List.of(fact), store.activeFacts("shop"));
        assertTrue(store.stagedFacts("analysis-1").isEmpty());
        assertTrue(store.stagedEvidence("analysis-1").isEmpty());
    }

    @Test
    void incrementalCommitKeepsFactsFromUntouchedScopesActive() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence firstEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord firstScopeFact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                "B",
                firstEvidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", firstEvidence, firstScopeFact);
        store.commit("analysis-1");

        Evidence secondEvidence = callEvidence("spoon", "src/main/java/com/foo/C.java");
        FactRecord secondScopeFact = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-2",
                "spoon",
                "src/main/java/com/foo/C.java",
                "D",
                secondEvidence.evidenceKey());
        stageOneScope(store, "analysis-2", "snapshot-3", "scope-2", secondEvidence, secondScopeFact);

        store.commit("analysis-2");

        List<FactRecord> activeFacts = store.activeFacts("shop");
        assertEquals(2, activeFacts.size());
        assertEquals(firstScopeFact.factKey(), activeFacts.getFirst().factKey());
        assertEquals("snapshot-3", activeFacts.getFirst().snapshotId());
        assertEquals("snapshot-2", activeFacts.getFirst().validFromSnapshot());
        assertEquals(secondScopeFact, activeFacts.get(1));
    }

    @Test
    void incrementalCommitCarriesUntouchedFactsIntoCommittedSnapshotView() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence firstEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord firstScopeFact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                "B",
                firstEvidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", firstEvidence, firstScopeFact);
        store.commit("analysis-1");

        Evidence secondEvidence = callEvidence("spoon", "src/main/java/com/foo/C.java");
        FactRecord secondScopeFact = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-2",
                "spoon",
                "src/main/java/com/foo/C.java",
                "D",
                secondEvidence.evidenceKey());
        stageOneScope(store, "analysis-2", "snapshot-3", "scope-2", secondEvidence, secondScopeFact);

        store.commit("analysis-2");

        FactRecord carriedForward = store.activeFacts("shop").getFirst();
        assertEquals(firstScopeFact.factKey(), carriedForward.factKey());
        assertEquals("snapshot-3", carriedForward.snapshotId());
        assertEquals("snapshot-2", carriedForward.validFromSnapshot());
        assertEquals("snapshot-3", MaterializedEdge.from(carriedForward).snapshotId());
    }

    @Test
    void commitTombstonesOnlyMissingFactsFromTouchedOwnerTuples() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence sourceEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord staleCall = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-call",
                "spoon",
                "src/main/java/com/foo/A.java",
                "B",
                sourceEvidence.evidenceKey());
        FactRecord untouchedDataFact = dataFact(
                "analysis-1",
                "snapshot-2",
                "scope-data",
                "spoon",
                "src/main/java/com/foo/A.java",
                sourceEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-1",
                "snapshot-2",
                List.of(
                        ScopeRun.planned(
                                "scope-call",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.CALL).start(),
                        ScopeRun.planned(
                                "scope-data",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.DATA).start()),
                List.of(staleCall, untouchedDataFact),
                List.of(sourceEvidence));
        store.commit("analysis-1");

        Evidence nextEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java", "line:20-22");
        FactRecord replacementCall = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-call-next",
                "spoon",
                "src/main/java/com/foo/A.java",
                "C",
                nextEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-2",
                "snapshot-3",
                List.of(ScopeRun.planned(
                        "scope-call-next",
                        "analysis-2",
                        "spoon",
                        "src/main/java/com/foo/A.java",
                        RelationFamily.CALL).start()),
                List.of(replacementCall),
                List.of(nextEvidence));

        store.commit("analysis-2");

        List<FactRecord> activeFacts = store.activeFacts("shop");
        assertEquals(2, activeFacts.size());
        assertEquals(untouchedDataFact.factKey(), activeFacts.getFirst().factKey());
        assertEquals("snapshot-3", activeFacts.getFirst().snapshotId());
        assertEquals("snapshot-2", activeFacts.getFirst().validFromSnapshot());
        assertEquals(replacementCall, activeFacts.get(1));
    }

    @Test
    void commitDoesNotTombstoneFactsFromUntouchedIdentityTypes() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence sourceEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord staleDataFact = dataFact(
                "analysis-1",
                "snapshot-2",
                "scope-data-symbol",
                "spoon",
                "src/main/java/com/foo/A.java",
                sourceEvidence.evidenceKey());
        FactRecord untouchedFlowFact = guardedFlowFact(
                "analysis-1",
                "snapshot-2",
                "scope-data-flow",
                "spoon",
                "src/main/java/com/foo/A.java",
                sourceEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-1",
                "snapshot-2",
                List.of(
                        ScopeRun.planned(
                                "scope-data-symbol",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.DATA).start(),
                        ScopeRun.planned(
                                "scope-data-flow",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.DATA,
                                IdentityType.FLOW_ID).start()),
                List.of(staleDataFact, untouchedFlowFact),
                List.of(sourceEvidence));
        store.commit("analysis-1");

        Evidence nextEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java", "line:20-22");
        FactRecord replacementDataFact = dataFact(
                "analysis-2",
                "snapshot-3",
                "scope-data-symbol-next",
                "spoon",
                "src/main/java/com/foo/A.java",
                nextEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-2",
                "snapshot-3",
                List.of(ScopeRun.planned(
                        "scope-data-symbol-next",
                        "analysis-2",
                        "spoon",
                        "src/main/java/com/foo/A.java",
                        RelationFamily.DATA,
                        IdentityType.SYMBOL_ID).start()),
                List.of(replacementDataFact),
                List.of(nextEvidence));

        store.commit("analysis-2");

        List<FactRecord> activeFacts = store.activeFacts("shop");
        assertEquals(2, activeFacts.size());
        assertTrue(activeFacts.stream().anyMatch(fact -> fact.factKey().equals(untouchedFlowFact.factKey())
                && fact.snapshotId().equals("snapshot-3")
                && fact.validFromSnapshot().equals("snapshot-2")));
        assertTrue(activeFacts.contains(replacementDataFact));
    }

    @Test
    void implicitIdentityScopeTombstonesFactsWhenRerunEmitsNoFacts() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence sourceEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord staleDataFact = dataFact(
                "analysis-1",
                "snapshot-2",
                "scope-data",
                "spoon",
                "src/main/java/com/foo/A.java",
                sourceEvidence.evidenceKey());
        FactRecord staleFlowFact = guardedFlowFact(
                "analysis-1",
                "snapshot-2",
                "scope-data",
                "spoon",
                "src/main/java/com/foo/A.java",
                sourceEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-1",
                "snapshot-2",
                List.of(ScopeRun.planned(
                        "scope-data",
                        "analysis-1",
                        "spoon",
                        "src/main/java/com/foo/A.java",
                        RelationFamily.DATA).start()),
                List.of(staleDataFact, staleFlowFact),
                List.of(sourceEvidence));
        store.commit("analysis-1");

        Evidence nextEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java", "line:20-22");
        stageScopes(
                store,
                "analysis-2",
                "snapshot-3",
                List.of(ScopeRun.planned(
                        "scope-data-next",
                        "analysis-2",
                        "spoon",
                        "src/main/java/com/foo/A.java",
                        RelationFamily.DATA).start()),
                List.of(),
                List.of(nextEvidence));

        store.commit("analysis-2");

        assertTrue(store.activeFacts("shop").isEmpty());
    }

    @Test
    void commitTriggersCacheRebuildForTouchedRelationFamiliesAfterActiveViewPublishes() {
        List<CacheRebuildRequest> requests = new ArrayList<>();
        InMemoryFactStagingStore store = new InMemoryFactStagingStore(requests::add);
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord callFact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-call",
                "spoon",
                "src/main/java/com/foo/A.java",
                "B",
                evidence.evidenceKey());
        FactRecord dataFact = dataFact(
                "analysis-1",
                "snapshot-2",
                "scope-data",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());
        stageScopes(
                store,
                "analysis-1",
                "snapshot-2",
                List.of(
                        ScopeRun.planned(
                                "scope-call",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.CALL).start(),
                        ScopeRun.planned(
                                "scope-data",
                                "analysis-1",
                                "spoon",
                                "src/main/java/com/foo/A.java",
                                RelationFamily.DATA).start()),
                List.of(callFact, dataFact),
                List.of(evidence));

        store.commit("analysis-1");

        assertEquals(List.of(new CacheRebuildRequest(
                "shop",
                "snapshot-2",
                List.of(RelationFamily.CALL, RelationFamily.DATA),
                2)), requests);
    }

    @Test
    void carriesCustomSourceRootFactsForwardToNextSnapshot() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence generatedEvidence = callEvidence("spring", "generated/sources/annotations/java/main/com/foo/Api.java");
        FactRecord generatedFact = customRootEndpointFact(
                "analysis-1",
                "snapshot-2",
                "scope-generated",
                "spring",
                "generated/sources/annotations/java/main/com/foo/Api.java",
                generatedEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-1",
                "snapshot-2",
                List.of(ScopeRun.planned(
                        "scope-generated",
                        "analysis-1",
                        "spring",
                        "generated/sources/annotations/java/main/com/foo/Api.java",
                        RelationFamily.CALL).start()),
                List.of(generatedFact),
                List.of(generatedEvidence));
        store.commit("analysis-1");

        Evidence nextEvidence = callEvidence("spoon", "src/main/java/com/foo/Other.java");
        FactRecord nextFact = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-next",
                "spoon",
                "src/main/java/com/foo/Other.java",
                nextEvidence.evidenceKey());
        stageScopes(
                store,
                "analysis-2",
                "snapshot-3",
                List.of(ScopeRun.planned(
                        "scope-next",
                        "analysis-2",
                        "spoon",
                        "src/main/java/com/foo/Other.java",
                        RelationFamily.CALL).start()),
                List.of(nextFact),
                List.of(nextEvidence));

        store.commit("analysis-2");

        assertTrue(store.activeFacts("shop").stream()
                .anyMatch(fact -> fact.factKey().equals(generatedFact.factKey())
                        && fact.snapshotId().equals("snapshot-3")
                        && fact.validFromSnapshot().equals("snapshot-2")));
    }

    @Test
    void rollbackDoesNotTriggerCacheRebuild() {
        List<CacheRebuildRequest> requests = new ArrayList<>();
        InMemoryFactStagingStore store = new InMemoryFactStagingStore(requests::add);
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", evidence, fact);

        store.rollback("analysis-1");

        assertTrue(requests.isEmpty());
    }

    @Test
    void failedAnalysisDiscardsStagedFactsWithoutPublishingActiveView() {
        List<CacheRebuildRequest> requests = new ArrayList<>();
        InMemoryFactStagingStore store = new InMemoryFactStagingStore(requests::add);
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", evidence, fact);

        AnalysisRun failed = store.fail("analysis-1");

        assertEquals(RunStatus.FAILED, failed.status());
        assertTrue(store.activeFacts("shop").isEmpty());
        assertTrue(store.stagedFacts("analysis-1").isEmpty());
        assertTrue(store.stagedEvidence("analysis-1").isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void cacheRebuildFailureDoesNotRollbackCommittedActiveView() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore(request -> {
            throw new IllegalStateException("cache unavailable");
        });
        Evidence evidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord fact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                evidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", evidence, fact);

        CommittedFactBatch committed = store.commit("analysis-1");

        assertEquals(RunStatus.COMMITTED, committed.analysisRun().status());
        assertEquals(List.of(fact), store.activeFacts("shop"));
    }

    @Test
    void currentReportExcludesTombstonedRelations() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence staleEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord staleCall = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-call",
                "spoon",
                "src/main/java/com/foo/A.java",
                "B",
                staleEvidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-call", staleEvidence, staleCall);
        store.commit("analysis-1");

        Evidence replacementEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java", "line:20-22");
        FactRecord replacementCall = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-call-next",
                "spoon",
                "src/main/java/com/foo/A.java",
                "C",
                replacementEvidence.evidenceKey());
        stageOneScope(store, "analysis-2", "snapshot-3", "scope-call-next", replacementEvidence, replacementCall);
        store.commit("analysis-2");

        CurrentFactReport report = store.currentReport("shop");

        assertEquals("shop", report.projectId());
        assertEquals(List.of(replacementCall), report.facts());
        assertEquals(List.of(MaterializedEdge.from(replacementCall)), report.edges());
    }

    @Test
    void rollbackDiscardsStagedBatchAndPreservesPreviousActiveFacts() {
        InMemoryFactStagingStore store = new InMemoryFactStagingStore();
        Evidence committedEvidence = callEvidence("spoon", "src/main/java/com/foo/A.java");
        FactRecord committedFact = callFact(
                "analysis-1",
                "snapshot-2",
                "scope-1",
                "spoon",
                "src/main/java/com/foo/A.java",
                committedEvidence.evidenceKey());
        stageOneScope(store, "analysis-1", "snapshot-2", "scope-1", committedEvidence, committedFact);
        store.commit("analysis-1");

        Evidence stagedEvidence = callEvidence("spoon", "src/main/java/com/foo/B.java");
        FactRecord stagedFact = callFact(
                "analysis-2",
                "snapshot-3",
                "scope-2",
                "spoon",
                "src/main/java/com/foo/B.java",
                stagedEvidence.evidenceKey());
        stageOneScope(store, "analysis-2", "snapshot-3", "scope-2", stagedEvidence, stagedFact);

        AnalysisRun rolledBack = store.rollback("analysis-2");

        assertEquals(RunStatus.ROLLED_BACK, rolledBack.status());
        assertEquals(List.of(committedFact), store.activeFacts("shop"));
        assertTrue(store.stagedFacts("analysis-2").isEmpty());
        assertTrue(store.stagedEvidence("analysis-2").isEmpty());
    }

    private static Evidence callEvidence(String analyzerId, String scopeKey) {
        return callEvidence(analyzerId, scopeKey, "line:12-14");
    }

    private static Evidence callEvidence(String analyzerId, String scopeKey, String location) {
        return Evidence.create(
                analyzerId,
                scopeKey,
                scopeKey,
                location,
                1,
                SourceType.SPOON);
    }

    private static FactRecord callFact(
            String analysisRunId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return callFact(analysisRunId, "snapshot-2", scopeRunId, analyzerId, scopeKey, evidenceKey);
    }

    private static FactRecord callFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return callFact(analysisRunId, snapshotId, scopeRunId, analyzerId, scopeKey, "B", evidenceKey);
    }

    private static FactRecord callFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String targetClassName,
            String evidenceKey) {
        return FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "method://shop/_root/src/main/java/com.foo." + targetClassName + "#b()V",
                "CALLS",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static FactRecord dataFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "field://shop/_root/src/main/java/com.foo.A#status:Ljava/lang/String;",
                "READS_FIELD",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static FactRecord guardedFlowFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "request-param://shop/_root/orderId",
                "GUARDED_BY",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static FactRecord requestParamFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.A#a()V",
                "request-param://shop/_root/orderId",
                "READS_PARAM",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.IMPACT_FLOW);
    }

    private static FactRecord planningFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "feature-seed://shop/run-20260502-001/checkout",
                "method://shop/_root/src/main/java/com.foo.CheckoutService#submit()V",
                "MATCHES",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.POSSIBLE,
                80,
                SourceType.USER_CONFIRMATION);
    }

    private static FactRecord workflowWatchFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "saved-query://shop/_root/checkout-watch",
                "method://shop/_root/src/main/java/com.foo.CheckoutService#submit()V",
                "WATCHES",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                70,
                SourceType.USER_CONFIRMATION);
    }

    private static FactRecord planningArtifactFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "feature-seed://shop/run-20260502-001/checkout",
                "feature-scope://shop/run-20260502-001/checkout",
                "MATCHES",
                "direct",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.POSSIBLE,
                80,
                SourceType.USER_CONFIRMATION);
    }

    private static FactRecord workflowArtifactFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "export-artifact://shop/run-20260502-001/checkout-plan-md",
                "change-plan://shop/run-20260502-001/checkout-plan",
                "EXPORTED_AS",
                "markdown",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                90,
                SourceType.USER_CONFIRMATION);
    }

    private static FactRecord boundaryExportFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "native-library://shop/_root/src/main/resources/native/libpayment.so",
                "boundary-symbol://shop/_root/src/main/resources/native/libpayment.so/exports/capturePayment",
                "EXPORTS_SYMBOL",
                "native-export",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.BOUNDARY_SYMBOL);
    }

    private static FactRecord boundaryReferenceFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                "method://shop/_root/src/main/java/com.foo.PaymentService#capture()V",
                "boundary-symbol://shop/_root/src/main/resources/native/libpayment.so/exports/capturePayment",
                "REFERENCES_SYMBOL",
                "jni-call",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.BOUNDARY_SYMBOL);
    }

    private static FactRecord customRootEndpointFact(
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            String analyzerId,
            String scopeKey,
            String evidenceKey) {
        return FactRecord.create(
                List.of("generated/sources/annotations/java/main"),
                "method://shop/_root/generated/sources/annotations/java/main/com.foo.Api#list()V",
                "api-endpoint://shop/_root/generated/sources/annotations/java/main/GET:/orders",
                "ROUTES_TO",
                "spring",
                "shop",
                snapshotId,
                analysisRunId,
                scopeRunId,
                analyzerId,
                scopeKey,
                evidenceKey,
                Confidence.CERTAIN,
                100,
                SourceType.SPOON);
    }

    private static void stageOneScope(
            InMemoryFactStagingStore store,
            String analysisRunId,
            String snapshotId,
            String scopeRunId,
            Evidence evidence,
            FactRecord fact) {
        AnalysisRun running = AnalysisRun.planned(analysisRunId, "shop", snapshotId).start();
        ScopeRun runningScope = ScopeRun.planned(
                scopeRunId,
                analysisRunId,
                fact.analyzerId(),
                fact.scopeKey(),
                RelationFamily.CALL).start();

        store.stage(running, List.of(runningScope), List.of(fact), List.of(evidence));
    }

    private static void stageScopes(
            InMemoryFactStagingStore store,
            String analysisRunId,
            String snapshotId,
            List<ScopeRun> runningScopes,
            List<FactRecord> facts,
            List<Evidence> evidence) {
        AnalysisRun running = AnalysisRun.planned(analysisRunId, "shop", snapshotId).start();

        store.stage(running, runningScopes, facts, evidence);
    }
}
