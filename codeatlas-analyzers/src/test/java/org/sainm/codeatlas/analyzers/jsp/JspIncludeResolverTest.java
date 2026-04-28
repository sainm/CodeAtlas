package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JspIncludeResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesStaticAndDynamicIncludesAgainstWebRootAndCurrentDirectory() throws Exception {
        Path page = tempDir.resolve("views/users/detail.jsp");
        Path header = tempDir.resolve("common/header.jsp");
        Path part = tempDir.resolve("views/users/part.jsp");
        Files.createDirectories(page.getParent());
        Files.createDirectories(header.getParent());
        Files.writeString(page, "");
        Files.writeString(header, "");
        Files.writeString(part, "");
        String jsp = """
            <%@ include file="/common/header.jsp" %>
            <jsp:include page="part.jsp"/>
            <jsp:include page="/missing.jsp"/>
            """;
        WebAppContext context = new WebAppContext(tempDir, null, "2.4", "unknown", "generic", List.of(), Map.of(), "UTF-8");
        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        List<JspIncludeReference> references = new JspIncludeResolver().resolve(page, analysis, context);

        assertEquals(3, references.size());
        assertEquals(JspIncludeType.STATIC_DIRECTIVE, references.get(0).type());
        assertTrue(references.stream().anyMatch(reference -> reference.resolvedPath().equals(header.toAbsolutePath().normalize())
            && reference.confidence() == Confidence.CERTAIN));
        assertTrue(references.stream().anyMatch(reference -> reference.resolvedPath().equals(part.toAbsolutePath().normalize())
            && reference.type() == JspIncludeType.DYNAMIC_ACTION));
        assertTrue(references.stream().anyMatch(reference -> reference.rawPath().equals("/missing.jsp")
            && !reference.exists()
            && reference.confidence() == Confidence.POSSIBLE));
    }
}
