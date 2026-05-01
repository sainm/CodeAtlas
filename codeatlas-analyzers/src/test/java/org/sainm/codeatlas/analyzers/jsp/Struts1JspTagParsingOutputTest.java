package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Struts1JspTagParsingOutputTest {
    @TempDir
    Path tempDir;

    @Test
    void printsStruts1JspTagParsingOutput() throws Exception {
        Path webRoot = tempDir.resolve("src/main/webapp");
        Path page = webRoot.resolve("user/edit.jsp");
        Files.createDirectories(page.getParent());
        Files.writeString(page, """
            <%@ page pageEncoding="UTF-8" %>
            <%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
            <%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
            <%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
            <%@ taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles" %>

            <html:form action="/user/save.do" method="post">
              <html:text property="name"/>
              <html:hidden property="id"/>
              <html:password property="password"/>
              <html:checkbox property="active"/>
              <html:select property="role">
                <html:optionsCollection name="roleOptions" value="code" label="label"/>
              </html:select>
              <html:textarea property="description"/>
              <html:radio property="status" value="ACTIVE"/>
              <html:multibox property="groups" value="admin"/>
              <html:submit property="method" value="save"/>
              <html:cancel property="cancel"/>
              <html:button property="preview" value="Preview"/>
              <html:file property="avatar"/>
              <html:image property="imageAction" page="/images/save.png"/>
              <html:reset property="reset"/>
            </html:form>

            <html:link action="/user/detail.do" paramId="id" paramName="user">Detail</html:link>
            <html:rewrite action="/user/edit"/>
            <html:img page="/images/user.png"/>
            <html:errors property="global"/>

            <logic:iterate id="row" name="users">
              <bean:write name="row" property="id"/>
              <bean:write name="row" property="name"/>
            </logic:iterate>
            <logic:redirect action="/user/search.do"/>
            <logic:forward name="sessionExpired"/>

            <tiles:insert definition=".layout.main" flush="true">
              <tiles:put name="body" value="/WEB-INF/jsp/user/body.jsp"/>
              <tiles:getAsString name="title"/>
            </tiles:insert>
            """);
        WebAppContext context = new WebAppContext(
            webRoot,
            null,
            "2.5",
            "2.1",
            "generic",
            List.of(),
            Map.of(
                "http://struts.apache.org/tags-html", "WEB-INF/struts-html.tld",
                "http://struts.apache.org/tags-bean", "WEB-INF/struts-bean.tld",
                "http://struts.apache.org/tags-logic", "WEB-INF/struts-logic.tld",
                "http://struts.apache.org/tags-tiles", "WEB-INF/struts-tiles.tld"
            ),
            "UTF-8"
        );

        JspSemanticAnalysis analysis = new TolerantJspSemanticExtractor().extract(Files.readString(page), context, page);
        List<JspForm> forms = new TolerantJspFormExtractor().extract(Files.readString(page));

        assertFalse(analysis.actions().isEmpty());
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("html:form")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("logic:iterate")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("bean:write")));
        assertTrue(analysis.actions().stream().anyMatch(action -> action.name().equals("tiles:insert")));
        assertTrue(forms.stream().anyMatch(form -> form.action().equals("/user/save.do")));

        System.out.println("STRUTS1_JSP_TAG_PARSE_OUTPUT");
        System.out.println("jspFile=" + page);
        System.out.println("taglibs=" + analysis.taglibs().size());
        for (JspTaglibReference taglib : analysis.taglibs()) {
            System.out.println("taglib line=" + taglib.line()
                + " prefix=" + taglib.prefix()
                + " uri=" + taglib.uri()
                + " resolved=" + taglib.resolvedLocation()
                + " confidence=" + taglib.confidence());
        }
        System.out.println("actions=" + analysis.actions().size());
        for (JspAction action : analysis.actions()) {
            System.out.println("action line=" + action.line() + " name=" + action.name() + " attrs=" + action.attributes());
        }
        System.out.println("forms=" + forms.size());
        for (JspForm form : forms) {
            System.out.println("form line=" + form.line() + " action=" + form.action() + " method=" + form.method());
            for (JspInput input : form.inputs()) {
                System.out.println("input line=" + input.line() + " name=" + input.name() + " type=" + input.type());
            }
        }
    }
}
