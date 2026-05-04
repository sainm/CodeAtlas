package org.sainm.codeatlas.analyzers.source;

import java.util.List;

record JasperRuntimeProfile(
        boolean jasperAvailable,
        boolean jakartaServletAvailable,
        boolean javaxServletAvailable,
        boolean jakartaJspAvailable,
        boolean javaxJspAvailable,
        JasperRuntimeClassResolver classResolver,
        List<JavaAnalysisDiagnostic> diagnostics) {
    JasperRuntimeProfile {
        if (classResolver == null) {
            classResolver = new JasperRuntimeClassResolver() {
                @Override
                public boolean isAvailable(String className) {
                    return false;
                }

                @Override
                public Class<?> loadClass(String className) throws ClassNotFoundException {
                    throw new ClassNotFoundException(className);
                }
            };
        }
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    boolean canInvokeJasper() {
        return !jasperProfile().equals("TOKEN_ONLY");
    }

    String jasperProfile() {
        if (!jasperAvailable) {
            return "TOKEN_ONLY";
        }
        String servlet = servletNamespace();
        String jsp = jspNamespace();
        if (servlet.equals("jakarta") && jsp.equals("jakarta")) {
            return "TOMCAT_10_JAKARTA";
        }
        if (servlet.equals("javax") && jsp.equals("javax")) {
            return "TOMCAT_8_9_JAVAX";
        }
        if (servlet.equals("mixed") && jsp.equals("mixed")) {
            return "VENDOR_COMPAT";
        }
        return "TOKEN_ONLY";
    }

    String servletNamespace() {
        return namespace(jakartaServletAvailable, javaxServletAvailable);
    }

    String jspNamespace() {
        return namespace(jakartaJspAvailable, javaxJspAvailable);
    }

    Class<?> loadJspcClass() throws ClassNotFoundException {
        return classResolver.loadClass(JasperRuntimeProbe.JASPER_JSPC_CLASS);
    }

    private static String namespace(boolean jakartaAvailable, boolean javaxAvailable) {
        if (jakartaAvailable && javaxAvailable) {
            return "mixed";
        }
        if (jakartaAvailable) {
            return "jakarta";
        }
        if (javaxAvailable) {
            return "javax";
        }
        return "missing";
    }
}
