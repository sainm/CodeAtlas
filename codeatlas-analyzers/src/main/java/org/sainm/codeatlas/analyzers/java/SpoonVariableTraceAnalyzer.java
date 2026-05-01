package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class SpoonVariableTraceAnalyzer {
    private final int complianceLevel;
    private final boolean noClasspath;

    public SpoonVariableTraceAnalyzer() {
        this(25, true);
    }

    public SpoonVariableTraceAnalyzer(int complianceLevel, boolean noClasspath) {
        this.complianceLevel = complianceLevel;
        this.noClasspath = noClasspath;
    }

    public VariableTraceResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(complianceLevel);
        launcher.getEnvironment().setNoClasspath(noClasspath);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<VariableEvent> events = new ArrayList<>();
        List<MethodArgumentFlowEvent> argumentFlows = new ArrayList<>();
        for (CtType<?> type : model.getAllTypes()) {
            for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
                if (type.equals(constructor.getDeclaringType())) {
                    collectExecutable(events, argumentFlows, symbols, symbols.constructor(constructor), constructor);
                }
            }
            for (CtMethod<?> method : type.getMethods()) {
                collectExecutable(events, argumentFlows, symbols, symbols.method(method), method);
                collectBeanAccessor(events, symbols.method(method), method);
            }
        }
        return new VariableTraceResult(events, argumentFlows);
    }

    private void collectExecutable(
        List<VariableEvent> events,
        List<MethodArgumentFlowEvent> argumentFlows,
        SpoonSymbolMapper symbols,
        SymbolId methodSymbol,
        CtExecutable<?> executable
    ) {
        Map<String, VariableSource> sourceByVariable = new LinkedHashMap<>();
        Set<String> actionFormVariables = new LinkedHashSet<>();
        for (CtParameter<?> parameter : executable.getParameters()) {
            sourceByVariable.put(parameter.getSimpleName(), new VariableSource("", parameter.getSimpleName(), "method-parameter"));
            if (isActionFormType(parameter.getType())) {
                actionFormVariables.add(parameter.getSimpleName());
            }
            events.add(new VariableEvent(
                methodSymbol,
                parameter.getSimpleName(),
                VariableEventKind.PARAMETER,
                parameter.getType() == null ? "" : parameter.getType().getQualifiedName(),
                line(parameter.getPosition()),
                path(parameter.getPosition())
            ));
        }
        for (CtLocalVariable<?> local : executable.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            events.add(new VariableEvent(
                methodSymbol,
                local.getSimpleName(),
                VariableEventKind.LOCAL_DEFINITION,
                local.getDefaultExpression() == null ? "" : local.getDefaultExpression().toString(),
                line(local.getPosition()),
                path(local.getPosition())
            ));
        }
        for (CtAssignment<?, ?> assignment : executable.getElements(new TypeFilter<>(CtAssignment.class))) {
            String assignedName = variableName(assignment.getAssigned());
            events.add(new VariableEvent(
                methodSymbol,
                assignedName,
                VariableEventKind.ASSIGNMENT,
                assignment.getAssignment() == null ? "" : assignment.getAssignment().toString(),
                line(assignment.getPosition()),
                path(assignment.getPosition())
            ));
        }
        for (CtVariableWrite<?> write : executable.getElements(new TypeFilter<>(CtVariableWrite.class))) {
            events.add(new VariableEvent(methodSymbol, variableName(write), VariableEventKind.WRITE, write.toString(), line(write.getPosition()), path(write.getPosition())));
        }
        for (CtVariableRead<?> read : executable.getElements(new TypeFilter<>(CtVariableRead.class))) {
            events.add(new VariableEvent(methodSymbol, variableName(read), VariableEventKind.READ, read.toString(), line(read.getPosition()), path(read.getPosition())));
        }
        for (CtReturn<?> ctReturn : executable.getElements(new TypeFilter<>(CtReturn.class))) {
            events.add(new VariableEvent(
                methodSymbol,
                "",
                VariableEventKind.RETURN,
                ctReturn.getReturnedExpression() == null ? "" : ctReturn.getReturnedExpression().toString(),
                line(ctReturn.getPosition()),
                path(ctReturn.getPosition())
            ));
        }
        for (CtInvocation<?> invocation : executable.getElements(new TypeFilter<>(CtInvocation.class))) {
            addRequestInvocation(events, methodSymbol, invocation);
        }
        collectArgumentFlowsInSourceOrder(argumentFlows, symbols, methodSymbol, executable, sourceByVariable, actionFormVariables);
    }

    private void collectArgumentFlowsInSourceOrder(
        List<MethodArgumentFlowEvent> argumentFlows,
        SpoonSymbolMapper symbols,
        SymbolId methodSymbol,
        CtExecutable<?> executable,
        Map<String, VariableSource> sourceByVariable,
        Set<String> actionFormVariables
    ) {
        List<CtStatement> statements = new ArrayList<>();
        statements.addAll(executable.getElements(new TypeFilter<>(CtLocalVariable.class)));
        statements.addAll(executable.getElements(new TypeFilter<>(CtAssignment.class)));
        statements.addAll(executable.getElements(new TypeFilter<>(CtInvocation.class)));
        statements.stream()
            .sorted((left, right) -> Integer.compare(positionStart(left.getPosition()), positionStart(right.getPosition())))
            .forEach(statement -> {
                if (statement instanceof CtLocalVariable<?> local) {
                    if (isActionFormAlias(local.getDefaultExpression(), actionFormVariables)) {
                        actionFormVariables.add(local.getSimpleName());
                    }
                    VariableSource source = variableSource(local.getDefaultExpression(), sourceByVariable, actionFormVariables);
                    if (source.known()) {
                        sourceByVariable.put(local.getSimpleName(), source);
                    }
                } else if (statement instanceof CtAssignment<?, ?> assignment) {
                    String assignedName = variableName(assignment.getAssigned());
                    if (isActionFormAlias(assignment.getAssignment(), actionFormVariables)) {
                        actionFormVariables.add(assignedName);
                    }
                    VariableSource source = variableSource(assignment.getAssignment(), sourceByVariable, actionFormVariables);
                    if (!assignedName.isBlank() && source.known()) {
                        sourceByVariable.put(assignedName, source);
                    } else if (!assignedName.isBlank()) {
                        sourceByVariable.remove(assignedName);
                    }
                } else if (statement instanceof CtInvocation<?> invocation) {
                    addArgumentFlows(argumentFlows, symbols, methodSymbol, sourceByVariable, actionFormVariables, invocation);
                }
            });
    }

    private void collectBeanAccessor(List<VariableEvent> events, SymbolId methodSymbol, CtMethod<?> method) {
        String methodName = method.getSimpleName();
        String getterProperty = getterProperty(method);
        if (!getterProperty.isBlank()) {
            for (CtReturn<?> ctReturn : method.getElements(new TypeFilter<>(CtReturn.class))) {
                String returned = ctReturn.getReturnedExpression() == null ? "" : variableName(ctReturn.getReturnedExpression());
                if (returned.equals(getterProperty)) {
                    events.add(new VariableEvent(
                        methodSymbol,
                        getterProperty,
                        VariableEventKind.GETTER_RETURN,
                        ctReturn.getReturnedExpression().toString(),
                        line(ctReturn.getPosition()),
                        path(ctReturn.getPosition())
                    ));
                }
            }
        }

        String setterProperty = setterProperty(method);
        if (!setterProperty.isBlank()) {
            String parameterName = method.getParameters().getFirst().getSimpleName();
            for (CtAssignment<?, ?> assignment : method.getElements(new TypeFilter<>(CtAssignment.class))) {
                String assigned = variableName(assignment.getAssigned());
                String value = assignment.getAssignment() == null ? "" : variableName(assignment.getAssignment());
                if (assigned.equals(setterProperty) && value.equals(parameterName)) {
                    events.add(new VariableEvent(
                        methodSymbol,
                        setterProperty,
                        VariableEventKind.SETTER_WRITE,
                        parameterName,
                        line(assignment.getPosition()),
                        path(assignment.getPosition())
                    ));
                }
            }
        }
    }

    private void addRequestInvocation(List<VariableEvent> events, SymbolId methodSymbol, CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null) {
            return;
        }
        String methodName = invocation.getExecutable().getSimpleName();
        VariableEventKind kind = switch (methodName) {
            case "getParameter" -> VariableEventKind.REQUEST_PARAMETER_READ;
            case "getAttribute" -> VariableEventKind.REQUEST_ATTRIBUTE_READ;
            case "setAttribute" -> VariableEventKind.REQUEST_ATTRIBUTE_WRITE;
            default -> null;
        };
        if (kind == null) {
            return;
        }
        String key = invocation.getArguments().isEmpty() ? "" : stripQuotes(invocation.getArguments().getFirst().toString());
        events.add(new VariableEvent(methodSymbol, key, kind, invocation.toString(), line(invocation.getPosition()), path(invocation.getPosition())));
    }

    private void addArgumentFlows(
        List<MethodArgumentFlowEvent> argumentFlows,
        SpoonSymbolMapper symbols,
        SymbolId caller,
        Map<String, VariableSource> sourceByVariable,
        Set<String> actionFormVariables,
        CtInvocation<?> invocation
    ) {
        if (invocation.getExecutable() == null) {
            return;
        }
        SymbolId callee = symbols.executableReference(invocation.getExecutable());
        for (CtExpression<?> argument : invocation.getArguments()) {
            String directRequestParameter = requestParameterName(argument);
            if (!directRequestParameter.isBlank()) {
                argumentFlows.add(new MethodArgumentFlowEvent(
                    caller,
                    callee,
                    directRequestParameter,
                    argument.toString(),
                    "request-parameter-direct",
                    invocation.toString(),
                    line(invocation.getPosition()),
                    path(invocation.getPosition())
                ));
                continue;
            }
            String argumentName = variableName(argument);
            VariableSource source = variableSource(argument, sourceByVariable, actionFormVariables);
            if (source.known()) {
                argumentFlows.add(new MethodArgumentFlowEvent(
                    caller,
                    callee,
                    source.requestParameterName(),
                    argumentName,
                    source.flowKind(),
                    invocation.toString(),
                    line(invocation.getPosition()),
                    path(invocation.getPosition())
                ));
            }
        }
    }

    private VariableSource variableSource(
        CtExpression<?> expression,
        Map<String, VariableSource> sourceByVariable,
        Set<String> actionFormVariables
    ) {
        if (expression == null) {
            return VariableSource.unknown();
        }
        VariableSource actionFormSource = actionFormSource(expression, actionFormVariables);
        if (actionFormSource.known()) {
            return actionFormSource;
        }
        String requestParameterName = requestParameterName(expression);
        if (!requestParameterName.isBlank()) {
            return new VariableSource(requestParameterName, "", "request-parameter-local");
        }
        String sourceVariable = variableName(expression);
        VariableSource directSource = sourceByVariable.getOrDefault(sourceVariable, VariableSource.unknown());
        if (directSource.known()) {
            return directSource;
        }
        for (CtVariableRead<?> read : expression.getElements(new TypeFilter<>(CtVariableRead.class))) {
            VariableSource nestedSource = sourceByVariable.getOrDefault(variableName(read), VariableSource.unknown());
            if (nestedSource.known()) {
                return nestedSource;
            }
        }
        return VariableSource.unknown();
    }

    private boolean isActionFormAlias(CtExpression<?> expression, Set<String> actionFormVariables) {
        if (expression == null || actionFormVariables.isEmpty()) {
            return false;
        }
        return expression.getElements(new TypeFilter<>(CtVariableRead.class)).stream()
            .map(this::variableName)
            .anyMatch(actionFormVariables::contains);
    }

    private VariableSource actionFormSource(CtExpression<?> expression, Set<String> actionFormVariables) {
        VariableSource direct = actionFormInvocationSource(expression, actionFormVariables);
        if (direct.known()) {
            return direct;
        }
        for (CtInvocation<?> invocation : expression.getElements(new TypeFilter<>(CtInvocation.class))) {
            VariableSource nested = actionFormInvocationSource(invocation, actionFormVariables);
            if (nested.known()) {
                return nested;
            }
        }
        return VariableSource.unknown();
    }

    private VariableSource actionFormInvocationSource(CtExpression<?> expression, Set<String> actionFormVariables) {
        if (!(expression instanceof CtInvocation<?> invocation) || invocation.getExecutable() == null) {
            return VariableSource.unknown();
        }
        if (!invocation.getArguments().isEmpty()) {
            return dynaActionFormSource(invocation, actionFormVariables);
        }
        String property = getterProperty(invocation.getExecutable().getSimpleName());
        if (property.isBlank()) {
            return dynaActionFormSource(invocation, actionFormVariables);
        }
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return VariableSource.unknown();
        }
        return isActionFormAlias(target, actionFormVariables)
            ? new VariableSource(property, "", "action-form-getter")
            : VariableSource.unknown();
    }

    private VariableSource dynaActionFormSource(CtInvocation<?> invocation, Set<String> actionFormVariables) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!Set.of("get", "getString").contains(methodName) || invocation.getArguments().isEmpty()) {
            return VariableSource.unknown();
        }
        CtExpression<?> target = invocation.getTarget();
        if (target == null || !isActionFormAlias(target, actionFormVariables)) {
            return VariableSource.unknown();
        }
        String property = stripQuotes(invocation.getArguments().getFirst().toString());
        return property.isBlank()
            ? VariableSource.unknown()
            : new VariableSource(property, "", "action-form-get");
    }

    private String requestParameterName(CtExpression<?> expression) {
        if (!(expression instanceof CtInvocation<?> invocation) || invocation.getExecutable() == null) {
            return "";
        }
        if (!"getParameter".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().isEmpty()) {
            return "";
        }
        return stripQuotes(invocation.getArguments().getFirst().toString());
    }

    private String variableName(Object assigned) {
        if (assigned instanceof CtVariableAccess<?> access && access.getVariable() != null) {
            return access.getVariable().getSimpleName();
        }
        return assigned == null ? "" : assigned.toString();
    }

    private String getterProperty(CtMethod<?> method) {
        if (!method.getParameters().isEmpty()) {
            return "";
        }
        return getterProperty(method.getSimpleName());
    }

    private String getterProperty(String name) {
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2) {
            return decapitalize(name.substring(2));
        }
        return "";
    }

    private boolean isActionFormType(CtTypeReference<?> type) {
        if (type == null) {
            return false;
        }
        return Set.of(
            "org.apache.struts.action.ActionForm",
            "org.apache.struts.validator.ValidatorForm",
            "ActionForm",
            "ValidatorForm"
        ).contains(type.getQualifiedName()) || Set.of("ActionForm", "ValidatorForm").contains(type.getSimpleName());
    }

    private String setterProperty(CtMethod<?> method) {
        String name = method.getSimpleName();
        if (method.getParameters().size() == 1 && name.startsWith("set") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        return "";
    }

    private String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private int line(SourcePosition position) {
        if (position == null || !position.isValidPosition()) {
            return 0;
        }
        return Math.max(0, position.getLine());
    }

    private int positionStart(SourcePosition position) {
        if (position == null || !position.isValidPosition()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, position.getSourceStart());
    }

    private String path(SourcePosition position) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return "_unknown";
        }
        return position.getFile().toPath().toString();
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
            && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record VariableSource(String requestParameterName, String rootVariableName, String flowKind) {
        static VariableSource unknown() {
            return new VariableSource("", "", "");
        }

        boolean known() {
            return !requestParameterName.isBlank() || !rootVariableName.isBlank();
        }
    }
}
