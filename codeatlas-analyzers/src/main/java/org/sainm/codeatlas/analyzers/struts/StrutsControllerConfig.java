package org.sainm.codeatlas.analyzers.struts;

import java.util.Map;

public record StrutsControllerConfig(
    Map<String, String> attributes
) {
    public StrutsControllerConfig {
        attributes = Map.copyOf(attributes);
    }
}
