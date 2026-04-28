package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JspEncodingResolverTest {
    private final WebAppContext context = new WebAppContext(Path.of("."), null, "2.4", "unknown", "generic", List.of(), Map.of(), "Windows-31J");

    @Test
    void prefersBomOverDirective() {
        String jsp = "<%@ page pageEncoding=\"Shift_JIS\" %>";
        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        JspEncodingResolution resolution = new JspEncodingResolver().resolve(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, analysis, context);

        assertEquals("UTF-8", resolution.encoding());
        assertEquals("bom", resolution.source());
        assertEquals(Confidence.CERTAIN, resolution.confidence());
    }

    @Test
    void resolvesPageDirectiveAndContentTypeCharset() {
        String jsp = "<%@ page contentType=\"text/html; charset=EUC-JP\" %>";
        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(jsp, context);

        JspEncodingResolution resolution = new JspEncodingResolver().resolve(new byte[0], analysis, context);

        assertEquals("EUC-JP", resolution.encoding());
        assertEquals("contentType", resolution.source());
        assertEquals(Confidence.CERTAIN, resolution.confidence());
    }

    @Test
    void fallsBackToWebAppDefaultEncoding() {
        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract("<html/>", context);

        JspEncodingResolution resolution = new JspEncodingResolver().resolve(new byte[0], analysis, context);

        assertEquals("Windows-31J", resolution.encoding());
        assertEquals("webAppDefault", resolution.source());
        assertEquals(Confidence.LIKELY, resolution.confidence());
    }
}
