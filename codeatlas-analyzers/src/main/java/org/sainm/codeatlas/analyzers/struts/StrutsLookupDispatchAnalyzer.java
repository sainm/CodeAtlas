package org.sainm.codeatlas.analyzers.struts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

public final class StrutsLookupDispatchAnalyzer {
    public List<StrutsLookupDispatchMethodMapping> analyze(List<Path> sourceFiles) {
        if (sourceFiles.isEmpty()) {
            return List.of();
        }
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        List<StrutsLookupDispatchMethodMapping> mappings = new ArrayList<>();
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            if (!"getKeyMethodMap".equals(method.getSimpleName())) {
                continue;
            }
            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType == null || declaringType.getQualifiedName() == null) {
                continue;
            }
            for (CtInvocation<?> invocation : method.getElements(new TypeFilter<>(CtInvocation.class))) {
                addPutMapping(declaringType.getQualifiedName(), invocation, mappings);
            }
        }
        return mappings;
    }

    private void addPutMapping(String actionType, CtInvocation<?> invocation, List<StrutsLookupDispatchMethodMapping> mappings) {
        if (invocation.getExecutable() == null || !"put".equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        if (invocation.getArguments().size() < 2) {
            return;
        }
        String resourceKey = stringLiteral(invocation.getArguments().get(0));
        String methodName = stringLiteral(invocation.getArguments().get(1));
        if (!resourceKey.isBlank() && !methodName.isBlank()) {
            mappings.add(new StrutsLookupDispatchMethodMapping(actionType, resourceKey, methodName));
        }
    }

    private String stringLiteral(Object expression) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String text) {
            return text.trim();
        }
        return "";
    }
}
