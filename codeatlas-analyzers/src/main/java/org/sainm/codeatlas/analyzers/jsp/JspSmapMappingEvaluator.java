package org.sainm.codeatlas.analyzers.jsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JspSmapMappingEvaluator {
    public JspSmapMappingAssessment assess(JspSemanticAnalysis analysis) {
        if (analysis == null) {
            return new JspSmapMappingAssessment(JspSmapMappingStatus.BLOCKED, List.of("missingSemanticAnalysis"), Map.of());
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("parser", analysis.parserName());
        evidence.put("source", analysis.parserSource().name());

        List<String> reasons = new ArrayList<>();
        if (analysis.parserSource() == JspSemanticParserSource.TOKENIZER_FALLBACK
            || analysis.parserSource() == JspSemanticParserSource.JERICHO_WITH_TOKENIZER_MERGE) {
            reasons.add("jasperFallback");
            for (String missing : analysis.missingContext()) {
                reasons.add("missing:" + missing);
            }
            if (analysis.fallbackReason() != null) {
                evidence.put("fallbackReason", analysis.fallbackReason());
            }
            return new JspSmapMappingAssessment(JspSmapMappingStatus.BLOCKED, reasons, evidence);
        }

        if (!hasJspJavaFragments(analysis)) {
            reasons.add("noJspJavaFragments");
            return new JspSmapMappingAssessment(JspSmapMappingStatus.DEFERRED, reasons, evidence);
        }

        reasons.add("jasperParsed");
        reasons.add("jspJavaFragments");
        return new JspSmapMappingAssessment(JspSmapMappingStatus.CANDIDATE, reasons, evidence);
    }

    private boolean hasJspJavaFragments(JspSemanticAnalysis analysis) {
        return analysis.expressions().stream()
            .map(JspExpressionFragment::kind)
            .anyMatch(kind -> kind.equals("SCRIPTLET") || kind.equals("EXPRESSION"));
    }
}
