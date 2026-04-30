package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.SymbolId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
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
        Set<String> methodParameters = new LinkedHashSet<>();
        Map<String, String> requestParameterByVariable = new LinkedHashMap<>();
        for (CtParameter<?> parameter : executable.getParameters()) {
            methodParameters.add(parameter.getSimpleName());
            events.add(new VariableEvent(
                methodSymbol,
                parameter.getSimpleName(),
                VariableEventKind.PARAMETER,
                parameter.getType() == null ? "" : parameter.getType().getQualifiedName(),
                line(parameter.getPosition())
            ));
        }
        for (CtLocalVariable<?> local : executable.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            String requestParameterName = requestParameterName(local.getDefaultExpression());
            if (!requestParameterName.isBlank()) {
                requestParameterByVariable.put(local.getSimpleName(), requestParameterName);
            }
            events.add(new VariableEvent(
                methodSymbol,
                local.getSimpleName(),
                VariableEventKind.LOCAL_DEFINITION,
                local.getDefaultExpression() == null ? "" : local.getDefaultExpression().toString(),
                line(local.getPosition())
            ));
        }
        for (CtAssignment<?, ?> assignment : executable.getElements(new TypeFilter<>(CtAssignment.class))) {
            String assignedName = variableName(assignment.getAssigned());
            String requestParameterName = requestParameterName(assignment.getAssignment());
            if (!assignedName.isBlank() && !requestParameterName.isBlank()) {
                requestParameterByVariable.put(assignedName, requestParameterName);
            }
            events.add(new VariableEvent(
                methodSymbol,
                assignedName,
                VariableEventKind.ASSIGNMENT,
                assignment.getAssignment() == null ? "" : assignment.getAssignment().toString(),
                line(assignment.getPosition())
            ));
        }
        for (CtVariableWrite<?> write : executable.getElements(new TypeFilter<>(CtVariableWrite.class))) {
            events.add(new VariableEvent(methodSymbol, variableName(write), VariableEventKind.WRITE, write.toString(), line(write.getPosition())));
        }
        for (CtVariableRead<?> read : executable.getElements(new TypeFilter<>(CtVariableRead.class))) {
            events.add(new VariableEvent(methodSymbol, variableName(read), VariableEventKind.READ, read.toString(), line(read.getPosition())));
        }
        for (CtReturn<?> ctReturn : executable.getElements(new TypeFilter<>(CtReturn.class))) {
            events.add(new VariableEvent(
                methodSymbol,
                "",
                VariableEventKind.RETURN,
                ctReturn.getReturnedExpression() == null ? "" : ctReturn.getReturnedExpression().toString(),
                line(ctReturn.getPosition())
            ));
        }
        for (CtInvocation<?> invocation : executable.getElements(new TypeFilter<>(CtInvocation.class))) {
            addRequestInvocation(events, methodSymbol, invocation);
            addArgumentFlows(argumentFlows, symbols, methodSymbol, methodParameters, requestParameterByVariable, invocation);
        }
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
                        line(ctReturn.getPosition())
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
                        line(assignment.getPosition())
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
        events.add(new VariableEvent(methodSymbol, key, kind, invocation.toString(), line(invocation.getPosition())));
    }

    private void addArgumentFlows(
        List<MethodArgumentFlowEvent> argumentFlows,
        SpoonSymbolMapper symbols,
        SymbolId caller,
        Set<String> methodParameters,
        Map<String, String> requestParameterByVariable,
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
                    line(invocation.getPosition())
                ));
                continue;
            }
            String argumentName = variableName(argument);
            String requestParameterName = requestParameterByVariable.getOrDefault(argumentName, "");
            if (!requestParameterName.isBlank()) {
                argumentFlows.add(new MethodArgumentFlowEvent(
                    caller,
                    callee,
                    requestParameterName,
                    argumentName,
                    "request-parameter-local",
                    invocation.toString(),
                    line(invocation.getPosition())
                ));
                continue;
            }
            if (methodParameters.contains(argumentName)) {
                argumentFlows.add(new MethodArgumentFlowEvent(
                    caller,
                    callee,
                    "",
                    argumentName,
                    "method-parameter",
                    invocation.toString(),
                    line(invocation.getPosition())
                ));
            }
        }
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
        String name = method.getSimpleName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2) {
            return decapitalize(name.substring(2));
        }
        return "";
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
}
