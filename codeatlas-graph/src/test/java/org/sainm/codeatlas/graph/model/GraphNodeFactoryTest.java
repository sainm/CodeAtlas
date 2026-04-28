package org.sainm.codeatlas.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.sainm.codeatlas.graph.project.BuildSystem;
import org.sainm.codeatlas.graph.project.ModuleDescriptor;
import org.sainm.codeatlas.graph.project.ProjectDescriptor;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphNodeFactoryTest {
    @Test
    void createsProjectAndModuleNodesWithStableSymbols() {
        ProjectDescriptor project = new ProjectDescriptor("p1", "shop", "Shop", Path.of("D:/work/shop"));
        ModuleDescriptor module = new ModuleDescriptor("p1", ":order", Path.of("D:/work/shop/order"), BuildSystem.GRADLE);

        GraphNode projectNode = GraphNodeFactory.project(project);
        GraphNode moduleNode = GraphNodeFactory.module(project, module);

        assertEquals("project://shop/_root/_", projectNode.symbolId().value());
        assertEquals(Set.of(NodeRole.PROJECT), projectNode.roles());
        assertEquals("module://shop/:order/_", moduleNode.symbolId().value());
        assertEquals("GRADLE", moduleNode.properties().get("buildSystem"));
    }

    @Test
    void classRolesDoNotCreateControllerDuplicateNodes() {
        SymbolId actionClass = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserAction");
        GraphNode node = GraphNodeFactory.classNode(actionClass, NodeRole.STRUTS_ACTION);

        assertEquals(Set.of(NodeRole.CODE_TYPE, NodeRole.STRUTS_ACTION), node.roles());
        assertEquals(actionClass, node.symbolId());
    }

    @Test
    void rejectsWrongSymbolKindForSqlNode() {
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.Mapper", "update", "()V");
        assertThrows(IllegalArgumentException.class, () -> GraphNodeFactory.sqlNode(method));
    }

    @Test
    void createsMethodAndFieldAsCodeMembers() {
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        SymbolId field = SymbolId.field("shop", "_root", "src/main/java", "com.acme.UserService", "repository", "Lcom/acme/UserRepository;");

        assertEquals(Set.of(NodeRole.CODE_MEMBER, NodeRole.SERVICE), GraphNodeFactory.methodNode(method, NodeRole.SERVICE).roles());
        assertEquals(Set.of(NodeRole.CODE_MEMBER, NodeRole.DAO), GraphNodeFactory.fieldNode(field, NodeRole.DAO).roles());
    }
}
