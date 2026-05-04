package org.apache.jasper;

public final class JspC {
    public static int executeCalls;
    public static String uriroot;
    public static String jspFiles;
    public static String outputDir;
    public static boolean compile = true;
    public static boolean validateXml = true;
    public static Throwable executionFailure;

    public JspC() {
    }

    public static void reset() {
        executeCalls = 0;
        uriroot = "";
        jspFiles = "";
        outputDir = "";
        compile = true;
        validateXml = true;
        executionFailure = null;
    }

    public void setUriroot(String uriroot) {
        JspC.uriroot = uriroot;
    }

    public void setJspFiles(String jspFiles) {
        JspC.jspFiles = jspFiles;
    }

    public void setOutputDir(String outputDir) {
        JspC.outputDir = outputDir;
    }

    public void setCompile(boolean compile) {
        JspC.compile = compile;
    }

    public void setValidateXml(boolean validateXml) {
        JspC.validateXml = validateXml;
    }

    public void execute() {
        if (executionFailure != null) {
            if (executionFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (executionFailure instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(executionFailure);
        }
        executeCalls++;
    }
}
