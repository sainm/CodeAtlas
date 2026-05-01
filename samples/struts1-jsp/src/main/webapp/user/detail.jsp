<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<h2><bean:write name="userForm" property="name"/></h2>
<p><bean:write name="userForm" property="description"/></p>
<html:link action="/user/save.do">Edit</html:link>
