<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<html:form action="/admin/user/list.do" method="get">
  <html:hidden property="page"/>
  <label for="keyword">Keyword</label>
  <html:text property="keyword"/>
  <label for="includeDisabled">Include disabled</label>
  <html:checkbox property="includeDisabled"/>
  <html:submit property="method" value="search"/>
</html:form>

<logic:iterate id="user" name="adminUsers">
  <div>
    <bean:write name="user" property="name"/>
    <html:link page="/user/detail.jsp">Detail</html:link>
  </div>
</logic:iterate>
