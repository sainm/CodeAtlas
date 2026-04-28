package org.sainm.codeatlas.graph.model;

import org.sainm.codeatlas.graph.project.ModuleDescriptor;
import org.sainm.codeatlas.graph.project.ProjectDescriptor;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class GraphNodeFactory {
    private GraphNodeFactory() {
    }

    public static GraphNode project(ProjectDescriptor project) {
        SymbolId symbolId = new SymbolId(SymbolKind.PROJECT, project.projectKey(), "_root", "_", null, null, null, null);
        return new GraphNode(
            symbolId,
            Set.of(NodeRole.PROJECT),
            Map.of(
                "projectId", project.projectId(),
                "displayName", project.displayName(),
                "rootPath", project.rootPath().toString()
            )
        );
    }

    public static GraphNode module(ProjectDescriptor project, ModuleDescriptor module) {
        SymbolId symbolId = new SymbolId(SymbolKind.MODULE, project.projectKey(), module.moduleKey(), "_", null, null, null, null);
        return new GraphNode(
            symbolId,
            Set.of(NodeRole.MODULE),
            Map.of(
                "projectId", module.projectId(),
                "moduleKey", module.moduleKey(),
                "basePath", module.basePath().toString(),
                "buildSystem", module.buildSystem().name()
            )
        );
    }

    public static GraphNode sourceFile(String projectKey, String moduleKey, String sourceRootKey, Path relativePath) {
        SymbolId symbolId = SymbolId.logicalPath(
            SymbolKind.SOURCE_FILE,
            projectKey,
            moduleKey,
            sourceRootKey,
            relativePath.toString(),
            null
        );
        return new GraphNode(symbolId, Set.of(NodeRole.SOURCE_FILE), Map.of("path", relativePath.toString().replace('\\', '/')));
    }

    public static GraphNode classNode(SymbolId classSymbol, NodeRole role) {
        if (classSymbol.kind() != SymbolKind.CLASS
            && classSymbol.kind() != SymbolKind.INTERFACE
            && classSymbol.kind() != SymbolKind.ENUM
            && classSymbol.kind() != SymbolKind.ANNOTATION) {
            throw new IllegalArgumentException("classSymbol must be CLASS, INTERFACE, ENUM or ANNOTATION");
        }
        return new GraphNode(classSymbol, roles(NodeRole.CODE_TYPE, role), Map.of());
    }

    public static GraphNode methodNode(SymbolId methodSymbol, NodeRole role) {
        if (methodSymbol.kind() != SymbolKind.METHOD) {
            throw new IllegalArgumentException("methodSymbol must be METHOD");
        }
        return new GraphNode(methodSymbol, roles(NodeRole.CODE_MEMBER, role), Map.of());
    }

    public static GraphNode fieldNode(SymbolId fieldSymbol, NodeRole role) {
        if (fieldSymbol.kind() != SymbolKind.FIELD) {
            throw new IllegalArgumentException("fieldSymbol must be FIELD");
        }
        return new GraphNode(fieldSymbol, roles(NodeRole.CODE_MEMBER, role), Map.of());
    }

    public static GraphNode jspNode(SymbolId jspSymbol, NodeRole role) {
        if (jspSymbol.kind() != SymbolKind.JSP_PAGE
            && jspSymbol.kind() != SymbolKind.JSP_FORM
            && jspSymbol.kind() != SymbolKind.JSP_INPUT) {
            throw new IllegalArgumentException("jspSymbol must be a JSP symbol");
        }
        return new GraphNode(jspSymbol, roles(NodeRole.JSP_ARTIFACT, role), Map.of());
    }

    public static GraphNode requestParameterNode(SymbolId parameterSymbol) {
        if (parameterSymbol.kind() != SymbolKind.REQUEST_PARAMETER) {
            throw new IllegalArgumentException("parameterSymbol must be REQUEST_PARAMETER");
        }
        return new GraphNode(parameterSymbol, Set.of(NodeRole.REQUEST_PARAMETER), Map.of());
    }

    public static GraphNode actionPathNode(SymbolId actionPathSymbol) {
        if (actionPathSymbol.kind() != SymbolKind.ACTION_PATH) {
            throw new IllegalArgumentException("actionPathSymbol must be ACTION_PATH");
        }
        return new GraphNode(actionPathSymbol, Set.of(NodeRole.WEB_ENTRYPOINT, NodeRole.STRUTS_ACTION), Map.of());
    }

    public static GraphNode apiEndpointNode(SymbolId endpointSymbol) {
        if (endpointSymbol.kind() != SymbolKind.API_ENDPOINT) {
            throw new IllegalArgumentException("endpointSymbol must be API_ENDPOINT");
        }
        return new GraphNode(endpointSymbol, Set.of(NodeRole.WEB_ENTRYPOINT, NodeRole.SPRING_HANDLER), Map.of());
    }

    public static GraphNode sqlNode(SymbolId sqlSymbol) {
        if (sqlSymbol.kind() != SymbolKind.SQL_STATEMENT) {
            throw new IllegalArgumentException("sqlSymbol must be SQL_STATEMENT");
        }
        return new GraphNode(sqlSymbol, Set.of(NodeRole.SQL_ARTIFACT), Map.of());
    }

    public static GraphNode tableNode(SymbolId tableSymbol) {
        if (tableSymbol.kind() != SymbolKind.DB_TABLE && tableSymbol.kind() != SymbolKind.DB_COLUMN) {
            throw new IllegalArgumentException("tableSymbol must be DB_TABLE or DB_COLUMN");
        }
        return new GraphNode(tableSymbol, Set.of(NodeRole.DATABASE_OBJECT), Map.of());
    }

    public static GraphNode configNode(SymbolId configSymbol) {
        if (configSymbol.kind() != SymbolKind.CONFIG_KEY) {
            throw new IllegalArgumentException("configSymbol must be CONFIG_KEY");
        }
        return new GraphNode(configSymbol, Set.of(NodeRole.CONFIG_ARTIFACT), Map.of());
    }

    private static Set<NodeRole> roles(NodeRole primary, NodeRole secondary) {
        TreeSet<NodeRole> roles = new TreeSet<>();
        roles.add(primary);
        roles.add(secondary);
        return roles;
    }
}
