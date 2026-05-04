package org.sainm.codeatlas.analyzers.source;

import java.util.List;
import java.util.function.Predicate;

final class JasperRuntimeProbe {
    static final String JASPER_JSPC_CLASS = "org.apache.jasper.JspC";
    private static final String JAKARTA_SERVLET_CONTEXT_CLASS = "jakarta.servlet.ServletContext";
    private static final String JAVAX_SERVLET_CONTEXT_CLASS = "javax.servlet.ServletContext";
    private static final String JAKARTA_JSP_FACTORY_CLASS = "jakarta.servlet.jsp.JspFactory";
    private static final String JAVAX_JSP_FACTORY_CLASS = "javax.servlet.jsp.JspFactory";

    private final Predicate<String> classAvailable;

    private JasperRuntimeProbe(Predicate<String> classAvailable) {
        this.classAvailable = classAvailable;
    }

    static JasperRuntimeProbe defaults() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = JasperRuntimeProbe.class.getClassLoader();
        }
        ClassLoader probeClassLoader = classLoader;
        return using(className -> classAvailable(probeClassLoader, className));
    }

    static JasperRuntimeProbe using(Predicate<String> classAvailable) {
        return new JasperRuntimeProbe(classAvailable == null ? className -> false : classAvailable);
    }

    JasperRuntimeProfile probe() {
        boolean jasperAvailable = classAvailable.test(JASPER_JSPC_CLASS);
        boolean jakartaServletAvailable = classAvailable.test(JAKARTA_SERVLET_CONTEXT_CLASS);
        boolean javaxServletAvailable = classAvailable.test(JAVAX_SERVLET_CONTEXT_CLASS);
        boolean jakartaJspAvailable = classAvailable.test(JAKARTA_JSP_FACTORY_CLASS);
        boolean javaxJspAvailable = classAvailable.test(JAVAX_JSP_FACTORY_CLASS);
        JasperRuntimeProfile profile = new JasperRuntimeProfile(
                jasperAvailable,
                jakartaServletAvailable,
                javaxServletAvailable,
                jakartaJspAvailable,
                javaxJspAvailable,
                List.of());
        return new JasperRuntimeProfile(
                jasperAvailable,
                jakartaServletAvailable,
                javaxServletAvailable,
                jakartaJspAvailable,
                javaxJspAvailable,
                List.of(runtimeDiagnostic(profile)));
    }

    private static JavaAnalysisDiagnostic runtimeDiagnostic(JasperRuntimeProfile profile) {
        String message = "Jasper runtime profile: jspc=" + availability(profile.jasperAvailable())
                + ", profile=" + profile.jasperProfile()
                + ", servlet=" + profile.servletNamespace()
                + ", jsp=" + profile.jspNamespace();
        if (profile.jasperAvailable() && !profile.canInvokeJasper()) {
            return new JavaAnalysisDiagnostic("JASPER_RUNTIME_PROFILE_MISMATCH", message);
        }
        return new JavaAnalysisDiagnostic("JASPER_RUNTIME_PROFILE", message);
    }

    private static String availability(boolean available) {
        return available ? "available" : "missing";
    }

    private static boolean classAvailable(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }
}
