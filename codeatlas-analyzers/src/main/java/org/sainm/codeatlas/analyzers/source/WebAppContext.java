package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record WebAppContext(
        String webRoot,
        String webXmlPath,
        String servletVersion,
        List<WebPageEncodingInfo> pageEncodings,
        List<String> tldFiles,
        List<String> tagFiles,
        List<String> webInfLibJars,
        List<String> classpathCandidates,
        List<StrutsConfigPathInfo> strutsConfigs,
        List<WebServletMappingInfo> servletMappings,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public WebAppContext {
        JavaClassInfo.requireNonBlank(webRoot, "webRoot");
        webXmlPath = webXmlPath == null ? "" : webXmlPath;
        servletVersion = servletVersion == null ? "" : servletVersion;
        pageEncodings = List.copyOf(pageEncodings == null ? List.of() : pageEncodings);
        tldFiles = List.copyOf(tldFiles == null ? List.of() : tldFiles);
        tagFiles = List.copyOf(tagFiles == null ? List.of() : tagFiles);
        webInfLibJars = List.copyOf(webInfLibJars == null ? List.of() : webInfLibJars);
        classpathCandidates = List.copyOf(classpathCandidates == null ? List.of() : classpathCandidates);
        strutsConfigs = List.copyOf(strutsConfigs == null ? List.of() : strutsConfigs);
        servletMappings = List.copyOf(servletMappings == null ? List.of() : servletMappings);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<String> strutsConfigPaths() {
        return strutsConfigs.stream().map(StrutsConfigPathInfo::path).toList();
    }
}
