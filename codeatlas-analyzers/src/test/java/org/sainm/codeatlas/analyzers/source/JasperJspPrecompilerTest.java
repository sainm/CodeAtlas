package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperJspPrecompilerTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetJasperStub() {
        org.apache.jasper.JspC.reset();
    }

    @Test
    void skipsJasperInvocationWhenRuntimeProfileIsMixed() {
        JasperJspPrecompiler precompiler = JasperJspPrecompiler.using(JasperRuntimeProbe.using(Set.of(
                "org.apache.jasper.JspC",
                "jakarta.servlet.ServletContext",
                "javax.servlet.jsp.JspFactory")::contains));

        JspParseAttempt attempt = precompiler.precompile(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.TOKEN_FALLBACK, attempt.parserMode());
        assertEquals(0, org.apache.jasper.JspC.executeCalls);
        assertTrue(attempt.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_PROFILE_MISMATCH_TOKEN_FALLBACK")));
    }
}
