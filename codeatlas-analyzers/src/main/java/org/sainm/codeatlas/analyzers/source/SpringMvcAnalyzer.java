package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class SpringMvcAnalyzer {
    private static final List<String> ALL_HTTP_METHODS = List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "TRACE");

    private SpringMvcAnalyzer() {
    }

    public static SpringMvcAnalyzer defaults() {
        return new SpringMvcAnalyzer();
    }

    public SpringMvcAnalysisResult analyze(Path sourceRoot, List<Path> sourceFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new SpringMvcAnalysisResult(false, List.of(), List.of(), List.of(), List.of());
        }
        try {
            return extract(sourceRoot, buildModel(sourceFiles, false), false, List.of());
        } catch (RuntimeException exception) {
            List<JavaAnalysisDiagnostic> diagnostics = List.of(new JavaAnalysisDiagnostic(
                    "NO_CLASSPATH_FALLBACK",
                    exception.getMessage()));
            return extract(sourceRoot, buildModel(sourceFiles, true), true, diagnostics);
        }
    }

    private static CtModel buildModel(List<Path> sourceFiles, boolean noClasspath) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(noClasspath);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        for (Path sourceFile : sourceFiles) {
            launcher.addInputResource(sourceFile.toString());
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    private static SpringMvcAnalysisResult extract(
            Path sourceRoot,
            CtModel model,
            boolean noClasspathFallbackUsed,
            List<JavaAnalysisDiagnostic> diagnostics) {
        List<SpringComponentInfo> components = new ArrayList<>();
        List<SpringRouteInfo> routes = new ArrayList<>();
        List<SpringInjectionInfo> injections = new ArrayList<>();
        for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
            if (type.isShadow()) {
                continue;
            }
            SpringComponentKind componentKind = componentKind(type.getAnnotations());
            if (componentKind != null) {
                components.add(new SpringComponentInfo(
                        type.getQualifiedName(),
                        componentKind,
                        annotationNames(type.getAnnotations()),
                        location(sourceRoot, type.getPosition())));
            }
            addFieldInjections(sourceRoot, type, injections);
            addMethodInjections(sourceRoot, type, injections);
            addRoutes(sourceRoot, type, routes);
        }
        addConstructorInjections(sourceRoot, model, injections);
        return new SpringMvcAnalysisResult(
                noClasspathFallbackUsed,
                sortedComponents(components),
                sortedRoutes(routes),
                sortedInjections(injections),
                diagnostics);
    }

    private static void addRoutes(Path sourceRoot, CtType<?> type, List<SpringRouteInfo> routes) {
        if (!isSpringController(type.getAnnotations())) {
            return;
        }
        List<String> classPaths = mappingPaths(type.getAnnotations());
        List<String> classMethods = mappingHttpMethods(type.getAnnotations());
        for (CtMethod<?> method : type.getMethods()) {
            Mapping methodMapping = methodMapping(method.getAnnotations());
            if (methodMapping == null) {
                continue;
            }
            List<String> httpMethods = effectiveHttpMethods(classMethods, methodMapping.httpMethods());
            if (httpMethods.isEmpty()) {
                continue;
            }
            routes.add(new SpringRouteInfo(
                    type.getQualifiedName(),
                    method.getSimpleName(),
                    JavaDescriptor.methodDescriptor(method.getParameters().stream()
                            .map(CtParameter::getType)
                            .toList(), method.getType()),
                    httpMethods,
                    combinePaths(classPaths, methodMapping.paths()),
                    location(sourceRoot, method.getPosition())));
        }
    }

    private static void addFieldInjections(Path sourceRoot, CtType<?> type, List<SpringInjectionInfo> injections) {
        for (CtField<?> field : type.getFields()) {
            if (isInjectionAnnotationPresent(field.getAnnotations())) {
                injections.add(new SpringInjectionInfo(
                        type.getQualifiedName(),
                        field.getSimpleName(),
                        typeName(field.getType()),
                        SpringInjectionKind.FIELD,
                        location(sourceRoot, field.getPosition())));
            }
        }
    }

    private static void addConstructorInjections(Path sourceRoot, CtModel model, List<SpringInjectionInfo> injections) {
        List<CtConstructor<?>> constructors = model.getElements(new TypeFilter<>(CtConstructor.class));
        Map<String, Long> constructorCountByType = constructors.stream()
                .map(constructor -> constructor.getParent(CtType.class))
                .filter(type -> type != null)
                .collect(Collectors.groupingBy(CtType::getQualifiedName, Collectors.counting()));
        for (CtConstructor<?> constructor : constructors) {
            CtType<?> type = constructor.getParent(CtType.class);
            boolean hasInjectionAnnotation = isInjectionAnnotationPresent(constructor.getAnnotations());
            boolean isSingleConstructor = type != null
                    && constructorCountByType.getOrDefault(type.getQualifiedName(), 0L) == 1L;
            if (type == null || type.isShadow() || componentKind(type.getAnnotations()) == null
                    || (!hasInjectionAnnotation && (!isSingleConstructor || constructor.getParameters().isEmpty()))) {
                continue;
            }
            for (CtParameter<?> parameter : constructor.getParameters()) {
                injections.add(new SpringInjectionInfo(
                        type.getQualifiedName(),
                        "<init>",
                        typeName(parameter.getType()),
                        SpringInjectionKind.CONSTRUCTOR,
                        location(sourceRoot, constructor.getPosition())));
            }
        }
    }

    private static void addMethodInjections(Path sourceRoot, CtType<?> type, List<SpringInjectionInfo> injections) {
        for (CtMethod<?> method : type.getMethods()) {
            if (!isInjectionAnnotationPresent(method.getAnnotations())) {
                continue;
            }
            for (CtParameter<?> parameter : method.getParameters()) {
                injections.add(new SpringInjectionInfo(
                        type.getQualifiedName(),
                        method.getSimpleName(),
                        typeName(parameter.getType()),
                        SpringInjectionKind.METHOD,
                        location(sourceRoot, method.getPosition())));
            }
        }
    }

    private static SpringComponentKind componentKind(List<CtAnnotation<?>> annotations) {
        if (hasAnnotation(annotations, "RestController")) {
            return SpringComponentKind.REST_CONTROLLER;
        }
        if (hasAnnotation(annotations, "Controller")) {
            return SpringComponentKind.CONTROLLER;
        }
        if (hasAnnotation(annotations, "Service")) {
            return SpringComponentKind.SERVICE;
        }
        if (hasAnnotation(annotations, "Component")) {
            return SpringComponentKind.COMPONENT;
        }
        return null;
    }

    private static boolean isSpringController(List<CtAnnotation<?>> annotations) {
        return hasAnnotation(annotations, "Controller") || hasAnnotation(annotations, "RestController");
    }

    private static List<String> mappingPaths(List<CtAnnotation<?>> annotations) {
        for (CtAnnotation<?> annotation : annotations) {
            String name = simpleAnnotationName(annotation);
            if (name.equals("RequestMapping")) {
                List<String> paths = annotationValues(annotation, "value", "path");
                return paths.isEmpty() ? List.of("") : paths;
            }
        }
        return List.of("");
    }

    private static Mapping methodMapping(List<CtAnnotation<?>> annotations) {
        for (CtAnnotation<?> annotation : annotations) {
            String name = simpleAnnotationName(annotation);
            List<String> paths = annotationValues(annotation, "value", "path");
            paths = paths.isEmpty() ? List.of("") : paths;
            switch (name) {
                case "GetMapping" -> {
                    return new Mapping(List.of("GET"), paths);
                }
                case "PostMapping" -> {
                    return new Mapping(List.of("POST"), paths);
                }
                case "PutMapping" -> {
                    return new Mapping(List.of("PUT"), paths);
                }
                case "DeleteMapping" -> {
                    return new Mapping(List.of("DELETE"), paths);
                }
                case "PatchMapping" -> {
                    return new Mapping(List.of("PATCH"), paths);
                }
                case "RequestMapping" -> {
                    return new Mapping(requestMethods(annotation), paths);
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static List<String> mappingHttpMethods(List<CtAnnotation<?>> annotations) {
        for (CtAnnotation<?> annotation : annotations) {
            if (simpleAnnotationName(annotation).equals("RequestMapping")) {
                return requestMethods(annotation);
            }
        }
        return List.of();
    }

    private static List<String> effectiveHttpMethods(List<String> classMethods, List<String> methodMethods) {
        if (classMethods.isEmpty()) {
            return methodMethods.isEmpty() ? ALL_HTTP_METHODS : methodMethods;
        }
        if (methodMethods.isEmpty()) {
            return classMethods;
        }
        List<String> result = new ArrayList<>(classMethods);
        result.addAll(methodMethods);
        return result.stream()
                .distinct()
                .toList();
    }

    private static List<String> requestMethods(CtAnnotation<?> annotation) {
        return annotationValues(annotation, "method").stream()
                .map(value -> {
                    int dot = value.lastIndexOf('.');
                    String method = dot >= 0 ? value.substring(dot + 1) : value;
                    return stripQuotes(method).toUpperCase(Locale.ROOT);
                })
                .toList();
    }

    private static List<String> combinePaths(List<String> classPaths, List<String> methodPaths) {
        List<String> result = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                result.add(joinPath(classPath, methodPath));
            }
        }
        return result;
    }

    private static String joinPath(String left, String right) {
        String normalizedLeft = stripQuotes(left == null ? "" : left);
        String normalizedRight = stripQuotes(right == null ? "" : right);
        if (normalizedLeft.isBlank()) {
            return normalizedRight.isBlank() ? "/" : ensureLeadingSlash(normalizedRight);
        }
        if (normalizedRight.isBlank()) {
            return ensureLeadingSlash(normalizedLeft);
        }
        String prefix = ensureLeadingSlash(normalizedLeft);
        String suffix = normalizedRight.startsWith("/") ? normalizedRight : "/" + normalizedRight;
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) + suffix : prefix + suffix;
    }

    private static String ensureLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isInjectionAnnotationPresent(List<CtAnnotation<?>> annotations) {
        return hasAnnotation(annotations, "Autowired")
                || hasAnnotation(annotations, "Inject")
                || hasAnnotation(annotations, "Resource");
    }

    private static boolean hasAnnotation(List<CtAnnotation<?>> annotations, String simpleName) {
        return annotations.stream().anyMatch(annotation -> simpleAnnotationName(annotation).equals(simpleName));
    }

    private static List<String> annotationNames(List<CtAnnotation<?>> annotations) {
        return annotations.stream()
                .map(annotation -> annotation.getAnnotationType().getQualifiedName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static String simpleAnnotationName(CtAnnotation<?> annotation) {
        String simpleName = annotation.getAnnotationType().getSimpleName();
        if (simpleName != null && !simpleName.isBlank()) {
            return simpleName;
        }
        String qualifiedName = annotation.getAnnotationType().getQualifiedName();
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    private static List<String> annotationValues(CtAnnotation<?> annotation, String... keys) {
        Map<String, CtExpression> values = annotation.getValues();
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            CtExpression expression = values.get(key);
            if (expression != null) {
                result.addAll(splitAnnotationExpression(expression.toString()));
            }
        }
        return result.stream().map(SpringMvcAnalyzer::stripQuotes).filter(value -> !value.isBlank()).toList();
    }

    private static List<String> splitAnnotationExpression(String expression) {
        String value = expression == null ? "" : expression.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder item = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quote != 0) {
                item.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                item.append(current);
                continue;
            }
            if (current == ',') {
                addAnnotationExpressionPart(result, item);
                item.setLength(0);
                continue;
            }
            item.append(current);
        }
        addAnnotationExpressionPart(result, item);
        return result;
    }

    private static void addAnnotationExpressionPart(List<String> result, StringBuilder item) {
        String value = item.toString().trim();
        if (!value.isBlank()) {
            result.add(value);
        }
    }

    private static String stripQuotes(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'"))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static String typeName(CtTypeReference<?> type) {
        return type == null ? "" : type.getQualifiedName();
    }

    private static SourceLocation location(Path sourceRoot, SourcePosition position) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new SourceLocation("", 0, 0);
        }
        Path file = position.getFile().toPath();
        String relativePath = sourceRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
        return new SourceLocation(relativePath, position.getLine(), position.getColumn());
    }

    private static List<SpringComponentInfo> sortedComponents(List<SpringComponentInfo> components) {
        return components.stream()
                .sorted(Comparator.comparing(SpringComponentInfo::qualifiedName))
                .toList();
    }

    private static List<SpringRouteInfo> sortedRoutes(List<SpringRouteInfo> routes) {
        return routes.stream()
                .sorted(Comparator.comparing(SpringRouteInfo::ownerQualifiedName)
                        .thenComparing(SpringRouteInfo::methodName)
                        .thenComparing(SpringRouteInfo::methodSignature))
                .toList();
    }

    private static List<SpringInjectionInfo> sortedInjections(List<SpringInjectionInfo> injections) {
        return injections.stream()
                .sorted(Comparator.comparing(SpringInjectionInfo::ownerQualifiedName)
                        .thenComparing(SpringInjectionInfo::memberName)
                        .thenComparing(SpringInjectionInfo::targetTypeName))
                .toList();
    }

    private record Mapping(List<String> httpMethods, List<String> paths) {
    }
}
