package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsWebXmlAnalysisResult(
    List<String> servletNames,
    List<String> configLocations,
    List<String> urlPatterns,
    List<StrutsModuleConfig> moduleConfigs
) {
    public StrutsWebXmlAnalysisResult(List<String> servletNames, List<String> configLocations, List<String> urlPatterns) {
        this(
            servletNames,
            configLocations,
            urlPatterns,
            configLocations.stream()
                .map(location -> new StrutsModuleConfig("", location))
                .toList()
        );
    }

    public StrutsWebXmlAnalysisResult {
        servletNames = List.copyOf(servletNames);
        configLocations = List.copyOf(configLocations);
        urlPatterns = List.copyOf(urlPatterns);
        moduleConfigs = List.copyOf(moduleConfigs);
    }
}
