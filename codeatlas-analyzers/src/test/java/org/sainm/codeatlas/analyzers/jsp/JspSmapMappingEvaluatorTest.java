package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JspSmapMappingEvaluatorTest {
    @Test
    void recommendsSmapWhenJasperParsedJspJavaFragments() {
        JspSemanticAnalysis analysis = new JspSemanticAnalysis(
            List.of(),
            List.of(),
            List.of(new JspExpressionFragment("SCRIPTLET", "String id = request.getParameter(\"id\");", 12)),
            List.of(),
            List.of(),
            List.of(),
            "UTF-8",
            JspSemanticParserSource.APACHE_JASPER,
            "apache-jasper",
            null,
            List.of()
        );

        JspSmapMappingAssessment assessment = new JspSmapMappingEvaluator().assess(analysis);

        assertEquals(JspSmapMappingStatus.CANDIDATE, assessment.status());
        assertTrue(assessment.reasons().contains("jasperParsed"));
        assertTrue(assessment.reasons().contains("jspJavaFragments"));
        assertEquals(Map.of("parser", "apache-jasper", "source", "APACHE_JASPER"), assessment.evidence());
    }

    @Test
    void blocksSmapWhenJasperFellBack() {
        JspSemanticAnalysis analysis = new JspSemanticAnalysis(
            List.of(),
            List.of(),
            List.of(new JspExpressionFragment("SCRIPTLET", "broken", 3)),
            List.of(),
            List.of(),
            List.of(),
            "UTF-8",
            JspSemanticParserSource.TOKENIZER_FALLBACK,
            "tolerant-jsp-tokenizer",
            "Apache Jasper could not parse this JSP with the available webapp context",
            List.of("web.xml", "taglibRegistry")
        );

        JspSmapMappingAssessment assessment = new JspSmapMappingEvaluator().assess(analysis);

        assertEquals(JspSmapMappingStatus.BLOCKED, assessment.status());
        assertTrue(assessment.reasons().contains("jasperFallback"));
        assertTrue(assessment.reasons().contains("missing:web.xml"));
        assertTrue(assessment.reasons().contains("missing:taglibRegistry"));
    }

    @Test
    void defersSmapWhenPageHasNoJspJavaFragments() {
        JspSemanticAnalysis analysis = new JspSemanticAnalysis(
            List.of(),
            List.of(new JspAction("jsp:include", Map.of("page", "/part.jsp"), 4)),
            List.of(new JspExpressionFragment("EL", "user.name", 6)),
            List.of(),
            List.of(),
            List.of(),
            "UTF-8",
            JspSemanticParserSource.APACHE_JASPER,
            "apache-jasper",
            null,
            List.of()
        );

        JspSmapMappingAssessment assessment = new JspSmapMappingEvaluator().assess(analysis);

        assertEquals(JspSmapMappingStatus.DEFERRED, assessment.status());
        assertTrue(assessment.reasons().contains("noJspJavaFragments"));
    }
}
