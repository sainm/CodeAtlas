package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsWebXmlAnalysisResult(
    List<String> servletNames,
    List<String> configLocations,
    List<String> urlPatterns
) {
    public StrutsWebXmlAnalysisResult {
        servletNames = List.copyOf(servletNames);
        configLocations = List.copyOf(configLocations);
        urlPatterns = List.copyOf(urlPatterns);
    }
}
