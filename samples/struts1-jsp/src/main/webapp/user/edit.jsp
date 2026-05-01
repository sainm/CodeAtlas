<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ include file="/common/footer.jsp" %>

<html:form action="/user/save.do" method="post">
  <html:hidden property="userId"/>
  <label for="name">Name</label>
  <html:text property="name"/>
  <label for="description">Description</label>
  <html:textarea property="description"/>
  <html:submit value="Save"/>
</html:form>

<logic:iterate id="user" name="users">
  <bean:write name="user" property="name"/>
</logic:iterate>
