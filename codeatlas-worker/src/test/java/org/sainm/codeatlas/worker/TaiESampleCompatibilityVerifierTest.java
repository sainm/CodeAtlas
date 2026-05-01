package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaiESampleCompatibilityVerifierTest {
    @TempDir
    Path tempDir;

    @Test
    void verifiesClasspathInputsAndSignatureMappingForSampleProject() throws Exception {
        Path classes = tempDir.resolve("build/classes/java/main");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("UserService.class"), "placeholder");
        TaiEWorkerRequest request = new TaiEWorkerRequest(
            Path.of("java"),
            Path.of("tai-e-all.jar"),
            tempDir,
            tempDir.resolve("tai-e-output"),
            List.of(classes),
            List.of(classes),
            "",
            List.of("com.acme.UserService"),
            TaiEAnalysisProfile.callGraph().analysisOptions(),
            17,
            true,
            true,
            "2g",
            Duration.ofMinutes(1)
        );

        TaiESampleCompatibilityResult result = new TaiESampleCompatibilityVerifier(
            new TaiESignatureMapper("shop", "_root", "bytecode")
        ).verify(
            request,
            List.of("<com.acme.UserService: void save(java.lang.String)>")
        );

        assertTrue(result.compatible());
        assertEquals(1, result.classPathCount());
        assertEquals(1, result.mappedSignatureCount());
        assertTrue(result.messages().isEmpty());
    }

    @Test
    void reportsMissingClasspathAndUnmappedSignature() {
        TaiEWorkerRequest request = new TaiEWorkerRequest(
            Path.of("java"),
            Path.of("tai-e-all.jar"),
            tempDir,
            tempDir.resolve("tai-e-output"),
            List.of(tempDir.resolve("missing")),
            List.of(),
            "",
            List.of(),
            TaiEAnalysisProfile.callGraph().analysisOptions(),
            17,
            true,
            true,
            "2g",
            Duration.ofMinutes(1)
        );

        TaiESampleCompatibilityResult result = new TaiESampleCompatibilityVerifier(
            new TaiESignatureMapper("shop", "_root", "bytecode")
        ).verify(
            request,
            List.of("broken")
        );

        assertEquals(false, result.compatible());
        assertEquals(0, result.classPathCount());
        assertEquals(0, result.mappedSignatureCount());
        assertEquals(2, result.messages().size());
    }
}
