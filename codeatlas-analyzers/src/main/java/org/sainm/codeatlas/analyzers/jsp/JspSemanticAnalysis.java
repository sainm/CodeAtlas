package org.sainm.codeatlas.analyzers.jsp;

import java.util.List;

public record JspSemanticAnalysis(
    List<JspDirective> directives,
    List<JspAction> actions,
    List<JspExpressionFragment> expressions,
    List<JspTaglibReference> taglibs,
    List<JspClientNavigation> clientNavigations,
    List<String> includes,
    String encoding,
    JspSemanticParserSource parserSource,
    String parserName,
    String fallbackReason
) {
    public JspSemanticAnalysis(
        List<JspDirective> directives,
        List<JspAction> actions,
        List<JspExpressionFragment> expressions,
        List<JspTaglibReference> taglibs,
        List<JspClientNavigation> clientNavigations,
        List<String> includes,
        String encoding
    ) {
        this(
            directives,
            actions,
            expressions,
            taglibs,
            clientNavigations,
            includes,
            encoding,
            JspSemanticParserSource.TOKENIZER_FALLBACK,
            "tolerant-jsp-tokenizer",
            null
        );
    }

    public JspSemanticAnalysis {
        directives = List.copyOf(directives);
        actions = List.copyOf(actions);
        expressions = List.copyOf(expressions);
        taglibs = List.copyOf(taglibs);
        clientNavigations = List.copyOf(clientNavigations);
        includes = List.copyOf(includes);
        encoding = encoding == null || encoding.isBlank() ? "UTF-8" : encoding.trim();
        parserSource = parserSource == null ? JspSemanticParserSource.TOKENIZER_FALLBACK : parserSource;
        parserName = parserName == null || parserName.isBlank() ? "tolerant-jsp-tokenizer" : parserName.trim();
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason.trim();
    }
}
