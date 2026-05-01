<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<html:form action="/login.do" method="post">
  <label for="username">Username</label>
  <html:text property="username"/>
  <html:submit value="Login"/>
</html:form>
