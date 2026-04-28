package org.sainm.codeatlas.graph.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.junit.jupiter.api.Test;

class SymbolSearchIndexTest {
    @Test
    void searchesAcrossClassesMethodsAndLogicalArtifacts() {
        SymbolSearchIndex index = new SymbolSearchIndex();
        SymbolId clazz = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserService");
        SymbolId method = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
        SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
        index.add(GraphNodeFactory.classNode(clazz, NodeRole.SERVICE));
        index.add(GraphNodeFactory.methodNode(method, NodeRole.SERVICE));
        index.add(GraphNodeFactory.jspNode(jsp, NodeRole.JSP_ARTIFACT));

        assertEquals(method, index.search("save", 10).getFirst().symbolId());
        assertEquals(clazz, index.search("UserService", 10).getFirst().symbolId());
        assertEquals(jsp, index.search("edit.jsp", 10).getFirst().symbolId());
    }
}
