package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;

public record JspEncodingResolution(
    String encoding,
    String source,
    Confidence confidence
) {
    public JspEncodingResolution {
        encoding = encoding == null || encoding.isBlank() ? "UTF-8" : encoding.trim();
        source = source == null || source.isBlank() ? "default" : source.trim();
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
    }
}
