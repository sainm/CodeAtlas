package org.sainm.codeatlas.analyzers.jsp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record WebAppContext(
    Path webRoot,
    Path webXml,
    String servletVersion,
    String jspVersion,
    String containerProfile,
    List<Path> classpathEntries,
    Map<String, String> taglibs,
    String defaultEncoding
) {
    public WebAppContext {
        if (webRoot == null) {
            throw new IllegalArgumentException("webRoot is required");
        }
        webRoot = webRoot.toAbsolutePath().normalize();
        webXml = webXml == null ? webRoot.resolve("WEB-INF/web.xml").toAbsolutePath().normalize() : webXml.toAbsolutePath().normalize();
        servletVersion = servletVersion == null || servletVersion.isBlank() ? "unknown" : servletVersion.trim();
        jspVersion = jspVersion == null || jspVersion.isBlank() ? "unknown" : jspVersion.trim();
        containerProfile = containerProfile == null || containerProfile.isBlank() ? "generic" : containerProfile.trim();
        classpathEntries = List.copyOf(classpathEntries);
        taglibs = Map.copyOf(taglibs);
        defaultEncoding = defaultEncoding == null || defaultEncoding.isBlank() ? "UTF-8" : defaultEncoding.trim();
    }
}
