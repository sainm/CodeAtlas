package org.sainm.codeatlas.analyzers.source;

public record WebServletMappingInfo(
        String servletName,
        String urlPattern) {
    public WebServletMappingInfo {
        JavaClassInfo.requireNonBlank(servletName, "servletName");
        JavaClassInfo.requireNonBlank(urlPattern, "urlPattern");
    }
}
