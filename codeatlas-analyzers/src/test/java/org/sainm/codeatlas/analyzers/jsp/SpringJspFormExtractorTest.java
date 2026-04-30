package org.sainm.codeatlas.analyzers.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringJspFormExtractorTest {
    @Test
    void extractsSpringFormPathInputs() {
        String jsp = """
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <form:form action="/spring/user/save" method="post" modelAttribute="userForm">
              <form:input path="name"/>
              <form:hidden path="id"/>
              <form:password path="password"/>
              <form:checkbox path="active"/>
              <form:select path="role">
                <form:options items="${roleOptions}" itemValue="code" itemLabel="label"/>
              </form:select>
              <form:textarea path="description"/>
              <form:radiobutton path="status" value="ACTIVE"/>
              <form:button name="method" value="save">Save</form:button>
            </form:form>
            """;

        List<JspForm> forms = new TolerantJspFormExtractor().extract(jsp);

        assertEquals(1, forms.size());
        JspForm form = forms.getFirst();
        assertEquals("/spring/user/save", form.action());
        assertEquals("post", form.method());
        assertEquals(8, form.inputs().size());
        assertEquals("name", form.inputs().get(0).name());
        assertEquals("input", form.inputs().get(0).type());
        assertEquals("id", form.inputs().get(1).name());
        assertEquals("hidden", form.inputs().get(1).type());
        assertEquals("role", form.inputs().get(4).name());
        assertEquals("select", form.inputs().get(4).type());
        assertEquals("method", form.inputs().get(7).name());
        assertEquals("button", form.inputs().get(7).type());
    }
}
