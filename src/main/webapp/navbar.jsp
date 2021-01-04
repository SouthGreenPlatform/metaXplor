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
<%@page contentType="text/html" pageEncoding="UTF-8" import="org.springframework.security.core.context.SecurityContextHolder"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
	java.util.Properties prop = new java.util.Properties();
	prop.load(getServletContext().getResourceAsStream("/META-INF/MANIFEST.MF"));
	String appVersion = prop.getProperty("Implementation-version");
	String[] splitAppVersion = appVersion == null ? new String[] {""} : appVersion.split("-");
%>
<c:set var="appVersionNumber" value='<%= splitAppVersion[0] %>' />
<c:set var="appVersionType" value='<%= splitAppVersion.length > 1 ? splitAppVersion[1] : "" %>' />

<nav class="navbar navbar-inverse navbar-fixed-top" role="navigation">
   	<div style='display:inline; border:1px #101010 solid; border-left:0; background-color:white; height:102%; position:absolute;'>
  	 	<div style='font-size:12px; position:absolute; top:31px; right:75px;'>v${appVersionNumber}</div>
   		<a href="index.jsp"><img src="img/logo_metaxplor${param.color}.png" width='100' style="margin:7px 12px;" /></a>
   		<div style='font-size:10px; position:absolute; top:33px; left:76px;'>${appVersionType}</div>
   	</div>
    <div class="container" style='margin-left:150px;'>
        <div class="navbar-header">
            <button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#navbar">
                <span class="sr-only">Toggle navigation</span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
        </div>
        <div class="collapse navbar-collapse" id="navbar">
            <ul class="nav navbar-nav">
                <li>
                    <a href="main.jsp<c:if test='${param.module ne null}'>?module=${param.module}</c:if>" class="nav-link" data-toggle="tooltip" title="Search hosted assigned sequences by combining filters applying to imported fields">Explore data</a>
                </li>
                <li>
                    <a href="blast.jsp<c:if test='${param.module ne null}'>?module=${param.module}</c:if>" class="nav-link" data-toggle="tooltip" title="Find similarity between your own sequences and those hosted in the system">BLAST / Diamond</a>
                </li>
                <li>
                    <a href="pplacer.jsp<c:if test='${param.module ne null}'></c:if>" class="nav-link" data-toggle="tooltip" title="Place selected sequences on predefined reference phylogenetic trees">Phylogenetic assignment</a>
                </li>
                <li>
                    <a href="docIndex.jsp" class="nav-link" data-toggle="tooltip" title="Consult user and administrator documentation">Documentation</a>
                </li>
                <li>
                <c:if test="${userDao.canLoggedUserWriteToSystem()}">
	   				<a href="#" class="dropdown-toggle" data-toggle="dropdown" data-toggle="tooltip" title="Manage existing databases, import data into them, configure users' access to projects">Manage data</a>
	   				<ul class="dropdown-menu">
	                    <c:if test="${userDao.doesLoggedUserOwnEntities()}">
							<li><a href="<c:url value='/permissionManagement.jsp' />" data-placement="bottom">Administer existing data<br/>and user permissions</a></li>
						</c:if>
						<li><a href="import.jsp" id="import" onclick="window.location.href = this.href" data-placement="bottom">Import data</a></li>
					</ul>
                </c:if>
                </li>
                <li>
                <c:set var="loggedUser" value="<%= SecurityContextHolder.getContext().getAuthentication().getPrincipal()%>" />
                <c:choose>
                    <c:when test='${loggedUser eq "anonymousUser"}'>
                        <a href="login.jsp" class="nav-link" data-toggle="tooltip" title="Authenticate into the system">Login</a>
                    </c:when>
                    <c:otherwise>
                        <a href="<c:url value='/j_spring_security_logout' />" data-toggle="tooltip" data-placement="bottom" title="Log out ${loggedUser.username}" id="logOut">Log out</a>
                    </c:otherwise>
                </c:choose>
                </li>
            </ul>
        </div>

    </div>
</nav>