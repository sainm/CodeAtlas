package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsConfigAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesStrutsConfigActionsFormsForwardsPluginsAndController() throws IOException {
        write("WEB-INF/struts-config-admin.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE struts-config PUBLIC
                  "-//Apache Software Foundation//DTD Struts Configuration 1.3//EN"
                  "http://struts.apache.org/dtds/struts-config_1_3.dtd">
                <struts-config>
                  <form-beans>
                    <form-bean name="userForm" type="org.apache.struts.action.DynaActionForm">
                      <form-property name="id" type="java.lang.String"/>
                      <form-property name="phone" type="java.lang.String" initial=""/>
                    </form-bean>
                  </form-beans>
                  <global-forwards>
                    <forward name="home" path="/index.jsp" redirect="false"/>
                  </global-forwards>
                  <global-exceptions>
                    <exception key="error.user" type="com.acme.UserException" path="/error.jsp"/>
                  </global-exceptions>
                  <action-mappings>
                    <action path="/user/update"
                            type="com.acme.web.UserDispatchAction"
                            name="userForm"
                            scope="request"
                            parameter="method">
                      <forward name="success" path="/WEB-INF/jsp/user.jsp"/>
                      <exception key="error.update" type="com.acme.UpdateException" path="/error.jsp"/>
                    </action>
                  </action-mappings>
                  <controller processorClass="com.acme.web.CustomRequestProcessor" contentType="text/html"/>
                  <message-resources parameter="ApplicationResources" key="default"/>
                  <plug-in className="org.apache.struts.tiles.TilesPlugin">
                    <set-property property="definitions-config" value="/WEB-INF/tiles-defs.xml"/>
                  </plug-in>
                  <plug-in className="org.apache.struts.validator.ValidatorPlugIn">
                    <set-property property="pathnames" value="/WEB-INF/validator-rules.xml,/WEB-INF/validation.xml"/>
                  </plug-in>
                </struts-config>
                """);

        StrutsConfigAnalysisResult result = StrutsConfigAnalyzer.defaults().analyze(
                tempDir,
                List.of(tempDir.resolve("WEB-INF/struts-config-admin.xml")));

        assertTrue(result.diagnostics().isEmpty());
        assertTrue(result.modules().stream().anyMatch(module -> module.moduleKey().equals("admin")
                && module.configPath().equals("WEB-INF/struts-config-admin.xml")));
        StrutsFormBeanInfo formBean = result.formBeans().stream()
                .filter(candidate -> candidate.name().equals("userForm"))
                .findFirst()
                .orElseThrow();
        assertTrue(formBean.dynamic());
        assertEquals(List.of("id", "phone"), formBean.properties().stream().map(StrutsFormPropertyInfo::name).toList());
        StrutsActionInfo action = result.actions().stream()
                .filter(candidate -> candidate.path().equals("/admin/user/update"))
                .findFirst()
                .orElseThrow();
        assertEquals("com.acme.web.UserDispatchAction", action.type());
        assertEquals("userForm", action.formBeanName());
        assertEquals("method", action.parameter());
        assertEquals(StrutsActionDispatchKind.DISPATCH, action.dispatchKind());
        assertTrue(action.forwards().stream().anyMatch(forward -> forward.name().equals("success")
                && forward.path().equals("/WEB-INF/jsp/user.jsp")));
        assertTrue(action.exceptions().stream().anyMatch(exception -> exception.key().equals("error.update")));
        assertTrue(result.globalForwards().stream().anyMatch(forward -> forward.name().equals("home")
                && forward.path().equals("/index.jsp")));
        assertTrue(result.globalExceptions().stream().anyMatch(exception -> exception.type().equals("com.acme.UserException")));
        assertTrue(result.messageResources().stream()
                .anyMatch(resource -> resource.parameter().equals("ApplicationResources")));
        assertTrue(result.plugins().stream().anyMatch(plugin -> plugin.kind() == StrutsPluginKind.TILES
                && plugin.properties().get("definitions-config").equals("/WEB-INF/tiles-defs.xml")));
        assertTrue(result.plugins().stream().anyMatch(plugin -> plugin.kind() == StrutsPluginKind.VALIDATOR
                && plugin.properties().get("pathnames").contains("validation.xml")));
        assertFalse(result.controllers().isEmpty());
        assertEquals("com.acme.web.CustomRequestProcessor", result.controllers().get(0).processorClass());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
