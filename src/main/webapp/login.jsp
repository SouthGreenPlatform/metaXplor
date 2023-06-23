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
<%@page contentType="text/html" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController,org.springframework.security.web.WebAttributes" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %> 
<fmt:setBundle basename="config" />
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<meta name="google" content="notranslate">
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <title>Login</title>
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/login.css">
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript">
            var currentWindow = this;
            while (currentWindow != top)
            {
                try
                {
                    currentWindow.parent.document;	// accessing this throws an exception if webapp is running in a frame
                    currentWindow = currentWindow.parent;
                } catch (e)
                {
                    break;
                }
            }
            if (currentWindow != this)
                currentWindow.location.href = location.href;
        </script>
    </head>
    <body>
<%
	Exception lastException = (Exception) session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
%>
        <div class ="container">
            <div class="row margin-top">
                <div class="col-md-4"></div>
                <div class="col-md-4">
                    <div class="panel panel-default">
                        <div class="panel-body text-center">
                        	<div style="background-color:white; padding:7px; border:darkblue 5px outset; margin:10px 0 40px 0;">
                        		<img src="img/logo_metaxplor${param.color}.png" width='200' />
                        		<br/>
                        		LOGIN FORM
                        	</div>
                            <form name="f" action='j_spring_security_check' method='POST' id="form-login">
                                <input type="text" name="username" id="username" placeholder="Username" required="required" />
                                <input type="password" name="password" id="password" placeholder="Password" required="required" />
                                <button type="submit" name="connexion" class="btn btn-primary btn-block btn-large">Log  me in</button> 
                            </form>
							<div class="text-red margin-top-md">
								&nbsp;
								<c:if test="${param.auth eq 'failure'}">
									<div id="loginErrorMsg" style="background-color:white; padding:0 10px; margin:5px 5px;"><%= lastException != null && lastException instanceof org.springframework.security.authentication.BadCredentialsException ? "&nbsp;&nbsp;&nbsp;<span style='color:#F2961B;'>Authentication failed!</span>" : "" %>&nbsp;</div>
							        <script type="text/javascript">
	                                setTimeout(function () {
	                                    $("div#loginErrorMsg").fadeTo(1000, 0);
	                                }, 2000);
	                                </script>
								</c:if>
							</div>
                            <button type="button" class="btn btn-primary btn-block btn-large" style="margin:20px 0;" onclick="window.location.href = 'index.jsp';">Return to public databases</button>
                            <fmt:message var="adminEmail" key="adminEmail" />
                            <c:if test='${!fn:startsWith(adminEmail, "??") && !empty adminEmail}'>
                                <p>Apply for an account at <a href="mailto:${adminEmail}?subject=metaXplor account request">${adminEmail}</a></p>
                            </c:if>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <script type="text/javascript" src="js/commons.js"></script>
    </body>
<%
	session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, null);
%>

</html>