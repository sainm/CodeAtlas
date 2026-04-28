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
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
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

        for (CtType<?> type : model.getAllTypes()) {
            analyzeType(scope, symbols, nodes, facts, type);
        }
        addMethodImplementationFacts(scope, symbols, model.getAllTypes(), facts);

        return new JavaAnalysisResult(nodes, facts);
    }

    private void analyzeType(
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        List<GraphNode> nodes,
        List<GraphFact> facts,
        CtType<?> type
    ) {
        SymbolId typeSymbol = symbols.type(type);
        nodes.add(GraphNodeFactory.classNode(typeSymbol, NodeRole.CODE_TYPE));
        addFileDeclaration(scope, symbols, nodes, facts, typeSymbol, type.getPosition());

        CtTypeReference<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addFact(scope, facts, typeSymbol, RelationType.EXTENDS, symbols.typeReference(superClass), type.getPosition(), "extends", Confidence.LIKELY);
        }
        for (CtTypeReference<?> implementedType : type.getSuperInterfaces()) {
            addFact(scope, facts, typeSymbol, RelationType.IMPLEMENTS, symbols.typeReference(implementedType), type.getPosition(), "implements", Confidence.LIKELY);
        }

        for (CtField<?> field : type.getFields()) {
            SymbolId fieldSymbol = symbols.field(field);
            nodes.add(GraphNodeFactory.fieldNode(fieldSymbol, NodeRole.CODE_MEMBER));
            addFact(scope, facts, typeSymbol, RelationType.DECLARES, fieldSymbol, field.getPosition(), "field", Confidence.CERTAIN);
        }
        for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
            if (!type.equals(constructor.getDeclaringType())) {
                continue;
            }
            SymbolId constructorSymbol = symbols.constructor(constructor);
            nodes.add(GraphNodeFactory.methodNode(constructorSymbol, NodeRole.CODE_MEMBER));
            addFact(scope, facts, typeSymbol, RelationType.DECLARES, constructorSymbol, constructor.getPosition(), "constructor", Confidence.CERTAIN);
            addInvocations(scope, symbols, facts, constructorSymbol, constructor.getElements(new TypeFilter<>(CtInvocation.class)));
        }
        for (CtMethod<?> method : type.getMethods()) {
            SymbolId methodSymbol = symbols.method(method);
            nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
            addFact(scope, facts, typeSymbol, RelationType.DECLARES, methodSymbol, method.getPosition(), "method", Confidence.CERTAIN);
            addInvocations(scope, symbols, facts, methodSymbol, method.getElements(new TypeFilter<>(CtInvocation.class)));
        }
    }

    private void addMethodImplementationFacts(
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        Collection<CtType<?>> types,
        List<GraphFact> facts
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
                                scope,
                                facts,
                                symbols.method(implementationMethod),
                                RelationType.IMPLEMENTS,
                                symbols.method(interfaceMethod),
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
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        List<GraphNode> nodes,
        List<GraphFact> facts,
        SymbolId declaredSymbol,
        SourcePosition position
    ) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return;
        }
        SymbolId fileSymbol = symbols.sourceFile(scope.root(), position.getFile().toPath());
        nodes.add(GraphNodeFactory.sourceFile(declaredSymbol.projectKey(), declaredSymbol.moduleKey(), declaredSymbol.sourceRootKey(), relativePath(scope.root(), position.getFile())));
        addFact(scope, facts, fileSymbol, RelationType.DECLARES, declaredSymbol, position, "file-declares", Confidence.CERTAIN);
    }

    private void addInvocations(
        AnalyzerScope scope,
        SpoonSymbolMapper symbols,
        List<GraphFact> facts,
        SymbolId caller,
        List<CtInvocation<?>> invocations
    ) {
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                continue;
            }
            addFact(scope, facts, caller, RelationType.CALLS, symbols.executableReference(executable), invocation.getPosition(), "direct", Confidence.LIKELY);
        }
    }

    private void addFact(
        AnalyzerScope scope,
        List<GraphFact> facts,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        SourcePosition position,
        String qualifier,
        Confidence confidence
    ) {
        EvidenceKey evidenceKey = evidenceKey(position, qualifier);
        facts.add(GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            evidenceKey,
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
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
}
