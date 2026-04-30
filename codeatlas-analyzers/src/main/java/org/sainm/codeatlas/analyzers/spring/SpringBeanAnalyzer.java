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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class SpringBeanAnalyzer {
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Set<String> STEREOTYPES = Set.of(
        "Controller",
        "RestController",
        "Service",
        "Repository",
        "Component"
    );
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
        "Autowired",
        "Resource",
        "Inject"
    );

    public SpringBeanAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        Map<String, NodeRole> beanRoles = new HashMap<>();
        List<SpringBeanDependency> dependencies = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        SpringBeanAnalysisContext context = new SpringBeanAnalysisContext(scope, symbols, beanRoles, dependencies, nodes, facts);

        for (CtType<?> type : model.getAllTypes()) {
            beanRole(type).ifPresent(role -> {
                context.beanRoles().put(type.getQualifiedName(), role);
                context.nodes().add(GraphNodeFactory.classNode(context.symbols().type(type), role));
            });
        }

        for (CtType<?> type : model.getAllTypes()) {
            NodeRole sourceRole = context.beanRoles().get(type.getQualifiedName());
            if (sourceRole == null) {
                continue;
            }
            SymbolId sourceClass = context.symbols().type(type);
            for (CtField<?> field : type.getFields()) {
                if (hasInjectionAnnotation(field.getAnnotations())) {
                    addDependency(
                        context,
                        sourceClass,
                        type.getQualifiedName(),
                        field.getType(),
                        "field:" + field.getSimpleName(),
                        qualifier(field.getAnnotations()).orElse(""),
                        Confidence.CERTAIN,
                        field.getPosition()
                    );
                }
            }

            @SuppressWarnings("unchecked")
            List<CtConstructor<?>> constructors = (List<CtConstructor<?>>) (List<?>) type.getElements(new TypeFilter<>(CtConstructor.class)).stream()
                .filter(constructor -> constructor.getDeclaringType() != null)
                .filter(constructor -> constructor.getDeclaringType().getQualifiedName().equals(type.getQualifiedName()))
                .toList();
            for (CtConstructor<?> constructor : constructors) {
                boolean annotated = hasInjectionAnnotation(constructor.getAnnotations());
                boolean implicitSingleConstructor = !annotated && constructors.size() == 1 && !constructor.getParameters().isEmpty();
                if (!annotated && !implicitSingleConstructor) {
                    continue;
                }
                Confidence confidence = annotated ? Confidence.CERTAIN : Confidence.LIKELY;
                for (CtParameter<?> parameter : constructor.getParameters()) {
                    String parameterQualifier = qualifier(parameter.getAnnotations())
                        .or(() -> qualifier(constructor.getAnnotations()))
                        .orElse("");
                    addDependency(
                        context,
                        sourceClass,
                        type.getQualifiedName(),
                        parameter.getType(),
                        "constructor:" + parameter.getSimpleName(),
                        parameterQualifier,
                        confidence,
                        parameter.getPosition()
                    );
                }
            }
        }

        return new SpringBeanAnalysisResult(dependencies, nodes, facts);
    }

    private Optional<NodeRole> beanRole(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String name = annotationName(annotation);
            if (!STEREOTYPES.contains(name)) {
                continue;
            }
            return Optional.of(switch (name) {
                case "Controller", "RestController" -> NodeRole.CONTROLLER;
                case "Service" -> NodeRole.SERVICE;
                case "Repository" -> NodeRole.DAO;
                default -> NodeRole.CODE_TYPE;
            });
        }
        return Optional.empty();
    }

    private void addDependency(
        SpringBeanAnalysisContext context,
        SymbolId sourceClass,
        String sourceQualifiedName,
        CtTypeReference<?> dependencyType,
        String injectionPoint,
        String qualifier,
        Confidence confidence,
        SourcePosition position
    ) {
        if (dependencyType == null || dependencyType.getQualifiedName() == null || dependencyType.getQualifiedName().isBlank()) {
            return;
        }
        SymbolId targetClass = context.symbols().typeReference(dependencyType);
        NodeRole targetRole = context.beanRoles().getOrDefault(dependencyType.getQualifiedName(), NodeRole.CODE_TYPE);
        context.nodes().add(GraphNodeFactory.classNode(targetClass, targetRole));
        context.dependencies().add(new SpringBeanDependency(
            sourceQualifiedName,
            dependencyType.getQualifiedName(),
            injectionPoint,
            qualifier,
            confidence,
            line(position)
        ));
        context.facts().add(GraphFact.active(
            new FactKey(sourceClass, RelationType.INJECTS, targetClass, qualifierForFact(injectionPoint, qualifier)),
            evidence(position, injectionPoint, qualifier),
            context.scope().projectId(),
            context.scope().snapshotId(),
            context.scope().analysisRunId(),
            context.scope().scopeKey(),
            confidence,
            SourceType.SPOON
        ));
    }

    private boolean hasInjectionAnnotation(List<CtAnnotation<?>> annotations) {
        return annotations.stream()
            .map(this::annotationName)
            .anyMatch(INJECTION_ANNOTATIONS::contains);
    }

    private Optional<String> qualifier(List<CtAnnotation<?>> annotations) {
        for (CtAnnotation<?> annotation : annotations) {
            String name = annotationName(annotation);
            if (name.equals("Qualifier") || name.equals("Resource") || name.equals("Named")) {
                Matcher matcher = STRING_LITERAL.matcher(annotation.toString());
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
        }
        return Optional.empty();
    }

    private EvidenceKey evidence(SourcePosition position, String injectionPoint, String qualifier) {
        String localPath = qualifier == null || qualifier.isBlank() ? injectionPoint : injectionPoint + ":" + qualifier;
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "spring-bean", "_unknown", 0, 0, localPath);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "spring-bean",
            position.getFile().toPath().toString(),
            line(position),
            line(position),
            localPath
        );
    }

    private String qualifierForFact(String injectionPoint, String qualifier) {
        return qualifier == null || qualifier.isBlank() ? injectionPoint : injectionPoint + "|" + qualifier;
    }

    private String annotationName(CtAnnotation<?> annotation) {
        if (annotation.getAnnotationType() == null) {
            return "";
        }
        return annotation.getAnnotationType().getSimpleName();
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }

    private record SpringBeanAnalysisContext(
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        Map<String, NodeRole> beanRoles,
        List<SpringBeanDependency> dependencies,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
    }
}
