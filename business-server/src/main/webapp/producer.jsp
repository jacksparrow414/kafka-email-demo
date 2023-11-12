<%--
  Created by IntelliJ IDEA.
  User: jacksparrow414
  Date: 2023/10/15
  Time: 11:08
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>Producer</title>
</head>
<body>
<form action="producerMessage" method="post">
   用户名: <input type="text" name="username"> <br>
    密 码: <input type="text" name="password"> <br>
    <input type="submit" value="发送消息">
</form>
</body>
</html>
