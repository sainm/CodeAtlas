package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class EntryPointIds {
    static final String SOURCE_ROOT = "_entrypoints";

    private EntryPointIds() {
    }

    static List<String> withEntryPointRoot(List<String> sourceRoots) {
        List<String> result = new ArrayList<>(sourceRoots == null ? List.of() : sourceRoots);
        if (!result.contains(SOURCE_ROOT)) {
            result.add(SOURCE_ROOT);
        }
        return List.copyOf(result);
    }

    static String spring(JavaSourceFactContext context, String httpMethod, String path) {
        return id(context, "spring/" + normalizeHttpMethod(httpMethod) + "/" + routePath(path));
    }

    static String struts(StrutsConfigFactContext context, String actionPath) {
        return "entrypoint://" + context.projectId() + "/" + context.moduleKey() + "/"
                + SOURCE_ROOT + "/struts/" + routePath(actionPath);
    }

    static String jsp(JavaSourceFactContext context, String jspPath) {
        return id(context, "jsp/" + localPath(jspPath));
    }

    static String html(JavaSourceFactContext context, String htmlPath) {
        return id(context, "html/" + localPath(htmlPath));
    }

    static String clientRequest(JavaSourceFactContext context, String httpMethod, String path) {
        return id(context, "client-http/" + normalizeHttpMethod(httpMethod) + "/" + routePath(path));
    }

    static String main(JavaSourceFactContext context, String ownerQualifiedName) {
        return id(context, "main/" + localPath(ownerQualifiedName) + "/main");
    }

    static String scheduler(JavaSourceFactContext context, String ownerQualifiedName, String methodName) {
        return id(context, "scheduler/" + localPath(ownerQualifiedName) + "/" + localPath(methodName));
    }

    static String messageListener(
            JavaSourceFactContext context,
            String ownerQualifiedName,
            String methodName,
            String signature) {
        return id(context, "message/" + localPath(ownerQualifiedName) + "/" + localPath(methodName)
                + "/" + signaturePath(signature));
    }

    private static String id(JavaSourceFactContext context, String ownerPath) {
        return "entrypoint://" + context.projectId() + "/" + context.moduleKey() + "/"
                + SOURCE_ROOT + "/" + ownerPath;
    }

    private static String normalizeHttpMethod(String httpMethod) {
        return httpMethod == null || httpMethod.isBlank()
                ? "GET"
                : httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    private static String routePath(String path) {
        String normalized = stripQueryAndFragment(path).replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "_root" : normalizeSegments(normalized);
    }

    private static String localPath(String path) {
        String normalized = stripQueryAndFragment(path).replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? "_root" : normalizeSegments(normalized);
    }

    private static String signaturePath(String signature) {
        return localPath(signature == null ? "" : signature.replace('/', '.'));
    }

    private static String normalizeSegments(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "_root" : String.join("/", segments);
    }

    private static String stripQueryAndFragment(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        return value.substring(0, end);
    }
}
