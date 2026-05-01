package org.sainm.codeatlas.analyzers.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;
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
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JavaParserFastScanner {
    public JavaAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            scanFile(scope, projectKey, sourceRootKey, sourceFile, nodes, facts);
        }
        return new JavaAnalysisResult(nodes, facts);
    }

    private void scanFile(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path sourceFile,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(sourceFile);
            String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getName().asString()).orElse("");
            for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                scanType(scope, projectKey, sourceRootKey, sourceFile, packageName, type, nodes, facts);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fast-scan Java source: " + sourceFile, exception);
        }
    }

    private void scanType(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path sourceFile,
        String packageName,
        TypeDeclaration<?> type,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId typeSymbol = typeSymbol(projectKey, scope.moduleKey(), sourceRootKey, packageName, type);
        nodes.add(GraphNodeFactory.classNode(typeSymbol, ApplicationLayerClassifier.roleForQualifiedName(typeSymbol.ownerQualifiedName())));
        SymbolId fileSymbol = SymbolId.logicalPath(SymbolKind.SOURCE_FILE, projectKey, scope.moduleKey(), sourceRootKey, sourceFile.toString(), null);
        nodes.add(GraphNodeFactory.sourceFile(projectKey, scope.moduleKey(), sourceRootKey, sourceFile));
        facts.add(fact(scope, fileSymbol, RelationType.DECLARES, typeSymbol, sourceFile, line(type), "javaparser-type", Confidence.LIKELY));

        if (type instanceof ClassOrInterfaceDeclaration classType) {
            scanClassOrInterface(scope, projectKey, sourceRootKey, sourceFile, typeSymbol, classType, nodes, facts);
        }
        for (FieldDeclaration field : type.getFields()) {
            for (var variable : field.getVariables()) {
                SymbolId fieldSymbol = SymbolId.field(projectKey, scope.moduleKey(), sourceRootKey, typeSymbol.ownerQualifiedName(), variable.getNameAsString(), sourceType(variable.getType()));
                nodes.add(GraphNodeFactory.fieldNode(fieldSymbol, NodeRole.CODE_MEMBER, GraphNodeFactory.sourceMethodProperties()));
                facts.add(fact(scope, typeSymbol, RelationType.DECLARES, fieldSymbol, sourceFile, line(field), "javaparser-field:" + variable.getNameAsString(), Confidence.LIKELY));
            }
        }
        for (ConstructorDeclaration constructor : type.getConstructors()) {
            SymbolId constructorSymbol = SymbolId.method(projectKey, scope.moduleKey(), sourceRootKey, typeSymbol.ownerQualifiedName(), "<init>", descriptor(constructor.getParameters().stream().map(parameter -> parameter.getType()).toList(), "void"));
            nodes.add(GraphNodeFactory.methodNode(constructorSymbol, NodeRole.CODE_MEMBER, fastSourceProperties()));
            facts.add(fact(scope, typeSymbol, RelationType.DECLARES, constructorSymbol, sourceFile, line(constructor), "javaparser-constructor", Confidence.LIKELY));
        }
        for (MethodDeclaration method : type.getMethods()) {
            SymbolId methodSymbol = SymbolId.method(projectKey, scope.moduleKey(), sourceRootKey, typeSymbol.ownerQualifiedName(), method.getNameAsString(), descriptor(method.getParameters().stream().map(parameter -> parameter.getType()).toList(), sourceType(method.getType())));
            nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER, fastSourceProperties()));
            facts.add(fact(scope, typeSymbol, RelationType.DECLARES, methodSymbol, sourceFile, line(method), "javaparser-method:" + method.getNameAsString(), Confidence.LIKELY));
        }
    }

    private void scanClassOrInterface(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path sourceFile,
        SymbolId typeSymbol,
        ClassOrInterfaceDeclaration classType,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        for (var extendedType : classType.getExtendedTypes()) {
            SymbolId target = typeReference(projectKey, scope.moduleKey(), sourceRootKey, typeSymbol.ownerQualifiedName(), extendedType.getNameAsString(), classType.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS);
            nodes.add(GraphNodeFactory.classNode(target, ApplicationLayerClassifier.roleForQualifiedName(target.ownerQualifiedName())));
            facts.add(fact(scope, typeSymbol, RelationType.EXTENDS, target, sourceFile, line(classType), "javaparser-extends", Confidence.LIKELY));
        }
        for (var implementedType : classType.getImplementedTypes()) {
            SymbolId target = typeReference(projectKey, scope.moduleKey(), sourceRootKey, typeSymbol.ownerQualifiedName(), implementedType.getNameAsString(), SymbolKind.INTERFACE);
            nodes.add(GraphNodeFactory.classNode(target, ApplicationLayerClassifier.roleForQualifiedName(target.ownerQualifiedName())));
            facts.add(fact(scope, typeSymbol, RelationType.IMPLEMENTS, target, sourceFile, line(classType), "javaparser-implements", Confidence.LIKELY));
        }
    }

    private SymbolId typeSymbol(String projectKey, String moduleKey, String sourceRootKey, String packageName, TypeDeclaration<?> type) {
        return new SymbolId(symbolKind(type), projectKey, moduleKey, sourceRootKey, qualifiedName(packageName, type), null, null, null);
    }

    private SymbolKind symbolKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration classType) {
            return classType.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS;
        }
        if (type instanceof EnumDeclaration) {
            return SymbolKind.ENUM;
        }
        if (type instanceof AnnotationDeclaration) {
            return SymbolKind.ANNOTATION;
        }
        return SymbolKind.CLASS;
    }

    private String qualifiedName(String packageName, TypeDeclaration<?> type) {
        Optional<String> qualifiedName = type.getFullyQualifiedName();
        if (qualifiedName.isPresent()) {
            return qualifiedName.get();
        }
        return packageName == null || packageName.isBlank() ? type.getNameAsString() : packageName + "." + type.getNameAsString();
    }

    private SymbolId typeReference(String projectKey, String moduleKey, String sourceRootKey, String currentOwner, String simpleName, SymbolKind kind) {
        String packageName = "";
        int separator = currentOwner.lastIndexOf('.');
        if (separator > 0) {
            packageName = currentOwner.substring(0, separator);
        }
        String qualifiedName = simpleName.contains(".") || packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        return new SymbolId(kind, projectKey, moduleKey, sourceRootKey, qualifiedName, null, null, null);
    }

    private String descriptor(List<Type> parameters, String returnType) {
        List<String> parameterTypes = parameters.stream().map(this::sourceType).toList();
        return "(" + String.join(",", parameterTypes) + "):" + normalizeType(returnType);
    }

    private String sourceType(Type type) {
        return normalizeType(type.asString());
    }

    private String normalizeType(String type) {
        return switch (type) {
            case "String" -> "java.lang.String";
            case "Integer" -> "java.lang.Integer";
            case "Long" -> "java.lang.Long";
            case "Boolean" -> "java.lang.Boolean";
            case "Double" -> "java.lang.Double";
            case "Float" -> "java.lang.Float";
            case "Short" -> "java.lang.Short";
            case "Byte" -> "java.lang.Byte";
            case "Character" -> "java.lang.Character";
            default -> type;
        };
    }

    private java.util.Map<String, String> fastSourceProperties() {
        java.util.LinkedHashMap<String, String> properties = new java.util.LinkedHashMap<>(GraphNodeFactory.sourceMethodProperties());
        properties.put("codeOrigin", "javaparser-fast");
        return java.util.Map.copyOf(properties);
    }

    private int line(Node node) {
        return node.getRange().map(range -> range.begin.line).orElse(0);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path path,
        int line,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.JAVAPARSER_FAST, "javaparser-fast-scan", path.toString(), line, line, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.JAVAPARSER_FAST
        );
    }
}
