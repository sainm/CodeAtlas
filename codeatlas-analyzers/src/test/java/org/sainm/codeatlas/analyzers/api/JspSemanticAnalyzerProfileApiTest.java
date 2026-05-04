package org.sainm.codeatlas.analyzers.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.analyzers.source.JspAnalysisResult;
import org.sainm.codeatlas.analyzers.source.JspParserMode;
import org.sainm.codeatlas.analyzers.source.JspSemanticAnalyzer;
import org.sainm.codeatlas.analyzers.source.WebAppContext;

class JspSemanticAnalyzerProfileApiTest {
    @TempDir
    Path tempDir;

    @Test
    void publicApiAcceptsLegacyJavaxProfileClasspath() throws IOException {
        write("src/main/webapp/WEB-INF/jsp/user/simple.jsp", """
                <form action="/user/save.do"><input name="userId"></form>
                """);
        WebAppContext context = new WebAppContext(
                tempDir.resolve("src/main/webapp").toString(),
                tempDir.resolve("src/main/webapp/WEB-INF/web.xml").toString(),
                "4.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        Path legacyProfileClasses = compileLegacyJavaxProfile();

        JspAnalysisResult result = JspSemanticAnalyzer.usingJasperProfileClasspaths(Map.of(
                JspSemanticAnalyzer.TOMCAT_8_9_JAVAX_PROFILE,
                List.of(legacyProfileClasses))).analyze(
                        tempDir.resolve("src/main/webapp"),
                        context,
                        List.of(tempDir.resolve("src/main/webapp/WEB-INF/jsp/user/simple.jsp")));

        assertEquals(JspParserMode.JASPER, result.parserMode());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_ISOLATED_PROFILE_SELECTED")));
    }

    private Path compileLegacyJavaxProfile() throws IOException {
        Path sources = tempDir.resolve("profile-sources");
        Path classes = tempDir.resolve("profile-classes");
        writeSource(sources, "org/apache/jasper/JspC.java", """
                package org.apache.jasper;

                public final class JspC {
                    public void setUriroot(String uriroot) {}
                    public void setJspFiles(String jspFiles) {}
                    public void setOutputDir(String outputDir) {}
                    public void setCompile(boolean compile) {}
                    public void setValidateXml(boolean validateXml) {}
                    public void execute() {}
                }
                """);
        writeSource(sources, "javax/servlet/ServletContext.java", """
                package javax.servlet;

                public interface ServletContext {
                }
                """);
        writeSource(sources, "javax/servlet/jsp/JspFactory.java", """
                package javax.servlet.jsp;

                public class JspFactory {
                }
                """);
        Files.createDirectories(classes);
        int exitCode = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                sources.resolve("org/apache/jasper/JspC.java").toString(),
                sources.resolve("javax/servlet/ServletContext.java").toString(),
                sources.resolve("javax/servlet/jsp/JspFactory.java").toString());
        assertEquals(0, exitCode);
        return classes;
    }

    private void write(String relativePath, String content) throws IOException {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void writeSource(Path root, String relativePath, String content) throws IOException {
        Path path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
