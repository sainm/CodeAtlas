<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<html:form action="/user/save.do" method="post">
  <html:hidden property="userId"/>
  <html:text property="name"/>
  <html:textarea property="description"/>
  <html:submit value="Save"/>
</html:form>

<logic:iterate id="user" name="users">
  <bean:write name="user" property="name"/>
</logic:iterate>
