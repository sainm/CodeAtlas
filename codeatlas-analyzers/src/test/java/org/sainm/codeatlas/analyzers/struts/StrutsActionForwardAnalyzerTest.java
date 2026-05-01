package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsActionForwardAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void linksMappingFindForwardToNamedStrutsForwardConfig() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) {
                return mapping.findForward("success");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        var result = new StrutsActionForwardAnalyzer().analyze(scope, "shop", "src/main/java", "src/main/webapp", List.of(source));

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.CONFIG_KEY
            && node.symbolId().sourceRootKey().equals("src/main/webapp")
            && node.symbolId().ownerQualifiedName().equals("struts-forward")
            && node.symbolId().localId().equals("success")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().ownerQualifiedName().equals("com.acme.UserAction")
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.CONFIG_KEY
            && fact.factKey().target().ownerQualifiedName().equals("struts-forward")
            && fact.factKey().target().localId().equals("success")
            && fact.factKey().qualifier().equals("mapping.findForward:success")));
    }

    @Test
    void linksDirectActionForwardConstructorToJspOrActionTarget() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserAction extends org.apache.struts.action.Action {
              org.apache.struts.action.ActionForward detail() {
                return new org.apache.struts.action.ActionForward("/user/detail.jsp");
              }

              org.apache.struts.action.ActionForward next() {
                return new org.apache.struts.action.ActionForward("/user/next.do");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        var result = new StrutsActionForwardAnalyzer().analyze(scope, "shop", "src/main/java", "src/main/webapp", List.of(source));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("new ActionForward:/user/detail.jsp")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("next")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("user/next")
            && fact.factKey().qualifier().equals("new ActionForward:/user/next.do")));
    }

    @Test
    void usesPathArgumentForNamedActionForwardConstructor() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserAction extends org.apache.struts.action.Action {
              org.apache.struts.action.ActionForward detail() {
                return new org.apache.struts.action.ActionForward("success", "/user/detail.jsp", true);
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        var result = new StrutsActionForwardAnalyzer().analyze(scope, "shop", "src/main/java", "src/main/webapp", List.of(source));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("new ActionForward:/user/detail.jsp")));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("detail")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("success")));
    }

    @Test
    void linksSendRedirectAndActionRedirectToNavigationTargets() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            class UserAction extends org.apache.struts.action.Action {
              public org.apache.struts.action.ActionForward execute(
                  org.apache.struts.action.ActionMapping mapping,
                  org.apache.struts.action.ActionForm form,
                  javax.servlet.http.HttpServletRequest request,
                  javax.servlet.http.HttpServletResponse response) throws Exception {
                response.sendRedirect("/login.do");
                return new org.apache.struts.action.ActionRedirect("/user/detail.jsp");
              }
            }
            """);

        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "src/main/java", tempDir);
        var result = new StrutsActionForwardAnalyzer().analyze(scope, "shop", "src/main/java", "src/main/webapp", List.of(source));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.ACTION_PATH
            && fact.factKey().target().ownerQualifiedName().equals("login")
            && fact.factKey().qualifier().equals("sendRedirect:/login.do")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.FORWARDS_TO
            && fact.factKey().source().kind() == SymbolKind.METHOD
            && fact.factKey().source().memberName().equals("execute")
            && fact.factKey().target().kind() == SymbolKind.JSP_PAGE
            && fact.factKey().target().ownerQualifiedName().equals("user/detail.jsp")
            && fact.factKey().qualifier().equals("new ActionRedirect:/user/detail.jsp")));
    }
}
