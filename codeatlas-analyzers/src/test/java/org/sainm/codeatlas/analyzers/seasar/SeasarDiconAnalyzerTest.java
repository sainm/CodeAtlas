package org.sainm.codeatlas.analyzers.seasar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.RelationType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeasarDiconAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversComponentsAsPossibleBindings() throws Exception {
        Path dicon = tempDir.resolve("app.dicon");
        Files.writeString(dicon, """
            <components>
              <component name="userService" class="com.acme.service.UserService"/>
              <component name="userDao" class="com.acme.dao.UserDao"/>
            </components>
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "app.dicon", tempDir);
        SeasarDiconAnalysisResult result = new SeasarDiconAnalyzer().analyze(scope, "shop", "src/main/resources", dicon);

        assertEquals(2, result.components().size());
        assertTrue(result.facts().stream().allMatch(fact -> fact.confidence() == Confidence.POSSIBLE));
        assertTrue(result.facts().stream().allMatch(fact -> fact.factKey().relationType() == RelationType.BINDS_TO));
    }
}
