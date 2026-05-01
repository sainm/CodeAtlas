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
    void parserRoundTripsCanonicalMethodSymbol() {
        String value = "method://shop/:order/src/main/java/com.acme.OrderService#cancel(Ljava/lang/Long;)V";

        SymbolId parsed = SymbolIdParser.parse(value);

        assertEquals(SymbolKind.METHOD, parsed.kind());
        assertEquals("shop", parsed.projectKey());
        assertEquals(":order", parsed.moduleKey());
        assertEquals("src/main/java", parsed.sourceRootKey());
        assertEquals("com.acme.OrderService", parsed.ownerQualifiedName());
        assertEquals("cancel", parsed.memberName());
        assertEquals("(Ljava/lang/Long;)V", parsed.descriptor());
        assertEquals(value, parsed.value());
    }

    @Test
    void fieldSymbolSeparatesJvmTypeDescriptorWithColon() {
        SymbolId symbolId = SymbolId.field(
            "shop",
            "_root",
            "src/main/java",
            "com/acme/UserForm",
            "userId",
            "Ljava/lang/String;"
        );

        assertEquals(
            "field://shop/_root/src/main/java/com.acme.UserForm#userId:Ljava/lang/String;",
            symbolId.value()
        );
        assertEquals(symbolId, SymbolIdParser.parse(symbolId.value()));
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
    void logicalPathNormalizesDotSegmentsAndEscapesUnsafeCharacters() {
        SymbolId symbolId = SymbolId.logicalPath(
            SymbolKind.JSP_TAG,
            "shop",
            "_root",
            "src/main/webapp",
            "/WEB-INF/./jsp/../jsp/订单 编辑.jsp",
            "tag[html:text:12:0]#part"
        );

        assertEquals(
            "jsp-tag://shop/_root/src/main/webapp/WEB-INF/jsp/%E8%AE%A2%E5%8D%95%20%E7%BC%96%E8%BE%91.jsp#tag[html:text:12:0]%23part",
            symbolId.value()
        );
        assertEquals("tag[html:text:12:0]#part", SymbolIdParser.parse(symbolId.value()).localId());
        assertEquals(symbolId.value(), SymbolIdParser.parse(symbolId.value()).value());
    }

    @Test
    void supportsReportAndNativeSymbolKinds() {
        SymbolId reportField = SymbolId.logicalPath(
            SymbolKind.REPORT_FIELD,
            "shop",
            "_root",
            "src/main/resources",
            "reports/order.pmd",
            "amount"
        );
        SymbolId nativeLibrary = SymbolId.logicalPath(
            SymbolKind.NATIVE_LIBRARY,
            "shop",
            "_root",
            "WEB-INF/lib",
            "fjoajif.jar!/libfjoajif.so",
            null
        );

        assertEquals("report-field://shop/_root/src/main/resources/reports/order.pmd#amount", reportField.value());
        assertEquals("native-library://shop/_root/WEB-INF/lib/fjoajif.jar!/libfjoajif.so", nativeLibrary.value());
        assertEquals(reportField, SymbolIdParser.parse(reportField.value()));
        assertEquals(nativeLibrary, SymbolIdParser.parse(nativeLibrary.value()));
    }

    @Test
    void methodDescriptorIsRequiredForCanonicalMethodFacts() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", null)
        );

        assertEquals("method descriptor is required", error.getMessage());
    }

    @Test
    void projectKeyIsRequired() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SymbolId.classSymbol("", "_root", "src/main/java", "com.acme.OrderService")
        );
    }
}
