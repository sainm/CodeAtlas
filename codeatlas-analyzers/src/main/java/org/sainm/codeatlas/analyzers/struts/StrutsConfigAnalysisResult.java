package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.List;

public record StrutsConfigAnalysisResult(
    List<StrutsFormBean> formBeans,
    List<StrutsActionMapping> actionMappings,
    List<StrutsForward> forwards,
    List<StrutsPlugin> plugins,
    List<StrutsControllerConfig> controllers,
    List<StrutsMessageResource> messageResources,
    List<StrutsExceptionMapping> exceptions,
    List<GraphNode> nodes,
    List<GraphFact> facts
) {
    public StrutsConfigAnalysisResult(
        List<StrutsFormBean> formBeans,
        List<StrutsActionMapping> actionMappings,
        List<StrutsForward> forwards,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        this(formBeans, actionMappings, forwards, List.of(), List.of(), List.of(), List.of(), nodes, facts);
    }

    public StrutsConfigAnalysisResult {
        formBeans = List.copyOf(formBeans);
        actionMappings = List.copyOf(actionMappings);
        forwards = List.copyOf(forwards);
        plugins = List.copyOf(plugins);
        controllers = List.copyOf(controllers);
        messageResources = List.copyOf(messageResources);
        exceptions = List.copyOf(exceptions);
        nodes = List.copyOf(nodes);
        facts = List.copyOf(facts);
    }
}
