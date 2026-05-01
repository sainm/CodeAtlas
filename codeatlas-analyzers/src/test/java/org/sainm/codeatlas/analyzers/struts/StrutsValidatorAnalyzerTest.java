package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsValidatorAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsValidatorFormsFieldsAndDependsRules() throws Exception {
        Path validation = tempDir.resolve("WEB-INF/validation.xml");
        Files.createDirectories(validation.getParent());
        Files.writeString(validation, """
            <form-validation>
              <formset>
                <form name="userForm">
                  <field property="name" depends="required,maxlength"/>
                  <field property="email" depends="email"/>
                </form>
              </formset>
            </form-validation>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "WEB-INF/validation.xml", tempDir);
        StrutsValidatorAnalysisResult result = new StrutsValidatorAnalyzer().analyze(scope, "shop", "src/main/webapp", validation);

        assertEquals(1, result.forms().size());
        assertEquals(2, result.forms().getFirst().fields().size());
        assertTrue(result.forms().getFirst().fields().stream().anyMatch(field -> field.property().equals("name")
            && field.depends().contains("required")));
        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("email")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.COVERED_BY
            && fact.factKey().source().kind() == SymbolKind.REQUEST_PARAMETER
            && fact.factKey().source().ownerQualifiedName().equals("name")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.USES_CONFIG
            && fact.factKey().qualifier().equals("validator-depends:maxlength")));
    }
}
