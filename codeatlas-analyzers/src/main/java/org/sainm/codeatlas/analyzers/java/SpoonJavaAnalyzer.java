package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
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
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import spoon.reflect.code.CtLambda;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class SpoonJavaAnalyzer {
    private final int complianceLevel;
    private final boolean noClasspath;

    public SpoonJavaAnalyzer() {
        this(25, true);
    }

    public SpoonJavaAnalyzer(int complianceLevel, boolean noClasspath) {
        this.complianceLevel = complianceLevel;
        this.noClasspath = noClasspath;
    }

    public JavaAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(complianceLevel);
        launcher.getEnvironment().setNoClasspath(noClasspath);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        Map<String, NodeRole> rolesByType = rolesByType(model.getAllTypes());

        JavaAnalysisContext context = new JavaAnalysisContext(scope, symbols, nodes, facts, rolesByType);
        for (CtType<?> type : model.getAllTypes()) {
            analyzeType(context, type);
        }
        addMethodImplementationFacts(context, model.getAllTypes());

        return new JavaAnalysisResult(nodes, facts);
    }

    private void analyzeType(
        JavaAnalysisContext context,
        CtType<?> type
    ) {
        SymbolId typeSymbol = context.symbols().type(type);
        context.nodes().add(GraphNodeFactory.classNode(typeSymbol, roleForType(type, context.rolesByType())));
        addFileDeclaration(context, typeSymbol, type.getPosition());

        CtTypeReference<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addFact(context, typeSymbol, RelationType.EXTENDS, context.symbols().typeReference(superClass), type.getPosition(), "extends", Confidence.LIKELY);
        }
        for (CtTypeReference<?> implementedType : type.getSuperInterfaces()) {
            addFact(context, typeSymbol, RelationType.IMPLEMENTS, context.symbols().typeReference(implementedType), type.getPosition(), "implements", Confidence.LIKELY);
        }

        for (CtField<?> field : type.getFields()) {
            SymbolId fieldSymbol = context.symbols().field(field);
            context.nodes().add(GraphNodeFactory.fieldNode(fieldSymbol, NodeRole.CODE_MEMBER));
            addFact(context, typeSymbol, RelationType.DECLARES, fieldSymbol, field.getPosition(), "field", Confidence.CERTAIN);
            addLayerDependency(context, type, typeSymbol, field);
        }
        for (CtAnonymousExecutable initializer : type.getElements(new TypeFilter<>(CtAnonymousExecutable.class))) {
            if (!type.equals(initializer.getDeclaringType())) {
                continue;
            }
            SymbolId initializerSymbol = context.symbols().initializer(initializer);
            context.nodes().add(GraphNodeFactory.methodNode(initializerSymbol, NodeRole.CODE_MEMBER));
            addFact(context, typeSymbol, RelationType.DECLARES, initializerSymbol, initializer.getPosition(), initializer.isStatic() ? "static-initializer" : "instance-initializer", Confidence.CERTAIN);
            addInvocations(context, initializerSymbol, initializer.getElements(new TypeFilter<>(CtInvocation.class)));
        }
        for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
            if (!type.equals(constructor.getDeclaringType())) {
                continue;
            }
            SymbolId constructorSymbol = context.symbols().constructor(constructor);
            context.nodes().add(GraphNodeFactory.methodNode(constructorSymbol, NodeRole.CODE_MEMBER));
            addFact(context, typeSymbol, RelationType.DECLARES, constructorSymbol, constructor.getPosition(), "constructor", Confidence.CERTAIN);
            addInvocations(context, constructorSymbol, constructor.getElements(new TypeFilter<>(CtInvocation.class)));
        }
        for (CtMethod<?> method : type.getMethods()) {
            SymbolId methodSymbol = context.symbols().method(method);
            context.nodes().add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
            addFact(context, typeSymbol, RelationType.DECLARES, methodSymbol, method.getPosition(), "method", Confidence.CERTAIN);
            addInvocations(context, methodSymbol, method.getElements(new TypeFilter<>(CtInvocation.class)));
            addLambdas(context, typeSymbol, methodSymbol, method.getElements(new TypeFilter<>(CtLambda.class)));
        }
    }

    private void addLayerDependency(
        JavaAnalysisContext context,
        CtType<?> ownerType,
        SymbolId ownerSymbol,
        CtField<?> field
    ) {
        CtTypeReference<?> fieldType = field.getType();
        if (fieldType == null || fieldType.getQualifiedName() == null || fieldType.getQualifiedName().isBlank()) {
            return;
        }
        NodeRole ownerRole = roleForType(ownerType, context.rolesByType());
        NodeRole dependencyRole = roleForTypeReference(fieldType, context.rolesByType());
        if (!isApplicationLayer(ownerRole) || !ApplicationLayerClassifier.isSupportLayer(dependencyRole)) {
            return;
        }
        SymbolId dependencySymbol = context.symbols().typeReference(fieldType);
        context.nodes().add(GraphNodeFactory.classNode(dependencySymbol, dependencyRole));
        addFact(
            context,
            ownerSymbol,
            RelationType.INJECTS,
            dependencySymbol,
            field.getPosition(),
            "field-layer-dependency:" + field.getSimpleName(),
            Confidence.LIKELY
        );
    }

    private boolean isApplicationLayer(NodeRole role) {
        return ApplicationLayerClassifier.isApplicationLayer(role);
    }

    private Map<String, NodeRole> rolesByType(Collection<CtType<?>> types) {
        Map<String, NodeRole> roles = new HashMap<>();
        for (CtType<?> type : types) {
            roles.put(type.getQualifiedName(), inferRoleForType(type));
        }
        boolean changed;
        do {
            changed = false;
            for (CtType<?> type : types) {
                if (roles.get(type.getQualifiedName()) == NodeRole.STRUTS_ACTION) {
                    continue;
                }
                CtTypeReference<?> superClass = type.getSuperclass();
                if (superClass != null && roles.get(superClass.getQualifiedName()) == NodeRole.STRUTS_ACTION) {
                    roles.put(type.getQualifiedName(), NodeRole.STRUTS_ACTION);
                    changed = true;
                }
            }
        } while (changed);
        return roles;
    }

    private NodeRole roleForType(CtType<?> type, Map<String, NodeRole> rolesByType) {
        return rolesByType.getOrDefault(type.getQualifiedName(), inferRoleForType(type));
    }

    private NodeRole inferRoleForType(CtType<?> type) {
        if (isStrutsActionType(type)) {
            return NodeRole.STRUTS_ACTION;
        }
        NodeRole namedRole = ApplicationLayerClassifier.roleForQualifiedName(type.getQualifiedName());
        if (namedRole != NodeRole.CODE_TYPE) {
            return namedRole;
        }
        if (isStatelessStaticSupportType(type)) {
            return NodeRole.UTILITY;
        }
        return NodeRole.CODE_TYPE;
    }

    private boolean isStrutsActionType(CtType<?> type) {
        CtTypeReference<?> superClass = type.getSuperclass();
        return superClass != null && superClass.getQualifiedName() != null && superClass.getQualifiedName().contains("org.apache.struts");
    }


    private void addLambdas(
        JavaAnalysisContext context,
        SymbolId typeSymbol,
        SymbolId enclosingExecutable,
        List<CtLambda<?>> lambdas
    ) {
        for (int i = 0; i < lambdas.size(); i++) {
            CtLambda<?> lambda = lambdas.get(i);
            SymbolId lambdaSymbol = context.symbols().lambda(lambda, i);
            context.nodes().add(GraphNodeFactory.methodNode(lambdaSymbol, NodeRole.CODE_MEMBER));
            addFact(context, typeSymbol, RelationType.DECLARES, lambdaSymbol, lambda.getPosition(), "lambda", Confidence.CERTAIN);
            addFact(context, enclosingExecutable, RelationType.CALLS, lambdaSymbol, lambda.getPosition(), "lambda-expression", Confidence.LIKELY);
            addInvocations(context, lambdaSymbol, lambda.getElements(new TypeFilter<>(CtInvocation.class)));
        }
    }

    private void addMethodImplementationFacts(
        JavaAnalysisContext context,
        Collection<CtType<?>> types
    ) {
        Map<String, CtType<?>> typesByName = new HashMap<>();
        for (CtType<?> type : types) {
            typesByName.put(type.getQualifiedName(), type);
        }
        for (CtType<?> type : types) {
            if (type.isInterface()) {
                continue;
            }
            for (CtTypeReference<?> interfaceRef : type.getSuperInterfaces()) {
                CtType<?> interfaceType = typesByName.get(interfaceRef.getQualifiedName());
                if (interfaceType == null) {
                    continue;
                }
                for (CtMethod<?> implementationMethod : type.getMethods()) {
                    for (CtMethod<?> interfaceMethod : interfaceType.getMethods()) {
                        if (sameMethodShape(implementationMethod, interfaceMethod)) {
                            addFact(
                                context,
                                context.symbols().method(implementationMethod),
                                RelationType.IMPLEMENTS,
                                context.symbols().method(interfaceMethod),
                                implementationMethod.getPosition(),
                                "interface-method",
                                Confidence.LIKELY
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean sameMethodShape(CtMethod<?> left, CtMethod<?> right) {
        return left.getSimpleName().equals(right.getSimpleName())
            && left.getParameters().size() == right.getParameters().size();
    }

    private void addFileDeclaration(
        JavaAnalysisContext context,
        SymbolId declaredSymbol,
        SourcePosition position
    ) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return;
        }
        SymbolId fileSymbol = context.symbols().sourceFile(context.scope().root(), position.getFile().toPath());
        context.nodes().add(GraphNodeFactory.sourceFile(declaredSymbol.projectKey(), declaredSymbol.moduleKey(), declaredSymbol.sourceRootKey(), relativePath(context.scope().root(), position.getFile())));
        addFact(context, fileSymbol, RelationType.DECLARES, declaredSymbol, position, "file-declares", Confidence.CERTAIN);
    }

    private void addInvocations(
        JavaAnalysisContext context,
        SymbolId caller,
        List<CtInvocation<?>> invocations
    ) {
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                continue;
            }
            SymbolId target = context.symbols().executableReference(executable);
            NodeRole targetRole = roleForExecutableReference(executable, context.rolesByType());
            if (ApplicationLayerClassifier.isSupportLayer(targetRole)) {
                CtTypeReference<?> declaringType = executable.getDeclaringType();
                if (declaringType != null) {
                    context.nodes().add(GraphNodeFactory.classNode(context.symbols().typeReference(declaringType), targetRole));
                }
                context.nodes().add(GraphNodeFactory.methodNode(target, targetRole));
            }
            addFact(context, caller, RelationType.CALLS, target, invocation.getPosition(), invocationQualifier(executable, targetRole), Confidence.LIKELY);
        }
    }

    private NodeRole roleForExecutableReference(CtExecutableReference<?> executable, Map<String, NodeRole> rolesByType) {
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null || declaringType.getQualifiedName() == null) {
            return NodeRole.CODE_MEMBER;
        }
        return roleForTypeReference(declaringType, rolesByType);
    }

    private NodeRole roleForTypeReference(CtTypeReference<?> typeReference, Map<String, NodeRole> rolesByType) {
        String qualifiedName = typeReference.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return NodeRole.CODE_TYPE;
        }
        return rolesByType.getOrDefault(qualifiedName, ApplicationLayerClassifier.roleForQualifiedName(qualifiedName));
    }

    private boolean isStatelessStaticSupportType(CtType<?> type) {
        if (type.isInterface() || type.isAnonymous()) {
            return false;
        }
        boolean hasMethod = !type.getMethods().isEmpty();
        boolean hasStaticMethod = type.getMethods().stream().anyMatch(CtMethod::isStatic);
        boolean hasInstanceMethod = type.getMethods().stream().anyMatch(method -> !method.isStatic());
        boolean hasField = !type.getFields().isEmpty();
        boolean hasStaticOnlyFields = hasField && type.getFields().stream().allMatch(CtField::isStatic);
        boolean hasInstanceField = type.getFields().stream().anyMatch(field -> !field.isStatic());
        if (hasInstanceMethod || hasInstanceField) {
            return false;
        }
        return (hasMethod && hasStaticMethod) || hasStaticOnlyFields;
    }

    private String invocationQualifier(CtExecutableReference<?> executable, NodeRole targetRole) {
        if (targetRole == NodeRole.UTILITY && executable.isStatic()) {
            return "static-utility";
        }
        if (targetRole == NodeRole.UTILITY) {
            return "utility";
        }
        if (executable.isStatic()) {
            return "static-direct";
        }
        return "direct";
    }

    private void addFact(
        JavaAnalysisContext context,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        SourcePosition position,
        String qualifier,
        Confidence confidence
    ) {
        EvidenceKey evidenceKey = evidenceKey(position, qualifier);
        context.facts().add(GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            evidenceKey,
            context.scope().projectId(),
            context.scope().snapshotId(),
            context.scope().analysisRunId(),
            context.scope().scopeKey(),
            confidence,
            SourceType.SPOON
        ));
    }

    private EvidenceKey evidenceKey(SourcePosition position, String qualifier) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "spoon-java", "_unknown", 0, 0, qualifier);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "spoon-java",
            position.getFile().toPath().toString(),
            Math.max(0, position.getLine()),
            Math.max(0, position.getEndLine()),
            qualifier
        );
    }

    private Path relativePath(Path root, File file) {
        return root.relativize(file.toPath()).normalize();
    }

    private record JavaAnalysisContext(
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        List<GraphNode> nodes,
        List<GraphFact> facts,
        Map<String, NodeRole> rolesByType
    ) {
    }
}
