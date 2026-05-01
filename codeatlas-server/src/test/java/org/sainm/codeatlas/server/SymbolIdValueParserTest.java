package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.junit.jupiter.api.Test;

class SymbolIdValueParserTest {
    private final SymbolIdValueParser parser = new SymbolIdValueParser();

    @Test
    void parsesMethodValueWithMultiSegmentSourceRoot() {
        SymbolId original = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "(Ljava/lang/String;)V");

        SymbolId parsed = parser.parse(original.value());

        assertEquals(original, parsed);
        assertEquals("src/main/java", parsed.sourceRootKey());
        assertEquals("com.acme.UserService", parsed.ownerQualifiedName());
    }

    @Test
    void parsesJspPageAndInputValues() {
        SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "WEB-INF/jsp/user/edit.jsp", null);
        SymbolId input = SymbolId.logicalPath(SymbolKind.JSP_INPUT, "shop", "_root", "src/main/webapp", "WEB-INF/jsp/user/edit.jsp", "form:0:input:userId");

        assertEquals(jsp, parser.parse(jsp.value()));
        assertEquals(input, parser.parse(input.value()));
    }

    @Test
    void parsesRequestParameterAndActionPathValues() {
        SymbolId parameter = SymbolId.logicalPath(SymbolKind.REQUEST_PARAMETER, "shop", "_root", "_request", "userId", null);
        SymbolId action = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "admin/user/list", null);

        assertEquals(parameter, parser.parse(parameter.value()));
        assertEquals(action, parser.parse(action.value()));
    }
}
