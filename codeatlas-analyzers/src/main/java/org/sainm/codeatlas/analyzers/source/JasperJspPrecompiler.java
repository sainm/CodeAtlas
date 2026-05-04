package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class JasperJspPrecompiler {
    private static final String JASPER_JSPC_CLASS = "org.apache.jasper.JspC";

    private JasperJspPrecompiler() {
    }

    static JasperJspPrecompiler defaults() {
        return new JasperJspPrecompiler();
    }

    JspParseAttempt precompile(Path webRoot, List<Path> jspFiles) {
        Class<?> jspcClass;
        try {
            jspcClass = Class.forName(JASPER_JSPC_CLASS);
        } catch (ClassNotFoundException | LinkageError exception) {
            return new JspParseAttempt(
                    JspParserMode.TOKEN_FALLBACK,
                    List.of(new JavaAnalysisDiagnostic(
                            "JASPER_UNAVAILABLE_TOKEN_FALLBACK",
                            JASPER_JSPC_CLASS
                                    + " is not available or cannot be linked; check javax/jakarta JSP classpath: "
                                    + rootCauseMessage(exception))));
        }
        Path outputDir = null;
        try {
            Object jspc = jspcClass.getConstructor().newInstance();
            invokeIfPresent(jspcClass, jspc, "setUriroot", String.class, webRoot.toAbsolutePath().normalize().toString());
            invokeIfPresent(jspcClass, jspc, "setJspFiles", String.class, jspFilesArgument(webRoot, jspFiles));
            outputDir = Files.createTempDirectory("codeatlas-jasper-");
            invokeIfPresent(jspcClass, jspc, "setOutputDir", String.class, outputDir.toString());
            invokeIfPresent(jspcClass, jspc, "setCompile", boolean.class, false);
            invokeIfPresent(jspcClass, jspc, "setValidateXml", boolean.class, false);
            jspcClass.getMethod("execute").invoke(jspc);
            return new JspParseAttempt(
                    JspParserMode.JASPER,
                    List.of(new JavaAnalysisDiagnostic(
                            "JASPER_SEMANTIC_PARSE_USED",
                            "Jasper parsed JSP files before tolerant fact extraction")));
        } catch (IOException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException
                | NoSuchMethodException
                | LinkageError exception) {
            return new JspParseAttempt(
                    JspParserMode.TOKEN_FALLBACK,
                    List.of(new JavaAnalysisDiagnostic(
                            "JASPER_INVOKE_FAILED_TOKEN_FALLBACK",
                            "Jasper invocation failed; using tolerant token fallback: " + rootCauseMessage(exception))));
        } finally {
            deleteRecursivelyQuietly(outputDir);
        }
    }

    private static void invokeIfPresent(
            Class<?> type,
            Object target,
            String name,
            Class<?> parameterType,
            Object value) throws InvocationTargetException, IllegalAccessException {
        try {
            Method method = type.getMethod(name, parameterType);
            method.invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            // Jasper versions vary; optional setup methods should not block fallback extraction.
        }
    }

    private static String jspFilesArgument(Path webRoot, List<Path> jspFiles) {
        return jspFiles.stream()
                .map(jspFile -> webRoot.toAbsolutePath().normalize()
                        .relativize(jspFile.toAbsolutePath().normalize())
                        .toString()
                        .replace('\\', '/'))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null) {
            cursor = invocationTargetException.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getName() : message;
    }

    private static void deleteRecursivelyQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ignored) {
                            // Best-effort cleanup only; parser results do not depend on temporary output.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
