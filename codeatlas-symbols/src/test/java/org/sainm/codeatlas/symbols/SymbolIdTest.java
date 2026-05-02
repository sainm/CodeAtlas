package org.sainm.codeatlas.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class SymbolIdTest {
    @Test
    void registryClassifiesSymbolFlowAndArtifactKinds() {
        SymbolKindRegistry registry = SymbolKindRegistry.defaults();

        assertSame(IdentityType.SYMBOL_ID, registry.require(DefaultSymbolKind.METHOD.kind()).identityType());
        assertSame(IdentityType.SYMBOL_ID, registry.require(DefaultSymbolKind.JSP_PAGE.kind()).identityType());
        assertSame(IdentityType.SYMBOL_ID, registry.require(DefaultSymbolKind.DATASOURCE.kind()).identityType());
        assertSame(IdentityType.SYMBOL_ID, registry.require(DefaultSymbolKind.DB_VIEW.kind()).identityType());
        assertSame(IdentityType.FLOW_ID, registry.require(DefaultSymbolKind.PARAM_SLOT.kind()).identityType());
        assertSame(IdentityType.ARTIFACT_ID, registry.require(DefaultSymbolKind.FEATURE_SEED.kind()).identityType());
        for (DefaultSymbolKind defaultKind : DefaultSymbolKind.values()) {
            assertSame(defaultKind.identityType(), registry.require(defaultKind.kind()).identityType());
        }
    }

    @Test
    void parsesAndRoundTripsJavaMethodSymbolId() {
        SymbolId symbolId = SymbolIdParser.parse(
                "method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V");

        assertEquals(DefaultSymbolKind.METHOD.kind(), symbolId.kind().kind());
        assertEquals(IdentityType.SYMBOL_ID, symbolId.identityType());
        assertEquals("shop", symbolId.projectKey());
        assertEquals("_root", symbolId.moduleKey());
        assertEquals("src/main/java", symbolId.sourceRootKey());
        assertEquals("com.foo.OrderService", symbolId.ownerPath());
        assertEquals("cancelOrder(Ljava/lang/Long;)V", symbolId.fragment().orElseThrow());
        assertEquals("method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V",
                symbolId.canonical());
    }

    @Test
    void normalizesWindowsPathsWithoutLeakingAbsoluteWorkspace() {
        SymbolId symbolId = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "(Ljava/lang/Long;)V");

        assertEquals("method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V",
                symbolId.canonical());
        assertFalse(symbolId.canonical().contains("D:"));
        assertFalse(symbolId.canonical().contains("\\"));
    }

    @Test
    void normalizesJavaMethodsUnderConfiguredSourceRoots() {
        SymbolId symbolId = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                List.of("src/test/java", "src/main/java"),
                "D:\\workspace\\shop\\src\\test\\java\\com\\foo\\OrderServiceTest.java",
                "com.foo.OrderServiceTest",
                "cancelsOrders",
                "()V");

        assertEquals("method://shop/_root/src/test/java/com.foo.OrderServiceTest#cancelsOrders()V",
                symbolId.canonical());
    }

    @Test
    void parsesResourceAndDatabaseSymbolExamples() {
        assertEquals("jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp",
                SymbolIdParser.parse("jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp").canonical());
        assertEquals("sql-statement://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById",
                SymbolIdParser.parse(
                        "sql-statement://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById")
                        .canonical());
        assertEquals("datasource://shop/mainDs",
                SymbolIdParser.parse("datasource://shop/mainDs").canonical());
        assertEquals("db-schema://shop/mainDs/public",
                SymbolIdParser.parse("db-schema://shop/mainDs/public").canonical());
        assertEquals("db-column://shop/mainDs/public/orders#order_id",
                SymbolIdParser.parse("db-column://shop/mainDs/public/orders#order_id").canonical());
        assertEquals("db-index://shop/mainDs/public/orders#orders_pk",
                SymbolIdParser.parse("db-index://shop/mainDs/public/orders#orders_pk").canonical());
        assertEquals("db-constraint://shop/mainDs/public/orders#orders_customer_fk",
                SymbolIdParser.parse("db-constraint://shop/mainDs/public/orders#orders_customer_fk").canonical());
        assertEquals("db-view://shop/mainDs/public/order_summary",
                SymbolIdParser.parse("db-view://shop/mainDs/public/order_summary").canonical());
        assertEquals("synthetic-symbol://shop/_root/src/main/java/com.foo.OrderService#anonymous-1",
                SymbolIdParser.parse("synthetic-symbol://shop/_root/src/main/java/com.foo.OrderService#anonymous-1")
                        .canonical());
        assertEquals("api-endpoint://shop/_root/src/main/java/GET:/orders/{id}",
                SymbolIdParser.parse("api-endpoint://shop/_root/src/main/java/GET:/orders/{id}").canonical());
    }

    @Test
    void validatesIllegalSymbolIds() {
        assertThrows(IllegalArgumentException.class, () -> SymbolIdParser.parse("unknown://shop/_root/src/main/java/X"));
        assertThrows(IllegalArgumentException.class, () -> SymbolIdParser.parse("method://shop/_root"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("method://shop/_root/C:/workspace/src/main/java/X#x()V"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("method://shop/_root/src\\main\\java/X#x()V"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("api-endpoint://shop/_root/C:/workspace/orders"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("api-endpoint://shop/_root/GET:/orders"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("html-page://shop/_root/src/main/webapp//WEB-INF/jsp/edit.jsp"));
    }

    @Test
    void parsesFlowAndArtifactIdentitiesWithRegisteredTypes() {
        SymbolId paramSlot = SymbolIdParser.parse(
                "param-slot://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V:param[0:Ljava/lang/Long;]");
        SymbolId requestParam = SymbolIdParser.parse("request-param://shop/_root/order-form#orderId");
        SymbolId sessionAttr = SymbolIdParser.parse("session-attr://shop/_root/http-session#cart");
        SymbolId modelAttr = SymbolIdParser.parse("model-attr://shop/_root/spring-model#order");
        SymbolId featureSeed = SymbolIdParser.parse("feature-seed://shop/run-20260502-001/6f4a9c");

        assertEquals(IdentityType.FLOW_ID, paramSlot.identityType());
        assertEquals("src/main/java", paramSlot.sourceRootKey());
        assertEquals("com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V", paramSlot.ownerPath());
        assertEquals("param[0:Ljava/lang/Long;]", paramSlot.fragment().orElseThrow());
        assertEquals(
                "param-slot://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V:param[0:Ljava/lang/Long;]",
                paramSlot.canonical());
        assertEquals("order-form", requestParam.ownerPath());
        assertEquals("orderId", requestParam.fragment().orElseThrow());
        assertEquals("request-param://shop/_root/order-form#orderId", requestParam.canonical());
        assertEquals("session-attr://shop/_root/http-session#cart", sessionAttr.canonical());
        assertEquals("model-attr://shop/_root/spring-model#order", modelAttr.canonical());
        assertEquals(IdentityType.ARTIFACT_ID, featureSeed.identityType());
    }

    @Test
    void parsesWithModuleSourceRootsInsteadOfOnlyBuiltInRoots() {
        SymbolIdParser parser = SymbolIdParser.withSourceRoots(List.of("generated/sources/annotations/java/main"));

        SymbolId generatedClass = parser.parseId(
                "class://shop/api/generated/sources/annotations/java/main/com.foo.GeneratedOrder");

        assertEquals("generated/sources/annotations/java/main", generatedClass.sourceRootKey());
        assertEquals("com.foo.GeneratedOrder", generatedClass.ownerPath());
        assertEquals("class://shop/api/generated/sources/annotations/java/main/com.foo.GeneratedOrder",
                generatedClass.canonical());
    }

    @Test
    void canonicalIsAssembledFromIdentityParts() {
        SymbolKind method = SymbolKindRegistry.defaults().require(DefaultSymbolKind.METHOD.kind());

        SymbolId symbolId = new SymbolId(
                method,
                "shop",
                "_root",
                "src/main/java",
                "com.foo.OrderService",
                "cancelOrder(Ljava/lang/Long;)V");

        assertEquals("method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V",
                symbolId.canonical());
    }

    @Test
    void rejectsUnsafeCanonicalCharactersFromParsedOrConstructedIds() {
        SymbolKind htmlPage = SymbolKindRegistry.defaults().require(DefaultSymbolKind.HTML_PAGE.kind());
        SymbolKind datasource = SymbolKindRegistry.defaults().require(DefaultSymbolKind.DATASOURCE.kind());
        SymbolKind requestParam = SymbolKindRegistry.defaults().require(DefaultSymbolKind.REQUEST_PARAM.kind());

        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("method://shop/_root/src/main/java/com.foo.OrderService#cancel Order()V"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("html-page://shop/_root/src/main/webapp/order?.html"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("html-page://shop/_root/src/main/webapp/order#edit.html"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("html-page://shop/_root/src/main/webapp/订单.html"));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp", "order?.html", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp", "order#edit.html", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp", "index.html", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(datasource, "shop", "mainDs", "src/main/java", "com.foo.OrderService", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(requestParam, "shop", "_root", "", "order-form#old", "orderId"));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "/src/main/webapp", "WEB-INF/jsp/edit.jsp", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp/", "WEB-INF/jsp/edit.jsp", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src//main/webapp", "WEB-INF/jsp/edit.jsp", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp", "/WEB-INF/jsp/edit.jsp", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SymbolId(htmlPage, "shop", "_root", "src/main/webapp", "WEB-INF//jsp/edit.jsp", null));
    }
}
