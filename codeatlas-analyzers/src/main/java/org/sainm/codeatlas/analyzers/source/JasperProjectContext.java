package org.sainm.codeatlas.analyzers.source;

record JasperProjectContext(String servletApiNamespace) {
    JasperProjectContext {
        servletApiNamespace = servletApiNamespace == null ? "" : servletApiNamespace.trim().toLowerCase();
    }

    static JasperProjectContext jakarta() {
        return new JasperProjectContext("jakarta");
    }

    static JasperProjectContext javax() {
        return new JasperProjectContext("javax");
    }

    static JasperProjectContext from(WebAppContext context) {
        if (context == null) {
            return new JasperProjectContext("");
        }
        int major = majorVersion(context.servletVersion());
        if (major >= 5) {
            return jakarta();
        }
        if (major > 0) {
            return javax();
        }
        return new JasperProjectContext("");
    }

    String preferredProfile() {
        return switch (servletApiNamespace) {
            case "jakarta" -> "TOMCAT_10_JAKARTA";
            case "javax" -> "TOMCAT_8_9_JAVAX";
            default -> "TOKEN_ONLY";
        };
    }

    private static int majorVersion(String version) {
        String value = version == null ? "" : version.trim();
        int dot = value.indexOf('.');
        String major = dot < 0 ? value : value.substring(0, dot);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
