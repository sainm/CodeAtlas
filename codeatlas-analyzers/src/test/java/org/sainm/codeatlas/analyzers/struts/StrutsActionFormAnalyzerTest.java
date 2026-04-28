package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsActionFormAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void bindsActionFormFieldsToRequestParameters() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserForm.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import org.apache.struts.action.ActionForm;

            class UserForm extends ActionForm {
                private String userId;
                private static String ignored;
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        StrutsActionFormAnalysisResult result = new StrutsActionFormAnalyzer().analyze(scope, "shop", "src/main/java", List.of(source));

        assertEquals(1, result.fields().size());
        assertEquals("userId", result.fields().getFirst().fieldName());
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("userId")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().target().kind() == SymbolKind.FIELD
            && fact.confidence() == Confidence.LIKELY));
    }
}
