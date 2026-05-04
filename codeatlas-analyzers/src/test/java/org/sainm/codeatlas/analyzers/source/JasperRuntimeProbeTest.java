package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import org.junit.jupiter.api.Test;

class JasperRuntimeProbeTest {
    @Test
    void reportsJakartaRuntimeProfileWhenJasperAndJakartaApisArePresent() {
        JasperRuntimeProfile profile = JasperRuntimeProbe.using(Set.of(
                "org.apache.jasper.JspC",
                "jakarta.servlet.ServletContext",
                "jakarta.servlet.jsp.JspFactory")::contains).probe();

        assertTrue(profile.jasperAvailable());
        assertTrue(profile.canInvokeJasper());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_RUNTIME_PROFILE")
                        && diagnostic.message().contains("profile=TOMCAT_10_JAKARTA")
                        && diagnostic.message().contains("servlet=jakarta")
                        && diagnostic.message().contains("jsp=jakarta")));
    }

    @Test
    void reportsMixedServletAndJspNamespacesAsNotInvokable() {
        JasperRuntimeProfile profile = JasperRuntimeProbe.using(Set.of(
                "org.apache.jasper.JspC",
                "jakarta.servlet.ServletContext",
                "javax.servlet.jsp.JspFactory")::contains).probe();

        assertTrue(profile.jasperAvailable());
        assertFalse(profile.canInvokeJasper());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_RUNTIME_PROFILE_MISMATCH")
                        && diagnostic.message().contains("profile=TOKEN_ONLY")
                        && diagnostic.message().contains("servlet=jakarta")
                        && diagnostic.message().contains("jsp=javax")));
    }

    @Test
    void reportsJavaxRuntimeProfileWhenJasperAndJavaxApisArePresent() {
        JasperRuntimeProfile profile = JasperRuntimeProbe.using(Set.of(
                "org.apache.jasper.JspC",
                "javax.servlet.ServletContext",
                "javax.servlet.jsp.JspFactory")::contains).probe();

        assertTrue(profile.jasperAvailable());
        assertTrue(profile.canInvokeJasper());
        assertTrue(profile.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JASPER_RUNTIME_PROFILE")
                        && diagnostic.message().contains("profile=TOMCAT_8_9_JAVAX")
                        && diagnostic.message().contains("servlet=javax")
                        && diagnostic.message().contains("jsp=javax")));
    }

    @Test
    void defaultsFallBackToAnalyzerLoaderWhenThreadContextLoaderCannotSeeJasper() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolatedContextLoader = URLClassLoader.newInstance(
                new URL[0],
                ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(isolatedContextLoader);

            JasperRuntimeProfile profile = JasperRuntimeProbe.defaults().probe();

            assertTrue(profile.jasperAvailable());
            assertTrue(profile.canInvokeJasper());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
