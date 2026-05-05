package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class JasperSmapParserTest {
    @Test
    void parsesJasperSmapBlockWithFileAndLineMappings() {
        String generatedJava = """
                package org.apache.jsp;

                import javax.servlet.jsp.*;
                import java.util.*;

                public final class UserPage_jsp extends HttpJspBase { }

                /* SMAP
                UserPage_jsp.java
                JSP
                *S JSP
                *F
                + 0 user/page/UserPage.jsp
                UserPage.jsp
                + 1 user/page/UserPage.jsp
                include/nav.jsp
                *L
                1,1:1,5
                2,2:6,10
                10#1,3:20,5
                15#1,4:30,10
                *E
                */
                """;

        Optional<JasperSmapParser.JasperSmapResult> result = JasperSmapParser.defaults()
                .parse(generatedJava, "/app/web/user/page/UserPage.jsp");

        assertTrue(result.isPresent());
        assertEquals(4, result.get().mappings().size());

        JasperSmapParser.JspLineMapping first = result.get().mappings().get(0);
        assertEquals("UserPage.jsp", first.jspPath());
        assertEquals(1, first.jspLineStart());
        assertEquals(1, first.jspLineEnd());
        assertEquals(1, first.generatedLineStart());
        assertEquals(5, first.generatedLineEnd());

        JasperSmapParser.JspLineMapping multiLine = result.get().mappings().get(1);
        assertEquals(2, multiLine.jspLineStart());
        assertEquals(3, multiLine.jspLineEnd());
        assertEquals(6, multiLine.generatedLineStart());
        assertEquals(25, multiLine.generatedLineEnd());

        JasperSmapParser.JspLineMapping includeMapping = result.get().mappings().get(2);
        assertEquals("include/nav.jsp", includeMapping.jspPath());
        assertEquals(10, includeMapping.jspLineStart());
        assertEquals(12, includeMapping.jspLineEnd());
        assertEquals(20, includeMapping.generatedLineStart());
        assertEquals(34, includeMapping.generatedLineEnd());
    }

    @Test
    void returnsEmptyWhenNoSmapBlockFound() {
        String content = "public class Foo {}";
        Optional<JasperSmapParser.JasperSmapResult> result = JasperSmapParser.defaults()
                .parse(content, "/app/Foo.jsp");
        assertFalse(result.isPresent());
    }

    @Test
    void returnsEmptyWhenNoLineMappingsExist() {
        String content = """
                /* SMAP
                Test_jsp.java
                JSP
                *S JSP
                *F
                + 0 test.jsp
                test.jsp
                *L
                *E
                */
                """;
        Optional<JasperSmapParser.JasperSmapResult> result = JasperSmapParser.defaults()
                .parse(content, "/app/test.jsp");
        assertFalse(result.isPresent());
    }
}
