package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;

public final class JpaEntityAnalyzer {
    private JpaEntityAnalyzer() {
    }

    public static JpaEntityAnalyzer defaults() {
        return new JpaEntityAnalyzer();
    }

    public JpaEntityAnalysisResult analyze(Path sourceRoot, List<Path> sourceFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new JpaEntityAnalysisResult(List.of(), List.of());
        }
        try {
            return extract(sourceRoot, buildModel(sourceFiles));
        } catch (RuntimeException exception) {
            return new JpaEntityAnalysisResult(
                    List.of(),
                    List.of(new JavaAnalysisDiagnostic("JPA_ENTITY_ANALYSIS_FAILED", exception.getMessage())));
        }
    }

    private static CtModel buildModel(List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        for (Path sourceFile : sourceFiles) {
            launcher.addInputResource(sourceFile.toString());
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    private static JpaEntityAnalysisResult extract(Path sourceRoot, CtModel model) {
        List<JpaEntityInfo> entities = new ArrayList<>();
        for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
            if (type.isShadow() || !hasAnnotation(type, "Entity")) {
                continue;
            }
            String tableName = annotationValue(type, "Table", "name");
            if (tableName.isBlank()) {
                tableName = type.getSimpleName();
            }
            String schemaName = annotationValue(type, "Table", "schema");
            List<JpaColumnMappingInfo> columns = new ArrayList<>();
            for (CtField<?> field : type.getFields()) {
                if (field.getModifiers().contains(ModifierKind.STATIC)
                        || field.getModifiers().contains(ModifierKind.TRANSIENT)
                        || hasAnnotation(field, "Transient")) {
                    continue;
                }
                String columnName = annotationValue(field, "Column", "name");
                if (columnName.isBlank()) {
                    columnName = field.getSimpleName();
                }
                columns.add(new JpaColumnMappingInfo(
                        field.getSimpleName(),
                        JavaDescriptor.typeDescriptor(field.getType()),
                        columnName,
                        location(sourceRoot, field.getPosition())));
            }
            entities.add(new JpaEntityInfo(
                    type.getQualifiedName(),
                    tableName,
                    schemaName,
                    columns,
                    location(sourceRoot, type.getPosition())));
        }
        return new JpaEntityAnalysisResult(entities, List.of());
    }

    private static boolean hasAnnotation(spoon.reflect.declaration.CtElement element, String simpleName) {
        return element.getAnnotations().stream()
                .anyMatch(annotation -> annotationName(annotation).equals(simpleName)
                        || annotationName(annotation).endsWith("." + simpleName));
    }

    private static String annotationValue(
            spoon.reflect.declaration.CtElement element,
            String annotationSimpleName,
            String attributeName) {
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            String annotationName = annotationName(annotation);
            if (!annotationName.equals(annotationSimpleName) && !annotationName.endsWith("." + annotationSimpleName)) {
                continue;
            }
            Object value = annotation.getValue(attributeName);
            return stringValue(value);
        }
        return "";
    }

    private static String annotationName(CtAnnotation<?> annotation) {
        return annotation.getAnnotationType() == null ? "" : annotation.getAnnotationType().getQualifiedName();
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CtLiteral<?> literal) {
            return stringValue(literal.getValue());
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return value.toString().replace("\"", "").trim();
    }

    private static SourceLocation location(Path sourceRoot, SourcePosition position) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new SourceLocation("", 0, 0);
        }
        String relativePath = sourceRoot.toAbsolutePath().normalize()
                .relativize(position.getFile().toPath().toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        return new SourceLocation(relativePath, position.getLine(), position.getColumn());
    }
}
