package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryActiveFactStore implements ActiveFactStore {
    private final Map<String, List<ActiveFact>> facts = new ConcurrentHashMap<>();

    @Override
    public List<ActiveFact> activeFacts(String projectId, String snapshotId) {
        return facts.getOrDefault(key(projectId, snapshotId), List.of());
    }

    public void put(String projectId, String snapshotId, List<ActiveFact> activeFacts) {
        facts.put(key(projectId, snapshotId), List.copyOf(activeFacts));
    }

    private String key(String projectId, String snapshotId) {
        return projectId + "|" + snapshotId;
    }
}
