package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JspIncludeResolver {
    public List<JspIncludeReference> resolve(Path jspFile, JspSemanticAnalysis analysis, WebAppContext context) {
        Path currentFile = jspFile.toAbsolutePath().normalize();
        List<JspIncludeReference> references = new ArrayList<>();
        for (JspDirective directive : analysis.directives()) {
            if (directive.name().equals("include")) {
                String rawPath = directive.attributes().get("file");
                addReference(references, JspIncludeType.STATIC_DIRECTIVE, rawPath, currentFile, context, directive.line());
            }
        }
        for (JspAction action : analysis.actions()) {
            if (action.name().equals("jsp:include")) {
                String rawPath = action.attributes().get("page");
                addReference(references, JspIncludeType.DYNAMIC_ACTION, rawPath, currentFile, context, action.line());
            }
        }
        return List.copyOf(references);
    }

    private void addReference(
        List<JspIncludeReference> references,
        JspIncludeType type,
        String rawPath,
        Path jspFile,
        WebAppContext context,
        int line
    ) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        Path resolvedPath = resolvePath(rawPath, jspFile, context);
        boolean exists = Files.exists(resolvedPath);
        references.add(new JspIncludeReference(
            type,
            rawPath,
            resolvedPath,
            exists,
            exists ? Confidence.CERTAIN : Confidence.POSSIBLE,
            line
        ));
    }

    private Path resolvePath(String rawPath, Path jspFile, WebAppContext context) {
        String normalized = rawPath.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            return context.webRoot().resolve(normalized.substring(1)).toAbsolutePath().normalize();
        }
        return jspFile.getParent().resolve(normalized).toAbsolutePath().normalize();
    }
}
