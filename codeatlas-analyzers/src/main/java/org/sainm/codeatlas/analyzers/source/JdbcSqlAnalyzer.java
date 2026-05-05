package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class JdbcSqlAnalyzer {
    private static final Set<String> JDBC_SQL_METHODS = Set.of(
            "prepareStatement",
            "prepareCall",
            "execute",
            "executeQuery",
            "executeUpdate",
            "executeLargeUpdate");

    private JdbcSqlAnalyzer() {
    }

    public static JdbcSqlAnalyzer defaults() {
        return new JdbcSqlAnalyzer();
    }

    public JdbcSqlAnalysisResult analyze(Path sourceRoot, List<Path> sourceFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new JdbcSqlAnalysisResult(List.of(), List.of());
        }
        try {
            return extract(sourceRoot, buildModel(sourceFiles));
        } catch (RuntimeException exception) {
            return new JdbcSqlAnalysisResult(
                    List.of(),
                    List.of(new JavaAnalysisDiagnostic("JDBC_SQL_ANALYSIS_FAILED", exception.getMessage())));
        }
    }

    private static CtModel buildModel(List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        for (Path sourceFile : sourceFiles) {
            launcher.addInputResource(sourceFile.toString());
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    private static JdbcSqlAnalysisResult extract(Path sourceRoot, CtModel model) {
        List<JdbcSqlStatementInfo> statements = new ArrayList<>();
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isJdbcSqlInvocation(invocation) || invocation.getArguments().isEmpty()) {
                continue;
            }
            CtMethod<?> ownerMethod = invocation.getParent(CtMethod.class);
            CtConstructor<?> ownerConstructor = ownerMethod == null ? invocation.getParent(CtConstructor.class) : null;
            CtType<?> ownerType = invocation.getParent(CtType.class);
            if (ownerType == null || ownerType.isShadow()
                    || (ownerMethod == null && ownerConstructor == null)) {
                continue;
            }
            String sql = evaluateString(invocation.getArguments().get(0), localConstants(invocation));
            if (sql == null || sql.isBlank()) {
                continue;
            }
            SourceLocation location = location(sourceRoot, invocation.getPosition());
            String ownerName = ownerMethod != null ? ownerMethod.getSimpleName() : "<init>";
            String ownerSignature = ownerSignature(ownerMethod, ownerConstructor);
            List<JdbcSqlParameterBindingInfo> parameters = parameterBindings(sourceRoot, invocation);
            statements.add(new JdbcSqlStatementInfo(
                    statementId(ownerType, ownerName, ownerSignature, location),
                    ownerType.getQualifiedName(),
                    ownerName,
                    ownerSignature,
                    sql,
                    parameters,
                    location));
        }
        return new JdbcSqlAnalysisResult(statements, List.of());
    }

    private static boolean isJdbcSqlInvocation(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!JDBC_SQL_METHODS.contains(methodName)) {
            return false;
        }
        List<String> typeNames = List.of(
                typeName(invocation.getExecutable().getDeclaringType()),
                invocation.getTarget() == null ? "" : typeName(invocation.getTarget().getType()));
        if (methodName.equals("prepareStatement") || methodName.equals("prepareCall")) {
            return typeNames.contains("java.sql.Connection");
        }
        return typeNames.stream().anyMatch(JdbcSqlAnalyzer::isJdbcStatementType);
    }

    private static List<JdbcSqlParameterBindingInfo> parameterBindings(Path sourceRoot, CtInvocation<?> prepareInvocation) {
        CtLocalVariable<?> statementVariable = prepareInvocation.getParent(CtLocalVariable.class);
        if (statementVariable == null || statementVariable.getDefaultExpression() != prepareInvocation) {
            return List.of();
        }
        String statementVariableName = statementVariable.getSimpleName();
        CtMethod<?> method = prepareInvocation.getParent(CtMethod.class);
        CtConstructor<?> constructor = method == null ? prepareInvocation.getParent(CtConstructor.class) : null;
        spoon.reflect.declaration.CtElement bodyOwner = method == null ? constructor : method;
        if (bodyOwner == null) {
            return List.of();
        }

        List<JdbcSqlParameterBindingInfo> bindings = new ArrayList<>();
        for (CtInvocation<?> invocation : bodyOwner.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isParameterBindingInvocation(invocation, statementVariableName)) {
                continue;
            }
            Integer index = parameterIndex(invocation.getArguments().get(0));
            if (index == null) {
                continue;
            }
            SourceLocation location = location(sourceRoot, invocation.getPosition());
            bindings.add(new JdbcSqlParameterBindingInfo(
                    index,
                    invocation.getExecutable().getSimpleName(),
                    location));
        }
        return bindings;
    }

    private static boolean isParameterBindingInvocation(CtInvocation<?> invocation, String statementVariableName) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!methodName.startsWith("set") || invocation.getArguments().size() < 2) {
            return false;
        }
        if (!(invocation.getTarget() instanceof CtVariableAccess<?> target)) {
            return false;
        }
        return target.getVariable() != null && statementVariableName.equals(target.getVariable().getSimpleName());
    }

    private static Integer parameterIndex(CtExpression<?> expression) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        return null;
    }

    private static boolean isJdbcStatementType(String typeName) {
        return typeName.equals("java.sql.Statement")
                || typeName.equals("java.sql.PreparedStatement")
                || typeName.equals("java.sql.CallableStatement");
    }

    private static String typeName(CtTypeReference<?> type) {
        return type == null ? "" : type.getQualifiedName();
    }

    private static Map<String, String> localConstants(CtInvocation<?> invocation) {
        CtMethod<?> method = invocation.getParent(CtMethod.class);
        CtConstructor<?> constructor = method == null ? invocation.getParent(CtConstructor.class) : null;
        spoon.reflect.declaration.CtElement bodyOwner = method == null ? constructor : method;
        if (bodyOwner == null) {
            return Map.of();
        }
        Map<String, String> constants = new HashMap<>();
        for (CtLocalVariable<?> variable : bodyOwner.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (variable.getDefaultExpression() == null || !variable.getModifiers().contains(ModifierKind.FINAL)) {
                continue;
            }
            String value = evaluateString(variable.getDefaultExpression(), constants);
            if (value != null) {
                constants.put(variable.getSimpleName(), value);
            }
        }
        return constants;
    }

    private static String evaluateString(CtExpression<?> expression, Map<String, String> constants) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String value) {
            return value;
        }
        if (expression instanceof CtVariableRead<?> variableRead) {
            return constants.get(variableRead.getVariable().getSimpleName());
        }
        if (expression instanceof CtBinaryOperator<?> binary && binary.getKind() == BinaryOperatorKind.PLUS) {
            String left = evaluateString(binary.getLeftHandOperand(), constants);
            String right = evaluateString(binary.getRightHandOperand(), constants);
            return left == null || right == null ? null : left + right;
        }
        return null;
    }

    private static String statementId(
            CtType<?> ownerType,
            String ownerName,
            String ownerSignature,
            SourceLocation location) {
        return ownerType.getQualifiedName() + "." + ownerName + ownerSignature + "@" + location.line();
    }

    private static String ownerSignature(CtMethod<?> ownerMethod, CtConstructor<?> ownerConstructor) {
        if (ownerMethod != null) {
            return JavaDescriptor.methodDescriptor(ownerMethod.getParameters().stream()
                    .map(parameter -> parameter.getType())
                    .toList(), ownerMethod.getType());
        }
        return JavaDescriptor.methodDescriptor(ownerConstructor.getParameters().stream()
                .map(parameter -> parameter.getType())
                .toList(), null);
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
