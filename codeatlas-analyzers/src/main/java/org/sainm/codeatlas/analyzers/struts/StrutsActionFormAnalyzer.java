package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.SpoonSymbolMapper;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

public final class StrutsActionFormAnalyzer {
    private static final Set<String> FORM_BASE_TYPES = Set.of(
        "org.apache.struts.action.ActionForm",
        "org.apache.struts.validator.ValidatorForm",
        "ActionForm",
        "ValidatorForm"
    );

    public StrutsActionFormAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), sourceRootKey);
        List<StrutsActionFormField> fields = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        for (CtType<?> type : model.getAllTypes()) {
            if (!isActionForm(type)) {
                continue;
            }
            SymbolId formClass = symbols.type(type);
            nodes.add(GraphNodeFactory.classNode(formClass, NodeRole.CODE_TYPE));
            for (CtField<?> field : type.getFields()) {
                if (field.hasModifier(ModifierKind.STATIC)) {
                    continue;
                }
                SymbolId fieldSymbol = symbols.field(field);
                SymbolId parameter = SymbolId.logicalPath(
                    SymbolKind.REQUEST_PARAMETER,
                    projectKey,
                    scope.moduleKey(),
                    "_request",
                    field.getSimpleName(),
                    null
                );
                nodes.add(GraphNodeFactory.fieldNode(fieldSymbol, NodeRole.CODE_MEMBER));
                nodes.add(GraphNodeFactory.requestParameterNode(parameter));
                fields.add(new StrutsActionFormField(
                    type.getQualifiedName(),
                    field.getSimpleName(),
                    field.getType() == null ? "_unknown" : field.getType().getQualifiedName(),
                    Confidence.LIKELY,
                    line(field.getPosition())
                ));
                facts.add(fact(scope, formClass, RelationType.DECLARES, fieldSymbol, field.getPosition(), "action-form-field", Confidence.CERTAIN));
                facts.add(fact(scope, parameter, RelationType.BINDS_TO, fieldSymbol, field.getPosition(), field.getSimpleName(), Confidence.LIKELY));
            }
        }

        return new StrutsActionFormAnalysisResult(fields, nodes, facts);
    }

    private boolean isActionForm(CtType<?> type) {
        CtTypeReference<?> superClass = type.getSuperclass();
        while (superClass != null) {
            String qualifiedName = superClass.getQualifiedName();
            String simpleName = superClass.getSimpleName();
            if (FORM_BASE_TYPES.contains(qualifiedName) || FORM_BASE_TYPES.contains(simpleName)) {
                return true;
            }
            superClass = superClass.getSuperclass();
        }
        return type.getQualifiedName().endsWith("Form");
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        SourcePosition position,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            evidence(position, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.SPOON
        );
    }

    private EvidenceKey evidence(SourcePosition position, String qualifier) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "struts-action-form", "_unknown", 0, 0, qualifier);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "struts-action-form",
            position.getFile().toPath().toString(),
            line(position),
            line(position),
            qualifier
        );
    }

    private int line(SourcePosition position) {
        return position == null || !position.isValidPosition() ? 0 : Math.max(0, position.getLine());
    }
}
