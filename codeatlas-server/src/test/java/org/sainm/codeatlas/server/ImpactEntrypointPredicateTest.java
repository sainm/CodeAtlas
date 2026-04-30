package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.junit.jupiter.api.Test;

class ImpactEntrypointPredicateTest {
    private final ImpactEntrypointPredicate predicate = new ImpactEntrypointPredicate();

    @Test
    void treatsWebEntrypointsAsEntrypoints() {
        assertTrue(predicate.test(SymbolId.logicalPath(SymbolKind.API_ENDPOINT, "shop", "_root", "src/main/java", "GET /users", null)));
        assertTrue(predicate.test(SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null)));
        assertTrue(predicate.test(SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null)));
    }

    @Test
    void rejectsMethodsWhoseEntrypointRoleRequiresGraphEvidence() {
        assertFalse(predicate.test(SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserController", "save", "()V")));
        assertFalse(predicate.test(SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V")));
    }

    @Test
    void rejectsInternalServiceMethod() {
        assertFalse(predicate.test(SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V")));
    }
}
