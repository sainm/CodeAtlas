package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.RelationType;
import java.util.ArrayList;
import java.util.List;

public final class ImpactPathExplainer {
    public ImpactExplanation explain(ImpactPath path) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < path.steps().size(); i++) {
            ImpactPathStep step = path.steps().get(i);
            if (i == 0) {
                lines.add("Entrypoint " + step.symbolId().value() + " is included as the starting point.");
            } else {
                lines.add(describe(path.steps().get(i - 1), step));
            }
        }
        String summary = "Changed symbol " + path.changedSymbol().value()
            + " is reachable from entrypoint " + path.entrypoint().value()
            + " with " + path.confidence() + " confidence and " + path.riskLevel() + " risk.";
        return new ImpactExplanation(summary, lines);
    }

    private String describe(ImpactPathStep previous, ImpactPathStep current) {
        RelationType relation = current.incomingRelation();
        String relationText = switch (relation) {
            case CALLS -> "calls";
            case ROUTES_TO -> "routes to";
            case SUBMITS_TO -> "submits to";
            case BINDS_TO -> "binds to";
            case READS_TABLE -> "reads table";
            case WRITES_TABLE -> "writes table";
            case FORWARDS_TO -> "forwards to";
            case PASSES_PARAM -> "passes parameter to";
            case READS_PARAM -> "reads request parameter from";
            case WRITES_PARAM -> "writes request parameter to";
            case null -> "continues to";
            default -> relation.name().toLowerCase().replace('_', ' ');
        };
        return previous.symbolId().value() + " " + relationText + " " + current.symbolId().value()
            + " [" + current.confidence() + ", " + current.sourceType() + "].";
    }
}
