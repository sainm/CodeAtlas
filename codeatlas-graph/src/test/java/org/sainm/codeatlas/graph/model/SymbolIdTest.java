package org.sainm.codeatlas.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SymbolIdTest {
    @Test
    void methodSymbolUsesJvmDescriptorForOverloadIdentity() {
        SymbolId symbolId = SymbolId.method(
            "shop",
            ":order",
            "src/main/java",
            "com.acme.OrderService",
            "cancel",
            "(Ljava/lang/Long;)V"
        );

        assertEquals(
            "method://shop/:order/src/main/java/com.acme.OrderService#cancel(Ljava/lang/Long;)V",
            symbolId.value()
        );
    }

    @Test
    void logicalPathNormalizesWindowsSeparators() {
        SymbolId symbolId = SymbolId.logicalPath(
            SymbolKind.JSP_INPUT,
            "shop",
            "_root",
            "src\\main\\webapp",
            "\\WEB-INF\\jsp\\order\\edit.jsp",
            "input[name=orderId]"
        );

        assertEquals(
            "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp#input[name=orderId]",
            symbolId.value()
        );
    }

    @Test
    void projectKeyIsRequired() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SymbolId.classSymbol("", "_root", "src/main/java", "com.acme.OrderService")
        );
    }
}

