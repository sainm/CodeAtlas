package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperProfileClassLoaderFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void selectsIsolatedJakartaProfileForJakartaContext() {
        JasperProfileClassLoaderFactory factory = JasperProfileClassLoaderFactory.using(Map.of(
                "TOMCAT_10_JAKARTA", Set.of(
                        "org.apache.jasper.JspC",
                        "jakarta.servlet.ServletContext",
                        "jakarta.servlet.jsp.JspFactory"),
                "TOMCAT_8_9_JAVAX", Set.of(
                        "org.apache.jasper.JspC",
                        "javax.servlet.ServletContext",
                        "javax.servlet.jsp.JspFactory")));

        JasperRuntimeProbe probe = factory.probeFor(JasperProjectContext.jakarta());
        JasperRuntimeProfile profile = probe.probe();

        assertEquals("TOMCAT_10_JAKARTA", profile.jasperProfile());
        assertTrue(profile.canInvokeJasper());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("classloader=isolated")));
    }

    @Test
    void keepsJavaxAndJakartaProfilesIsolated() {
        JasperProfileClassLoaderFactory factory = JasperProfileClassLoaderFactory.using(Map.of(
                "TOMCAT_10_JAKARTA", Set.of(
                        "org.apache.jasper.JspC",
                        "jakarta.servlet.ServletContext",
                        "jakarta.servlet.jsp.JspFactory"),
                "TOMCAT_8_9_JAVAX", Set.of(
                        "org.apache.jasper.JspC",
                        "javax.servlet.ServletContext",
                        "javax.servlet.jsp.JspFactory")));

        JasperRuntimeProbe jakartaProbe = factory.probeFor(JasperProjectContext.jakarta());
        JasperRuntimeProbe javaxProbe = factory.probeFor(JasperProjectContext.javax());

        assertNotSame(jakartaProbe, javaxProbe);
        assertEquals("TOMCAT_10_JAKARTA", jakartaProbe.probe().jasperProfile());
        assertEquals("TOMCAT_8_9_JAVAX", javaxProbe.probe().jasperProfile());
    }

    @Test
    void isolatedUrlClassLoaderDoesNotSeeAnalyzerClasspathJasper() {
        JasperProfileClassLoaderFactory factory = JasperProfileClassLoaderFactory.usingProfileClasspaths(Map.of(
                "TOMCAT_10_JAKARTA", List.of(tempDir)));

        JasperRuntimeProfile profile = factory.probeFor(JasperProjectContext.jakarta()).probe();

        assertFalse(profile.jasperAvailable());
        assertEquals("TOKEN_ONLY", profile.jasperProfile());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("classloader=isolated")));
    }

    @Test
    void derivesProjectContextFromServletVersion() {
        WebAppContext jakartaContext = new WebAppContext(
                "src/main/webapp",
                "src/main/webapp/WEB-INF/web.xml",
                "5.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        WebAppContext javaxContext = new WebAppContext(
                "src/main/webapp",
                "src/main/webapp/WEB-INF/web.xml",
                "4.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertEquals("TOMCAT_10_JAKARTA", JasperProjectContext.from(jakartaContext).preferredProfile());
        assertEquals("TOMCAT_8_9_JAVAX", JasperProjectContext.from(javaxContext).preferredProfile());
    }

    @Test
    void doesNotUseCurrentClasspathWhenRequestedIsolatedProfileIsMissing() {
        JasperProfileClassLoaderFactory factory = JasperProfileClassLoaderFactory.using(Map.of(
                "TOMCAT_10_JAKARTA", Set.of(
                        "org.apache.jasper.JspC",
                        "jakarta.servlet.ServletContext",
                        "jakarta.servlet.jsp.JspFactory")));

        JasperRuntimeProfile profile = factory.probeFor(JasperProjectContext.javax()).probe();

        assertFalse(profile.canInvokeJasper());
        assertEquals("TOKEN_ONLY", profile.jasperProfile());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_ISOLATED_PROFILE_MISSING")));
    }

    @Test
    void tokenOnlyContextDoesNotUseCurrentClasspathJasper() {
        JasperRuntimeProfile profile = JasperProfileClassLoaderFactory.defaults()
                .probeFor(new JasperProjectContext(""))
                .probe();

        assertFalse(profile.canInvokeJasper());
        assertEquals("TOKEN_ONLY", profile.jasperProfile());
    }
}
