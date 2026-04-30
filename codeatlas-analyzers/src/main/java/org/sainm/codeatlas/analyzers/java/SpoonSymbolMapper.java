package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.stream.Collectors;
import spoon.reflect.code.CtLambda;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public final class SpoonSymbolMapper {
    private final String projectKey;
    private final String moduleKey;
    private final String sourceRootKey;

    public SpoonSymbolMapper(String projectKey, String moduleKey, String sourceRootKey) {
        this.projectKey = projectKey;
        this.moduleKey = moduleKey;
        this.sourceRootKey = sourceRootKey;
    }

    public SymbolId sourceFile(Path root, Path file) {
        Path relative = root.relativize(file).normalize();
        return SymbolId.logicalPath(SymbolKind.SOURCE_FILE, projectKey, moduleKey, sourceRootKey, relative.toString(), null);
    }

    public SymbolId type(CtType<?> type) {
        return new SymbolId(
            kind(type),
            projectKey,
            moduleKey,
            sourceRootKey,
            type.getQualifiedName(),
            null,
            null,
            null
        );
    }

    public SymbolId method(CtMethod<?> method) {
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRootKey,
            method.getDeclaringType().getQualifiedName(),
            method.getSimpleName(),
            sourceDescriptor(method)
        );
    }

    public SymbolId constructor(CtConstructor<?> constructor) {
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRootKey,
            constructor.getDeclaringType().getQualifiedName(),
            "<init>",
            sourceDescriptor(constructor)
        );
    }

    public SymbolId initializer(CtAnonymousExecutable initializer) {
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRootKey,
            initializer.getDeclaringType().getQualifiedName(),
            initializer.isStatic() ? "<clinit>" : "<init-block>",
            "():void@" + stablePosition(initializer.getPosition())
        );
    }

    public SymbolId lambda(CtLambda<?> lambda, int index) {
        CtType<?> ownerType = lambda.getParent(CtType.class);
        String owner = ownerType == null ? "_unknown" : ownerType.getQualifiedName();
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRootKey,
            owner,
            "lambda$" + stablePosition(lambda.getPosition()) + "$" + index,
            "():_lambda"
        );
    }

    public SymbolId field(CtField<?> field) {
        return SymbolId.field(
            projectKey,
            moduleKey,
            sourceRootKey,
            field.getDeclaringType().getQualifiedName(),
            field.getSimpleName(),
            typeName(field.getType())
        );
    }

    public SymbolId executableReference(CtExecutableReference<?> reference) {
        CtTypeReference<?> declaringType = reference.getDeclaringType();
        String owner = declaringType == null ? "_unknown" : declaringType.getQualifiedName();
        String name = reference.isConstructor() ? "<init>" : reference.getSimpleName();
        return SymbolId.method(projectKey, moduleKey, sourceRootKey, owner, name, sourceDescriptor(reference));
    }

    public SymbolId typeReference(CtTypeReference<?> reference) {
        return new SymbolId(
            reference.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS,
            projectKey,
            moduleKey,
            sourceRootKey,
            reference.getQualifiedName(),
            null,
            null,
            null
        );
    }

    private String sourceDescriptor(CtExecutable<?> executable) {
        String params = executable.getParameters().stream()
            .map(parameter -> typeName(parameter.getType()))
            .collect(Collectors.joining(","));
        String returnType = executable instanceof CtMethod<?> method ? typeName(method.getType()) : "void";
        return "(" + params + "):" + returnType;
    }

    private String sourceDescriptor(CtExecutableReference<?> reference) {
        String params = reference.getParameters().stream()
            .map(this::typeName)
            .collect(Collectors.joining(","));
        String returnType = reference.getType() == null ? "_unknown" : typeName(reference.getType());
        return "(" + params + "):" + returnType;
    }

    private String typeName(CtTypeReference<?> reference) {
        return reference == null ? "_unknown" : reference.getQualifiedName();
    }

    private String stablePosition(SourcePosition position) {
        if (position == null || !position.isValidPosition()) {
            return "0";
        }
        return Math.max(0, position.getLine()) + ":" + Math.max(0, position.getColumn());
    }

    private SymbolKind kind(CtType<?> type) {
        if (type.isAnnotationType()) {
            return SymbolKind.ANNOTATION;
        }
        if (type.isEnum()) {
            return SymbolKind.ENUM;
        }
        if (type.isInterface()) {
            return SymbolKind.INTERFACE;
        }
        return SymbolKind.CLASS;
    }
}
