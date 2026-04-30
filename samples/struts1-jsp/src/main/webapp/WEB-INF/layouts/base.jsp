<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<!doctype html>
<html>
<head>
  <title><tiles:getAsString name="title"/></title>
</head>
<body>
  <header>
    <a href="<%= request.getContextPath() %>/user/edit.do">Edit user</a>
    <a href="<%= request.getContextPath() %>/admin/user/list.do">Admin</a>
  </header>
  <main>
    <tiles:insert attribute="body"/>
  </main>
</body>
</html>
