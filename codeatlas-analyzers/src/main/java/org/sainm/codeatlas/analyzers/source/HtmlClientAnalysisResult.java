package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record HtmlClientAnalysisResult(
        List<HtmlPageInfo> pages,
        List<HtmlFormInfo> forms,
        List<HtmlInputInfo> inputs,
        List<HtmlLinkInfo> links,
        List<ScriptResourceInfo> scripts,
        List<ClientRequestInfo> clientRequests,
        List<DomEventHandlerInfo> domEventHandlers,
        List<JavaAnalysisDiagnostic> diagnostics) {
    public HtmlClientAnalysisResult {
        pages = List.copyOf(pages == null ? List.of() : pages);
        forms = List.copyOf(forms == null ? List.of() : forms);
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        links = List.copyOf(links == null ? List.of() : links);
        scripts = List.copyOf(scripts == null ? List.of() : scripts);
        clientRequests = List.copyOf(clientRequests == null ? List.of() : clientRequests);
        domEventHandlers = List.copyOf(domEventHandlers == null ? List.of() : domEventHandlers);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
