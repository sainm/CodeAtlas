package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sainm.codeatlas.facts.Confidence;
import org.sainm.codeatlas.facts.FactRecord;

class StrutsConfigFactMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsStrutsActionPathsToExecuteMethodFacts() throws IOException {
        write("WEB-INF/struts-config-admin.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/user/save" type="com.acme.web.UserSaveAction">
                      <forward name="success" path="/WEB-INF/jsp/user.jsp"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config-admin.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config-admin.xml"));

        assertFalse(batch.evidence().isEmpty());
        assertFact(batch,
                "ROUTES_TO",
                "action-path://shop/_root/_actions/admin/user/save",
                "method://shop/_root/src/main/java/com.acme.web.UserSaveAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;");
        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.UserSaveAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "jsp-page://shop/_root/src/main/webapp/admin/WEB-INF/jsp/user.jsp");
        assertTrue(batch.facts().stream().allMatch(fact -> fact.confidence() == Confidence.LIKELY));
    }

    @Test
    void mapsMappingDispatchActionRoutesToConfiguredMethods() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/user/save"
                            type="com.acme.web.UserMappingDispatchAction"
                            parameter="save">
                      <forward name="success" path="/WEB-INF/jsp/user.jsp"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        String saveMethod = "method://shop/_root/src/main/java/com.acme.web.UserMappingDispatchAction"
                + "#save(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;"
                + "Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)"
                + "Lorg/apache/struts/action/ActionForward;";
        assertFact(batch,
                "ROUTES_TO",
                "action-path://shop/_root/_actions/user/save",
                saveMethod);
        assertFact(batch,
                "FORWARDS_TO",
                saveMethod,
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user.jsp");
        assertFalse(batch.facts().stream().anyMatch(fact -> fact.sourceIdentityId().contains("#execute(")
                || fact.targetIdentityId().contains("#execute(")));
    }

    @Test
    void stripsQueryAndFragmentFromStrutsJspForwards() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/user/show" type="com.acme.web.UserShowAction">
                      <forward name="success" path="/WEB-INF/jsp/user.jsp?id=1#details"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.UserShowAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/user.jsp");
    }

    @Test
    void mapsStrutsActionLevelForwardAttributes() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/help" type="com.acme.web.HelpAction" forward="/WEB-INF/jsp/help.jsp"/>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.HelpAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/help.jsp");
    }

    @Test
    void mapsForwardOnlyStrutsActionsFromActionPathToJspPage() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/help" forward="/WEB-INF/jsp/help.jsp"/>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "action-path://shop/_root/_actions/help",
                "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/help.jsp");
    }

    @Test
    void mapsStrutsActionForwardsToActionPathFacts() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/gate" type="com.acme.web.GateAction">
                      <forward name="login" path="/login.do"/>
                    </action>
                    <action path="/login" type="com.acme.web.LoginAction"/>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.GateAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "action-path://shop/_root/_actions/login");
    }

    @Test
    void mapsModuleRelativeStrutsForwardsToModuleActionPaths() throws IOException {
        write("WEB-INF/struts-config-admin.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/gate" type="com.acme.web.AdminGateAction">
                      <forward name="login" path="/login.do"/>
                    </action>
                    <action path="/login" type="com.acme.web.AdminLoginAction"/>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config-admin.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config-admin.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.AdminGateAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "action-path://shop/_root/_actions/admin/login");
    }

    @Test
    void mapsModuleRelativeStrutsForwardsToModulePages() throws IOException {
        write("WEB-INF/struts-config-admin.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/gate" type="com.acme.web.AdminGateAction">
                      <forward name="success" path="/WEB-INF/jsp/home.jsp"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config-admin.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config-admin.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.AdminGateAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "jsp-page://shop/_root/src/main/webapp/admin/WEB-INF/jsp/home.jsp");
    }

    @Test
    void mapsStrutsActionForwardsToStaticHtmlPages() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/help" type="com.acme.web.HelpAction">
                      <forward name="success" path="/help.html?mode=user#top"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFact(batch,
                "FORWARDS_TO",
                "method://shop/_root/src/main/java/com.acme.web.HelpAction#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)Lorg/apache/struts/action/ActionForward;",
                "html-page://shop/_root/src/main/webapp/help.html");
    }

    @Test
    void normalizesStrutsForwardTargetsWithDotSegments() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/gate" type="com.acme.web.GateAction">
                      <forward name="jsp" path="/WEB-INF/jsp/user/../home.jsp"/>
                      <forward name="html" path="/docs/../help.html"/>
                      <forward name="action" path="/admin/../login.do"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        String gateMethod = "method://shop/_root/src/main/java/com.acme.web.GateAction"
                + "#execute(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;"
                + "Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)"
                + "Lorg/apache/struts/action/ActionForward;";
        assertFact(batch, "FORWARDS_TO", gateMethod, "jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/home.jsp");
        assertFact(batch, "FORWARDS_TO", gateMethod, "html-page://shop/_root/src/main/webapp/help.html");
        assertFact(batch, "FORWARDS_TO", gateMethod, "action-path://shop/_root/_actions/login");
    }

    @Test
    void skipsExternalStrutsForwards() throws IOException {
        write("WEB-INF/struts-config.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/external" type="com.acme.web.ExternalAction">
                      <forward name="success" path="https://example.com/help.html"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config.xml"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("FORWARDS_TO")));
    }

    @Test
    void skipsExternalStrutsForwardsFromModuleConfigs() throws IOException {
        write("WEB-INF/struts-config-admin.xml", """
                <struts-config>
                  <action-mappings>
                    <action path="/external" type="com.acme.web.ExternalAction">
                      <forward name="success" path="https://example.com/login.do"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """);
        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config-admin.xml")));

        JavaSourceFactBatch batch = StrutsConfigFactMapper.defaults().map(
                result,
                new StrutsConfigFactContext(
                        "shop",
                        "_root",
                        "WEB-INF",
                        "src/main/webapp",
                        "src/main/java",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "WEB-INF/struts-config-admin.xml"));

        assertFalse(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("FORWARDS_TO")));
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        assertTrue(batch.facts().stream().anyMatch(fact -> matches(fact, relationName, sourceIdentityId, targetIdentityId)),
                () -> "Missing " + relationName + " fact from " + sourceIdentityId + " to " + targetIdentityId
                        + " in " + batch.facts());
    }

    private static boolean matches(
            FactRecord fact,
            String relationName,
            String sourceIdentityId,
            String targetIdentityId) {
        return fact.relationType().name().equals(relationName)
                && fact.sourceIdentityId().equals(sourceIdentityId)
                && fact.targetIdentityId().equals(targetIdentityId);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
