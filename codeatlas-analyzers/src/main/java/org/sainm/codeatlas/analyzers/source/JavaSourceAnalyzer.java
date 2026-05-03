package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class JavaSourceAnalyzer {
    private JavaSourceAnalyzer() {
    }

    public static JavaSourceAnalyzer defaults() {
        return new JavaSourceAnalyzer();
    }

    public JavaSourceAnalysisResult analyze(Path sourceRoot, List<Path> sourceFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new JavaSourceAnalysisResult(false, List.of(), List.of(), List.of(), List.of(), List.of());
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

    private static JavaSourceAnalysisResult extract(
            Path sourceRoot,
            CtModel model,
            boolean noClasspathFallbackUsed,
            List<JavaAnalysisDiagnostic> diagnostics) {
        List<JavaClassInfo> classes = new ArrayList<>();
        List<JavaMethodInfo> methods = new ArrayList<>();
        List<JavaFieldInfo> fields = new ArrayList<>();
        List<JavaInvocationInfo> invocations = new ArrayList<>();
        for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
            if (type.isShadow()) {
                continue;
            }
            classes.add(new JavaClassInfo(
                    type.getQualifiedName(),
                    type.getSimpleName(),
                    annotations(type),
                    location(sourceRoot, type.getPosition())));
            for (CtField<?> field : type.getFields()) {
                fields.add(new JavaFieldInfo(
                        type.getQualifiedName(),
                        field.getSimpleName(),
                        typeName(field.getType()),
                        JavaDescriptor.typeDescriptor(field.getType()),
                        annotations(field),
                        location(sourceRoot, field.getPosition())));
            }
            for (CtMethod<?> method : type.getMethods()) {
                methods.add(new JavaMethodInfo(
                        type.getQualifiedName(),
                        method.getSimpleName(),
                        JavaDescriptor.methodDescriptor(method.getParameters().stream()
                                .map(parameter -> parameter.getType())
                                .toList(), method.getType()),
                        typeName(method.getType()),
                        annotations(method),
                        location(sourceRoot, method.getPosition())));
            }
        }
        for (CtConstructor<?> constructor : model.getElements(new TypeFilter<>(CtConstructor.class))) {
            CtType<?> ownerType = constructor.getParent(CtType.class);
            SourceLocation constructorLocation = location(sourceRoot, constructor.getPosition());
            if (ownerType == null || ownerType.isShadow() || constructorLocation.relativePath().isBlank()) {
                continue;
            }
            methods.add(new JavaMethodInfo(
                    ownerType.getQualifiedName(),
                    "<init>",
                    JavaDescriptor.methodDescriptor(constructor.getParameters().stream()
                            .map(parameter -> parameter.getType())
                            .toList(), null),
                    "void",
                    annotations(constructor),
                    constructorLocation));
        }
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtMethod<?> ownerMethod = invocation.getParent(CtMethod.class);
            CtConstructor<?> ownerConstructor = ownerMethod == null ? invocation.getParent(CtConstructor.class) : null;
            CtType<?> ownerType = invocation.getParent(CtType.class);
            CtExecutableReference<?> executable = invocation.getExecutable();
            invocations.add(new JavaInvocationInfo(
                    ownerType == null ? "" : ownerType.getQualifiedName(),
                    ownerName(ownerMethod, ownerConstructor),
                    ownerSignature(ownerMethod, ownerConstructor),
                    typeName(executable.getDeclaringType()),
                    executable.getSimpleName(),
                    JavaDescriptor.methodDescriptor(executable.getParameters(), executable.getType()),
                    location(sourceRoot, invocation.getPosition())));
        }
        for (CtConstructorCall<?> constructorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtMethod<?> ownerMethod = constructorCall.getParent(CtMethod.class);
            CtConstructor<?> ownerConstructor = ownerMethod == null ? constructorCall.getParent(CtConstructor.class) : null;
            CtType<?> ownerType = constructorCall.getParent(CtType.class);
            CtExecutableReference<?> executable = constructorCall.getExecutable();
            invocations.add(new JavaInvocationInfo(
                    ownerType == null ? "" : ownerType.getQualifiedName(),
                    ownerName(ownerMethod, ownerConstructor),
                    ownerSignature(ownerMethod, ownerConstructor),
                    typeName(executable.getDeclaringType()),
                    "<init>",
                    JavaDescriptor.methodDescriptor(executable.getParameters(), null),
                    location(sourceRoot, constructorCall.getPosition())));
        }
        return new JavaSourceAnalysisResult(
                noClasspathFallbackUsed,
                sortedClasses(classes),
                sortedMethods(methods),
                sortedFields(fields),
                sortedInvocations(invocations),
                diagnostics);
    }

    private static String ownerName(CtMethod<?> ownerMethod, CtConstructor<?> ownerConstructor) {
        if (ownerMethod != null) {
            return ownerMethod.getSimpleName();
        }
        return ownerConstructor == null ? "" : "<init>";
    }

    private static String ownerSignature(CtMethod<?> ownerMethod, CtConstructor<?> ownerConstructor) {
        if (ownerMethod != null) {
            return JavaDescriptor.methodDescriptor(ownerMethod.getParameters().stream()
                    .map(parameter -> parameter.getType())
                    .toList(), ownerMethod.getType());
        }
        if (ownerConstructor != null) {
            return JavaDescriptor.methodDescriptor(ownerConstructor.getParameters().stream()
                    .map(parameter -> parameter.getType())
                    .toList(), null);
        }
        return "";
    }

    private static List<String> annotations(spoon.reflect.declaration.CtElement element) {
        List<String> result = new ArrayList<>();
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            result.add(annotation.getAnnotationType().getQualifiedName());
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

    private static List<JavaClassInfo> sortedClasses(List<JavaClassInfo> classes) {
        return classes.stream()
                .sorted(Comparator.comparing(JavaClassInfo::qualifiedName))
                .toList();
    }

    private static List<JavaMethodInfo> sortedMethods(List<JavaMethodInfo> methods) {
        return methods.stream()
                .sorted(Comparator.comparing(JavaMethodInfo::ownerQualifiedName)
                        .thenComparing(JavaMethodInfo::signature))
                .toList();
    }

    private static List<JavaFieldInfo> sortedFields(List<JavaFieldInfo> fields) {
        return fields.stream()
                .sorted(Comparator.comparing(JavaFieldInfo::ownerQualifiedName)
                        .thenComparing(JavaFieldInfo::simpleName))
                .toList();
    }

    private static List<JavaInvocationInfo> sortedInvocations(List<JavaInvocationInfo> invocations) {
        return invocations.stream()
                .sorted(Comparator.comparing(JavaInvocationInfo::ownerQualifiedName)
                        .thenComparing(JavaInvocationInfo::ownerMethodName)
                        .thenComparing(JavaInvocationInfo::targetSignature))
                .toList();
    }
}
