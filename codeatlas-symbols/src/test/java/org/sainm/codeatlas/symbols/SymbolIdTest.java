package org.sainm.codeatlas.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void allowsConsecutiveDotsButRejectsTraversalSegmentsInSymbolPaths() {
        SymbolId symbolId = SymbolIdParser.parse("source-file://shop/_root/src/main/java/com/acme/Foo..java");

        assertEquals("source-file://shop/_root/src/main/java/com/acme/Foo..java", symbolId.canonical());
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("source-file://shop/_root/src/main/java/com/acme/../Foo.java"));
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
    void modelsProvisionalJavaMethodDescriptorUntilResolved() {
        ProvisionalSymbol provisional = SymbolIdNormalizer.provisionalJavaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                List.of("src/main/java"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "cancelOrder(OrderRequest request)");
        SymbolId resolved = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "(Lcom/foo/OrderRequest;)V");

        assertEquals(DescriptorStatus.UNRESOLVED, provisional.descriptorStatus());
        assertFalse(provisional.allowsCertainFacts());
        assertEquals("method", provisional.symbolId().kind().kind());
        assertEquals("src/main/java", provisional.symbolId().sourceRootKey());
        assertEquals("com.foo.OrderService", provisional.symbolId().ownerPath());
        assertFalse(provisional.symbolId().canonical().contains("OrderRequest request"));
        assertEquals(resolved, provisional.withCanonicalReplacement(resolved).canonicalReplacement().orElseThrow());
    }

    @Test
    void buildsResolvedSymbolRedirectInsideTheSameIdentityContext() {
        ProvisionalSymbol provisional = SymbolIdNormalizer.provisionalJavaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                List.of("src/main/java"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "cancelOrder(OrderRequest request)");
        SymbolId resolved = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "(Lcom/foo/OrderRequest;)V");
        SymbolId otherProject = SymbolIdParser.parse(
                "method://billing/_root/src/main/java/com.foo.OrderService#cancelOrder(Lcom/foo/OrderRequest;)V");
        SymbolId otherKind = SymbolIdParser.parse("class://shop/_root/src/main/java/com.foo.OrderService");
        SymbolId otherOwner = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\CustomerService.java",
                "com.foo.CustomerService",
                "cancelOrder",
                "(Lcom/foo/OrderRequest;)V");
        SymbolId otherMethod = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "archiveOrder",
                "(Lcom/foo/OrderRequest;)V");
        SymbolId otherOverload = SymbolIdNormalizer.javaMethod(
                SymbolContext.of("shop", "_root", "D:\\workspace\\shop"),
                "D:\\workspace\\shop\\src\\main\\java\\com\\foo\\OrderService.java",
                "com.foo.OrderService",
                "cancelOrder",
                "(Ljava/lang/String;)V");
        SymbolKind field = SymbolKindRegistry.defaults().require(DefaultSymbolKind.FIELD.kind());
        SymbolId stringField = new SymbolId(
                field,
                "shop",
                "_root",
                "src/main/java",
                "com.foo.OrderService",
                "status:Ljava/lang/String;");
        SymbolId intField = new SymbolId(
                field,
                "shop",
                "_root",
                "src/main/java",
                "com.foo.OrderService",
                "status:I");

        ResolvedSymbolRedirect redirect = ResolvedSymbolRedirect.from(provisional, resolved, "MERGED_ALIAS");

        assertEquals(provisional.symbolId(), redirect.from());
        assertEquals(resolved, redirect.to());
        assertEquals(AliasMergeStatus.REDIRECT, redirect.status());
        assertEquals("MERGED_ALIAS", redirect.evidenceKey());
        assertTrue(redirect.redirects(provisional.symbolId()));
        assertEquals(resolved, redirect.resolve(provisional.symbolId()));
        assertEquals(resolved, redirect.resolve(resolved));
        ResolvedSymbolRedirect conflict = new ResolvedSymbolRedirect(
                provisional.symbolId(),
                resolved,
                AliasMergeStatus.CONFLICT,
                "CONFLICT_EVIDENCE");
        assertFalse(conflict.redirects(provisional.symbolId()));
        assertEquals(provisional.symbolId(), conflict.resolve(provisional.symbolId()));
        assertThrows(IllegalArgumentException.class, () -> provisional.withCanonicalReplacement(otherProject));
        assertThrows(IllegalArgumentException.class,
                () -> ResolvedSymbolRedirect.from(provisional, otherProject, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> ResolvedSymbolRedirect.from(provisional, otherKind, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> ResolvedSymbolRedirect.from(provisional, otherOwner, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> ResolvedSymbolRedirect.from(provisional, otherMethod, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedSymbolRedirect(resolved, provisional.symbolId(), AliasMergeStatus.REDIRECT, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedSymbolRedirect(resolved, otherOverload, AliasMergeStatus.REDIRECT, "MERGED_ALIAS"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedSymbolRedirect(stringField, intField, AliasMergeStatus.REDIRECT, "MERGED_ALIAS"));
    }

    @Test
    void normalizesJspHtmlSqlAndReportIdentitiesFromSourceRoots() {
        SymbolContext context = SymbolContext.of("shop", "_root", "D:\\workspace\\shop");
        List<String> sourceRoots = List.of("src/main/webapp", "src/main/resources", "reports");

        assertEquals("jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp",
                SymbolIdNormalizer.jspPage(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\WEB-INF\\jsp\\order\\edit.jsp").canonical());
        assertEquals("jsp-form://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[save:post:12:0]",
                SymbolIdNormalizer.jspForm(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\WEB-INF\\jsp\\order\\edit.jsp",
                        "save:post:12:0").canonical());
        assertEquals(
                "jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[save:post:12:0]:input[orderId:hidden:13:0]",
                SymbolIdNormalizer.jspInput(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\WEB-INF\\jsp\\order\\edit.jsp",
                        "save:post:12:0",
                        "orderId:hidden:13:0").canonical());
        assertEquals("html-form://shop/_root/src/main/webapp/order/new.html#form[save:post:9:0]",
                SymbolIdNormalizer.htmlForm(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\order\\new.html",
                        "save:post:9:0").canonical());
        assertEquals("script-resource://shop/_root/src/main/webapp/assets/order.js",
                SymbolIdNormalizer.scriptResource(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\assets\\order.js").canonical());
        assertEquals(
                "client-request://shop/_root/src/main/webapp/assets/order.js#request[fetch:POST:4fd1a2:21:0]",
                SymbolIdNormalizer.clientRequest(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\webapp\\assets\\order.js",
                        "fetch",
                        "POST",
                        "4fd1a2",
                        21,
                        0).canonical());
        assertEquals(
                "config-key://shop/_root/src/main/resources/struts-config.xml#/struts-config/action-mappings/action[@path='/user/save']",
                SymbolIdNormalizer.configKey(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\resources\\struts-config.xml",
                        "/struts-config/action-mappings/action[@path='/user/save']").canonical());
        assertEquals("sql-statement://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById",
                SymbolIdNormalizer.sqlStatement(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\src\\main\\resources\\com\\foo\\OrderMapper.xml",
                        "com.foo.OrderMapper.selectById").canonical());
        assertEquals("report-definition://shop/_root/reports/order/detail.svf",
                SymbolIdNormalizer.reportDefinition(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\reports\\order\\detail.svf").canonical());
        assertEquals("report-field://shop/_root/reports/order/detail.svf#order_id",
                SymbolIdNormalizer.reportField(
                        context,
                        sourceRoots,
                        "D:\\workspace\\shop\\reports\\order\\detail.svf",
                        "order_id").canonical());
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
        assertEquals("api-endpoint://shop/_root/src/main/java/GET:/",
                SymbolIdParser.parse("api-endpoint://shop/_root/src/main/java/GET:/").canonical());
        assertEquals("api-endpoint://shop/_root/_api/POST:/orders",
                SymbolIdParser.parse("api-endpoint://shop/_root/_api/POST:/orders").canonical());
        assertEquals("dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]",
                SymbolIdParser.withSourceRoots(List.of("WEB-INF"))
                        .parseId("dicon-component://shop/_root/WEB-INF/app.dicon#component[userService]")
                        .canonical());
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
                () -> SymbolIdParser.parse("api-endpoint://shop/GET:/_api/POST:/orders"));
        assertThrows(IllegalArgumentException.class,
                () -> SymbolIdParser.parse("html-page://shop/_root/src/main/webapp//WEB-INF/jsp/edit.jsp"));
    }

    @Test
    void parsesFlowAndArtifactIdentitiesWithRegisteredTypes() {
        SymbolId paramSlot = SymbolIdParser.parse(
                "param-slot://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V:param[0:Ljava/lang/Long;]");
        SymbolId requestParam = SymbolIdParser.parse("request-param://shop/_root/order-form#orderId");
        SymbolId apiScopedRequestParam = SymbolIdParser.parse("request-param://shop/_root/_api#token");
        SymbolId parserScopedRequestParam = SymbolIdParser.withSourceRoots(List.of("src/main/webapp", "_api"))
                .parseId("request-param://shop/_root/_api#token");
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
        assertEquals("", apiScopedRequestParam.sourceRootKey());
        assertEquals("_api", apiScopedRequestParam.ownerPath());
        assertEquals("token", apiScopedRequestParam.fragment().orElseThrow());
        assertEquals("request-param://shop/_root/_api#token", apiScopedRequestParam.canonical());
        assertEquals(apiScopedRequestParam, parserScopedRequestParam);
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
