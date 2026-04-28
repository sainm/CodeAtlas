package org.sainm.codeatlas.analyzers.spring;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.SpoonSymbolMapper;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public final class SpringMvcAnalyzer {
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    public SpringMvcAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<SpringEndpoint> endpoints = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        for (CtType<?> type : model.getAllTypes()) {
            if (!isController(type)) {
                continue;
            }
            String classPath = requestPath(type.getAnnotations()).orElse("");
            SymbolId controllerClass = symbols.type(type);
            nodes.add(GraphNodeFactory.classNode(controllerClass, NodeRole.CONTROLLER));
            for (CtMethod<?> method : type.getMethods()) {
                mapping(method.getAnnotations()).ifPresent(mapping -> {
                    String fullPath = joinPaths(classPath, mapping.path());
                    SpringEndpoint endpoint = new SpringEndpoint(mapping.httpMethod(), fullPath, type.getQualifiedName(), method.getSimpleName(), line(method.getPosition()));
                    endpoints.add(endpoint);
                    addEndpointFact(scope, projectKey, sourceRootKey, symbols.method(method), endpoint, method.getPosition(), nodes, facts);
                });
            }
        }
        return new SpringMvcAnalysisResult(endpoints, nodes, facts);
    }

    private boolean isController(CtType<?> type) {
        return type.getAnnotations().stream()
            .map(annotation -> annotation.getAnnotationType().getSimpleName())
            .anyMatch(name -> name.equals("Controller") || name.equals("RestController"));
    }

    private Optional<String> requestPath(List<CtAnnotation<?>> annotations) {
        return annotations.stream()
            .filter(annotation -> annotation.getAnnotationType().getSimpleName().equals("RequestMapping"))
            .findFirst()
            .map(this::pathFromAnnotation)
            .filter(path -> !path.isBlank());
    }

    private Optional<EndpointMapping> mapping(List<CtAnnotation<?>> annotations) {
        for (CtAnnotation<?> annotation : annotations) {
            String name = annotation.getAnnotationType().getSimpleName();
            String httpMethod = switch (name) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "DeleteMapping" -> "DELETE";
                case "PatchMapping" -> "PATCH";
                case "RequestMapping" -> methodFromRequestMapping(annotation.toString());
                default -> null;
            };
            if (httpMethod != null) {
                return Optional.of(new EndpointMapping(httpMethod, pathFromAnnotation(annotation)));
            }
        }
        return Optional.empty();
    }

    private String pathFromAnnotation(CtAnnotation<?> annotation) {
        Matcher matcher = STRING_LITERAL.matcher(annotation.toString());
        return matcher.find() ? matcher.group(1) : "/";
    }

    private String methodFromRequestMapping(String annotationText) {
        String upper = annotationText.toUpperCase(Locale.ROOT);
        for (String method : List.of("GET", "POST", "PUT", "DELETE", "PATCH")) {
            if (upper.contains("REQUESTMETHOD." + method) || upper.contains("METHOD = " + method)) {
                return method;
            }
        }
        return "ANY";
    }

    private void addEndpointFact(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        SymbolId methodSymbol,
        SpringEndpoint endpoint,
        SourcePosition position,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId endpointSymbol = SymbolId.logicalPath(
            SymbolKind.API_ENDPOINT,
            projectKey,
            scope.moduleKey(),
            sourceRootKey,
            endpoint.path(),
            endpoint.httpMethod()
        );
        nodes.add(GraphNodeFactory.apiEndpointNode(endpointSymbol));
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.SPRING_HANDLER));
        facts.add(GraphFact.active(
            new FactKey(endpointSymbol, RelationType.ROUTES_TO, methodSymbol, endpoint.httpMethod()),
            evidence(position, endpoint),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.CERTAIN,
            SourceType.SPOON
        ));
    }

    private EvidenceKey evidence(SourcePosition position, SpringEndpoint endpoint) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "spring-mvc", "_unknown", 0, 0, endpoint.httpMethod() + ":" + endpoint.path());
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "spring-mvc",
            position.getFile().toPath().toString(),
            line(position),
            line(position),
            endpoint.httpMethod() + ":" + endpoint.path()
        );
    }

    private String joinPaths(String left, String right) {
        String normalizedLeft = left == null || left.isBlank() || left.equals("/") ? "" : left;
        String normalizedRight = right == null || right.isBlank() || right.equals("/") ? "" : right;
        String joined = normalizedLeft + "/" + (normalizedRight.startsWith("/") ? normalizedRight.substring(1) : normalizedRight);
        return joined.equals("/") || joined.isBlank() ? "/" : joined.replaceAll("/{2,}", "/");
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }

    private record EndpointMapping(String httpMethod, String path) {
    }
}
