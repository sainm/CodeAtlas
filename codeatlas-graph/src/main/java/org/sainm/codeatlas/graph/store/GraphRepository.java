package org.sainm.codeatlas.graph.store;

import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GraphRepository {
    GraphNode upsertNode(GraphNode node);

    default List<GraphNode> upsertNodes(Collection<GraphNode> nodes) {
        return nodes.stream().map(this::upsertNode).toList();
    }

    Optional<GraphNode> findNode(SymbolId symbolId);

    void upsertFact(GraphFact fact);

    default void upsertFacts(Collection<GraphFact> facts) {
        facts.forEach(this::upsertFact);
    }

    void reanalyzeScope(
        String projectId,
        String previousSnapshotId,
        String newSnapshotId,
        String analysisRunId,
        String scopeKey,
        Collection<GraphFact> emittedFacts
    );

    List<ActiveFact> activeFacts(String projectId, String snapshotId);

    Optional<ActiveFact> activeFact(String projectId, String snapshotId, FactKey factKey);

    SnapshotDiff diff(String projectId, String leftSnapshotId, String rightSnapshotId);
}
