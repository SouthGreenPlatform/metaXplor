<%--
 * metaXplor - Copyright (C) 2020 <CIRAD>
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License, version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 *
 * See <http://www.gnu.org/licenses/agpl.html> for details about GNU General
 * Public License V3.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
    <head>
		<link rel ="stylesheet" type="text/css" href="css/role_manager.css" title="style">
		<script type="text/javascript" src="<c:url value="/private/js/jquery-1.12.4.min.js" />"></script>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Error 403</title>
    </head>
    <body>
    	<center>
	    <div style="margin-top:50px;" class="formErrors">
	        <p style="font-size:15px; font-weight:bold;">Wrong credentials</p>
        </div>
        </center>
        <div id="stackTrace" style="background-color:#f7f7f7; margin:10px; height:310px; overflow-y:scroll; display:none;"><b>${exception}</b><c:forEach items='${exception.stackTrace}' var='ste'><br>${ste}</c:forEach></div>
    </body>
</html>