package org.sainm.codeatlas.worker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.neo4j.CypherExecutor;
import org.sainm.codeatlas.graph.neo4j.Neo4jCypherBuilder;
import org.sainm.codeatlas.graph.neo4j.Neo4jGraphWriter;

public final class TaiEAnalysisResultImporter {
    private final String projectId;
    private final String snapshotId;
    private final String analysisRunId;
    private final String scopeKey;
    private final TaiESignatureMapper signatureMapper;

    public TaiEAnalysisResultImporter(
        String projectId,
        String snapshotId,
        String analysisRunId,
        String scopeKey,
        TaiESignatureMapper signatureMapper
    ) {
        this.projectId = require(projectId, "projectId");
        this.snapshotId = require(snapshotId, "snapshotId");
        this.analysisRunId = require(analysisRunId, "analysisRunId");
        this.scopeKey = scopeKey == null || scopeKey.isBlank() ? "tai-e" : scopeKey.trim();
        if (signatureMapper == null) {
            throw new IllegalArgumentException("signatureMapper is required");
        }
        this.signatureMapper = signatureMapper;
    }

    public TaiEAnalysisImportResult importCallEdges(List<TaiEMethodCallEdge> callEdges, CypherExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor is required");
        }
        if (callEdges == null || callEdges.isEmpty()) {
            return new TaiEAnalysisImportResult(0, 0);
        }
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphFact> facts = new ArrayList<>();
        for (TaiEMethodCallEdge callEdge : callEdges) {
            if (callEdge == null) {
                continue;
            }
            SymbolId caller = signatureMapper.mapMethod(callEdge.callerSignature());
            SymbolId callee = signatureMapper.mapMethod(callEdge.calleeSignature());
            nodes.putIfAbsent(caller.value(), methodNode(caller));
            nodes.putIfAbsent(callee.value(), methodNode(callee));
            facts.add(callFact(caller, callee, callEdge));
        }
        Neo4jGraphWriter writer = new Neo4jGraphWriter(executor, new Neo4jCypherBuilder());
        writer.upsertNodes(List.copyOf(nodes.values()));
        writer.upsertFacts(facts);
        return new TaiEAnalysisImportResult(nodes.size(), facts.size());
    }

    private GraphNode methodNode(SymbolId symbolId) {
        return new GraphNode(symbolId, Set.of(NodeRole.CODE_MEMBER), GraphNodeFactory.jvmMethodProperties(false, false));
    }

    private GraphFact callFact(SymbolId caller, SymbolId callee, TaiEMethodCallEdge callEdge) {
        int line = callEdge.lineNumber();
        return GraphFact.active(
            new FactKey(caller, RelationType.CALLS, callee, callEdge.qualifier()),
            new EvidenceKey(SourceType.TAI_E, "tai-e", callEdge.evidencePath(), line, line, callEdge.qualifier()),
            projectId,
            snapshotId,
            analysisRunId,
            scopeKey,
            Confidence.LIKELY,
            SourceType.TAI_E
        );
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
