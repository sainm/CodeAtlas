package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.List;

public interface ActiveFactStore {
    List<ActiveFact> activeFacts(String projectId, String snapshotId);
}
