<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<!doctype html>
<html>
<head>
  <title><tiles:getAsString name="title"/></title>
</head>
<body>
  <nav>
    <a href="<%= request.getContextPath() %>/admin/user/list.do">Users</a>
    <a href="<%= request.getContextPath() %>/user/edit.do">Default module</a>
  </nav>
  <section>
    <tiles:insert attribute="body"/>
  </section>
</body>
</html>
