package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class VariableTraceAnalyzer {
    private VariableTraceAnalyzer() {
    }

    public static VariableTraceAnalyzer defaults() {
        return new VariableTraceAnalyzer();
    }

    public VariableTraceAnalysisResult analyze(Path sourceRoot, List<Path> sourceFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return new VariableTraceAnalysisResult(List.of(), List.of(), List.of());
        }
        try {
            return extract(sourceRoot, buildModel(sourceFiles));
        } catch (RuntimeException exception) {
            return new VariableTraceAnalysisResult(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new JavaAnalysisDiagnostic("VARIABLE_TRACE_ANALYSIS_FAILED", exception.getMessage())));
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

    private static VariableTraceAnalysisResult extract(Path sourceRoot, CtModel model) {
        List<MethodLocalDefUseInfo> defUses = new ArrayList<>();
        List<MethodLocalUseInfo> uses = new ArrayList<>();
        List<RequestValueAccessInfo> requestAccesses = new ArrayList<>();
        List<FormPropertyReadInfo> formPropertyReads = new ArrayList<>();
        List<BeanPropertyFlowInfo> beanPropertyFlows = new ArrayList<>();
        List<RequestDerivedArgumentInfo> requestDerivedArguments = new ArrayList<>();
        List<ParameterDerivedArgumentInfo> parameterDerivedArguments = new ArrayList<>();
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            analyzeBody(sourceRoot, method, method.getParent(CtType.class), method, defUses, uses, requestAccesses, formPropertyReads, beanPropertyFlows, requestDerivedArguments, parameterDerivedArguments);
        }
        for (CtConstructor<?> constructor : model.getElements(new TypeFilter<>(CtConstructor.class))) {
            analyzeBody(sourceRoot, constructor, constructor.getParent(CtType.class), constructor, defUses, uses, requestAccesses, formPropertyReads, beanPropertyFlows, requestDerivedArguments, parameterDerivedArguments);
        }
        return new VariableTraceAnalysisResult(defUses, uses, requestAccesses, formPropertyReads, beanPropertyFlows, requestDerivedArguments, parameterDerivedArguments, List.of());
    }

    private static void analyzeBody(
            Path sourceRoot,
            CtElement bodyOwner,
            CtType<?> ownerType,
            CtElement executable,
            List<MethodLocalDefUseInfo> defUses,
            List<MethodLocalUseInfo> uses,
            List<RequestValueAccessInfo> requestAccesses,
            List<FormPropertyReadInfo> formPropertyReads,
            List<BeanPropertyFlowInfo> beanPropertyFlows,
            List<RequestDerivedArgumentInfo> requestDerivedArguments,
            List<ParameterDerivedArgumentInfo> parameterDerivedArguments) {
        if (ownerType == null || ownerType.isShadow()) {
            return;
        }
        String methodName = executable instanceof CtMethod<?> method ? method.getSimpleName() : "<init>";
        String signature = executable instanceof CtMethod<?> method
                ? JavaDescriptor.methodDescriptor(method.getParameters().stream()
                        .map(parameter -> parameter.getType())
                        .toList(), method.getType())
                : JavaDescriptor.methodDescriptor(((CtConstructor<?>) executable).getParameters().stream()
                        .map(parameter -> parameter.getType())
                        .toList(), null);
        MethodContext context = new MethodContext(ownerType.getQualifiedName(), methodName, signature);
        Map<String, String> aliases = new LinkedHashMap<>();
        Map<String, BeanPropertyRead> propertyAliases = new LinkedHashMap<>();
        Map<String, String> requestParameterAliases = new LinkedHashMap<>();
        Map<String, String> variableTypes = variableTypes(bodyOwner);
        Map<String, ParameterSource> parameterAliases = parameterAliases(executable);
        for (CtElement element : orderedTraceElements(bodyOwner)) {
            if (element instanceof CtLocalVariable<?> localVariable) {
                variableTypes.put(localVariable.getSimpleName(), typeName(localVariable.getType()));
                refreshBeanPropertyAlias(propertyAliases, localVariable.getSimpleName(), localVariable.getDefaultExpression());
                refreshRequestParameterAlias(requestParameterAliases, localVariable.getSimpleName(), localVariable.getDefaultExpression());
                handleDefinition(sourceRoot, context, aliases, localVariable.getSimpleName(),
                        localVariable.getDefaultExpression(), localVariable, defUses);
                refreshParameterAlias(parameterAliases, localVariable.getSimpleName(), localVariable.getDefaultExpression());
            } else if (element instanceof CtAssignment<?, ?> assignment
                    && assignment.getAssigned() instanceof CtVariableWrite<?> target) {
                refreshBeanPropertyAlias(propertyAliases, target.getVariable().getSimpleName(), assignment.getAssignment());
                refreshRequestParameterAlias(requestParameterAliases, target.getVariable().getSimpleName(), assignment.getAssignment());
                refreshParameterAlias(parameterAliases, target.getVariable().getSimpleName(), assignment.getAssignment());
                handleDefinition(sourceRoot, context, aliases, target.getVariable().getSimpleName(),
                        assignment.getAssignment(), assignment, defUses);
            } else if (element instanceof CtInvocation<?> invocation) {
                handleRequestAccess(sourceRoot, context, aliases, invocation, requestAccesses);
                handleFormPropertyRead(sourceRoot, context, variableTypes, invocation, formPropertyReads);
                handleBeanSetterFlow(sourceRoot, context, propertyAliases, invocation, beanPropertyFlows);
                handleRequestDerivedArguments(sourceRoot, context, requestParameterAliases, invocation, requestDerivedArguments);
                handleParameterDerivedArguments(sourceRoot, context, parameterAliases, invocation, parameterDerivedArguments);
                for (CtExpression<?> argument : invocation.getArguments()) {
                    for (String variableName : variableReads(argument)) {
                        uses.add(new MethodLocalUseInfo(
                                context.ownerQualifiedName(),
                                context.methodName(),
                                context.signature(),
                                variableName,
                                aliases.getOrDefault(variableName, variableName),
                                "argument",
                                line(invocation),
                                location(sourceRoot, invocation.getPosition())));
                    }
                }
            }
        }
    }

    private static Map<String, ParameterSource> parameterAliases(CtElement executable) {
        Map<String, ParameterSource> result = new LinkedHashMap<>();
        List<CtParameter<?>> parameters = executable instanceof CtMethod<?> method
                ? method.getParameters()
                : ((CtConstructor<?>) executable).getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            CtParameter<?> parameter = parameters.get(i);
            result.put(parameter.getSimpleName(), new ParameterSource(i, JavaDescriptor.typeDescriptor(parameter.getType())));
        }
        return result;
    }

    private static void refreshParameterAlias(
            Map<String, ParameterSource> parameterAliases,
            String targetVariableName,
            CtExpression<?> expression) {
        List<String> reads = variableReads(expression);
        if (!(expression instanceof CtVariableRead<?>) || reads.size() != 1 || !parameterAliases.containsKey(reads.getFirst())) {
            parameterAliases.remove(targetVariableName);
            return;
        }
        parameterAliases.put(targetVariableName, parameterAliases.get(reads.getFirst()));
    }

    private static void handleParameterDerivedArguments(
            Path sourceRoot,
            MethodContext context,
            Map<String, ParameterSource> parameterAliases,
            CtInvocation<?> invocation,
            List<ParameterDerivedArgumentInfo> parameterDerivedArguments) {
        String targetQualifiedName = targetQualifiedName(invocation);
        String targetMethodName = invocation.getExecutable().getSimpleName();
        if (targetQualifiedName.isBlank() || targetMethodName.isBlank()) {
            return;
        }
        List<CtExpression<?>> arguments = invocation.getArguments();
        for (int index = 0; index < arguments.size(); index++) {
            List<String> reads = variableReads(arguments.get(index));
            if (reads.isEmpty()) {
                continue;
            }
            String variableName = reads.getFirst();
            ParameterSource source = parameterAliases.get(variableName);
            if (source == null) {
                continue;
            }
            parameterDerivedArguments.add(new ParameterDerivedArgumentInfo(
                    context.ownerQualifiedName(),
                    context.methodName(),
                    context.signature(),
                    source.index(),
                    source.descriptor(),
                    targetQualifiedName,
                    targetMethodName,
                    JavaDescriptor.methodDescriptor(invocation.getExecutable().getParameters(), invocation.getExecutable().getType()),
                    index,
                    argumentDescriptor(invocation, index),
                    variableName,
                    location(sourceRoot, invocation.getPosition())));
        }
    }

    private static void refreshRequestParameterAlias(
            Map<String, String> requestParameterAliases,
            String variableName,
            CtExpression<?> expression) {
        if (expression instanceof CtInvocation<?> invocation
                && invocation.getExecutable().getSimpleName().equals("getParameter")
                && targetSimpleName(invocation).equals("request")
                && !invocation.getArguments().isEmpty()) {
            String parameterName = stringLiteral(invocation.getArguments().getFirst());
            if (!parameterName.isBlank()) {
                requestParameterAliases.put(variableName, parameterName);
                return;
            }
        }
        List<String> reads = variableReads(expression);
        if (expression instanceof CtVariableRead<?> && reads.size() == 1 && requestParameterAliases.containsKey(reads.getFirst())) {
            requestParameterAliases.put(variableName, requestParameterAliases.get(reads.getFirst()));
            return;
        }
        requestParameterAliases.remove(variableName);
    }

    private static void handleRequestDerivedArguments(
            Path sourceRoot,
            MethodContext context,
            Map<String, String> requestParameterAliases,
            CtInvocation<?> invocation,
            List<RequestDerivedArgumentInfo> requestDerivedArguments) {
        String targetQualifiedName = targetQualifiedName(invocation);
        String targetMethodName = invocation.getExecutable().getSimpleName();
        if (targetQualifiedName.isBlank() || targetMethodName.isBlank()) {
            return;
        }
        List<CtExpression<?>> arguments = invocation.getArguments();
        for (int index = 0; index < arguments.size(); index++) {
            List<String> reads = variableReads(arguments.get(index));
            if (reads.isEmpty()) {
                continue;
            }
            String variableName = reads.getFirst();
            String requestParameterName = requestParameterAliases.get(variableName);
            if (requestParameterName == null || requestParameterName.isBlank()) {
                continue;
            }
            requestDerivedArguments.add(new RequestDerivedArgumentInfo(
                    context.ownerQualifiedName(),
                    context.methodName(),
                    context.signature(),
                    targetQualifiedName,
                    targetMethodName,
                    JavaDescriptor.methodDescriptor(invocation.getExecutable().getParameters(), invocation.getExecutable().getType()),
                    index,
                    argumentDescriptor(invocation, index),
                    requestParameterName,
                    variableName,
                    location(sourceRoot, invocation.getPosition())));
        }
    }

    private static void refreshBeanPropertyAlias(
            Map<String, BeanPropertyRead> propertyAliases,
            String variableName,
            CtExpression<?> expression) {
        if (expression instanceof CtInvocation<?> invocation) {
            String propertyName = getterPropertyName(invocation);
            String sourceObject = targetSimpleName(invocation);
            if (!propertyName.isBlank() && !sourceObject.isBlank()) {
                propertyAliases.put(variableName, new BeanPropertyRead(sourceObject, propertyName));
                return;
            }
        }
        List<String> reads = variableReads(expression);
        if (expression instanceof CtVariableRead<?> && reads.size() == 1 && propertyAliases.containsKey(reads.getFirst())) {
            propertyAliases.put(variableName, propertyAliases.get(reads.getFirst()));
            return;
        }
        propertyAliases.remove(variableName);
    }

    private static void handleBeanSetterFlow(
            Path sourceRoot,
            MethodContext context,
            Map<String, BeanPropertyRead> propertyAliases,
            CtInvocation<?> invocation,
            List<BeanPropertyFlowInfo> beanPropertyFlows) {
        String targetProperty = setterPropertyName(invocation);
        String targetObject = targetSimpleName(invocation);
        if (targetProperty.isBlank() || targetObject.isBlank() || invocation.getArguments().isEmpty()) {
            return;
        }
        List<String> valueReads = variableReads(invocation.getArguments().getFirst());
        if (valueReads.isEmpty()) {
            return;
        }
        String valueVariable = valueReads.getFirst();
        BeanPropertyRead source = propertyAliases.get(valueVariable);
        if (source == null) {
            return;
        }
        beanPropertyFlows.add(new BeanPropertyFlowInfo(
                context.ownerQualifiedName(),
                context.methodName(),
                context.signature(),
                source.objectName(),
                source.propertyName(),
                targetObject,
                targetProperty,
                valueVariable,
                line(invocation),
                location(sourceRoot, invocation.getPosition())));
    }

    private static void handleFormPropertyRead(
            Path sourceRoot,
            MethodContext context,
            Map<String, String> variableTypes,
            CtInvocation<?> invocation,
            List<FormPropertyReadInfo> formPropertyReads) {
        String targetName = targetSimpleName(invocation);
        if (targetName.isBlank()) {
            return;
        }
        String typeName = variableTypes.getOrDefault(targetName, targetTypeName(invocation));
        if (!isActionFormType(typeName)) {
            return;
        }
        String propertyName = formPropertyName(invocation);
        if (propertyName.isBlank()) {
            return;
        }
        formPropertyReads.add(new FormPropertyReadInfo(
                context.ownerQualifiedName(),
                context.methodName(),
                context.signature(),
                targetName,
                typeName,
                propertyName,
                line(invocation),
                location(sourceRoot, invocation.getPosition())));
    }

    private static void handleRequestAccess(
            Path sourceRoot,
            MethodContext context,
            Map<String, String> aliases,
            CtInvocation<?> invocation,
            List<RequestValueAccessInfo> requestAccesses) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (!methodName.equals("getParameter") && !methodName.equals("getAttribute") && !methodName.equals("setAttribute")) {
            return;
        }
        if (!targetSimpleName(invocation).equals("request") || invocation.getArguments().isEmpty()) {
            return;
        }
        String name = stringLiteral(invocation.getArguments().getFirst());
        if (name.isBlank()) {
            return;
        }
        String valueVariable = "";
        String resolvedSource = "";
        if (methodName.equals("setAttribute") && invocation.getArguments().size() >= 2) {
            List<String> valueReads = variableReads(invocation.getArguments().get(1));
            if (!valueReads.isEmpty()) {
                valueVariable = valueReads.getFirst();
                resolvedSource = aliases.getOrDefault(valueVariable, valueVariable);
            }
        }
        requestAccesses.add(new RequestValueAccessInfo(
                context.ownerQualifiedName(),
                context.methodName(),
                context.signature(),
                methodName,
                name,
                valueVariable,
                resolvedSource,
                line(invocation),
                location(sourceRoot, invocation.getPosition())));
    }

    private static void handleDefinition(
            Path sourceRoot,
            MethodContext context,
            Map<String, String> aliases,
            String targetVariableName,
            CtExpression<?> expression,
            CtElement element,
            List<MethodLocalDefUseInfo> defUses) {
        List<String> reads = variableReads(expression);
        if (expression instanceof CtVariableRead<?> && reads.size() == 1) {
            aliases.put(targetVariableName, aliases.getOrDefault(reads.getFirst(), reads.getFirst()));
        } else {
            aliases.put(targetVariableName, targetVariableName);
        }
        for (String sourceVariableName : reads) {
            defUses.add(new MethodLocalDefUseInfo(
                    context.ownerQualifiedName(),
                    context.methodName(),
                    context.signature(),
                    sourceVariableName,
                    targetVariableName,
                    aliases.getOrDefault(sourceVariableName, sourceVariableName),
                    line(element),
                    location(sourceRoot, element.getPosition())));
        }
    }

    private static List<CtElement> orderedTraceElements(CtElement bodyOwner) {
        List<CtElement> result = new ArrayList<>();
        result.addAll(bodyOwner.getElements(new TypeFilter<>(CtLocalVariable.class)));
        result.addAll(bodyOwner.getElements(new TypeFilter<>(CtAssignment.class)));
        result.addAll(bodyOwner.getElements(new TypeFilter<>(CtInvocation.class)));
        return result.stream()
                .filter(element -> element.getPosition() != null && element.getPosition().isValidPosition())
                .sorted(Comparator.comparingInt(VariableTraceAnalyzer::line)
                        .thenComparingInt(element -> element.getPosition().getColumn()))
                .toList();
    }

    private static List<String> variableReads(CtExpression<?> expression) {
        if (expression == null) {
            return List.of();
        }
        return expression.getElements(new TypeFilter<>(CtVariableRead.class)).stream()
                .map(CtVariableRead::getVariable)
                .map(CtVariableReference::getSimpleName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private static String targetSimpleName(CtInvocation<?> invocation) {
        if (!(invocation instanceof CtTargetedExpression<?, ?> targeted) || targeted.getTarget() == null) {
            return "";
        }
        if (targeted.getTarget() instanceof CtVariableRead<?> read && read.getVariable() != null) {
            return read.getVariable().getSimpleName();
        }
        return targeted.getTarget().toString();
    }

    private static String targetTypeName(CtInvocation<?> invocation) {
        if (!(invocation instanceof CtTargetedExpression<?, ?> targeted) || targeted.getTarget() == null) {
            return "";
        }
        return typeName(targeted.getTarget().getType());
    }

    private static String targetQualifiedName(CtInvocation<?> invocation) {
        String declaringType = typeName(invocation.getExecutable().getDeclaringType());
        if (!declaringType.isBlank()) {
            return declaringType;
        }
        if (!(invocation instanceof CtTargetedExpression<?, ?> targeted) || targeted.getTarget() == null) {
            return "";
        }
        return typeName(targeted.getTarget().getType());
    }

    private static String argumentDescriptor(CtInvocation<?> invocation, int index) {
        if (invocation.getExecutable().getParameters().size() > index) {
            return JavaDescriptor.typeDescriptor(invocation.getExecutable().getParameters().get(index));
        }
        return "Ljava/lang/Object;";
    }

    private static String formPropertyName(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (methodName.equals("get") && !invocation.getArguments().isEmpty()) {
            return stringLiteral(invocation.getArguments().getFirst());
        }
        if (methodName.startsWith("get") && methodName.length() > 3 && invocation.getArguments().isEmpty()) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && invocation.getArguments().isEmpty()) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return "";
    }

    private static String getterPropertyName(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (methodName.startsWith("get") && methodName.length() > 3 && invocation.getArguments().isEmpty()) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && invocation.getArguments().isEmpty()) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return "";
    }

    private static String setterPropertyName(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if (methodName.startsWith("set") && methodName.length() > 3 && invocation.getArguments().size() == 1) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        return "";
    }

    private static boolean isActionFormType(String typeName) {
        return typeName.endsWith("ActionForm")
                || typeName.endsWith("DynaActionForm")
                || typeName.equals("org.apache.struts.action.ActionForm")
                || typeName.equals("org.apache.struts.action.DynaActionForm");
    }

    private static Map<String, String> variableTypes(CtElement bodyOwner) {
        Map<String, String> result = new LinkedHashMap<>();
        for (CtParameter<?> parameter : bodyOwner.getElements(new TypeFilter<>(CtParameter.class))) {
            result.put(parameter.getSimpleName(), typeName(parameter.getType()));
        }
        return result;
    }

    private static String stringLiteral(CtExpression<?> expression) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String value) {
            return value;
        }
        return "";
    }

    private static String typeName(CtTypeReference<?> type) {
        return type == null ? "" : type.getQualifiedName();
    }

    private static int line(CtElement element) {
        SourcePosition position = element.getPosition();
        return position == null || !position.isValidPosition() ? 0 : position.getLine();
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

    private record MethodContext(String ownerQualifiedName, String methodName, String signature) {
    }

    private record BeanPropertyRead(String objectName, String propertyName) {
    }

    private record ParameterSource(int index, String descriptor) {
    }
}
