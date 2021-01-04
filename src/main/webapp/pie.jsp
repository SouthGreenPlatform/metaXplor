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
<%
response.addHeader("X-XSS-Protection","0"); // avoids the "The XSS Auditor blocked access" error on some browsers
%>
<%@ taglib uri = "http://java.sun.com/jsp/jstl/functions" prefix = "fn" %>
<!DOCTYPE html>
<html>
<head>
    <title>pie</title>
    <meta charset="utf-8" />
    <script id="notfound">
        window.onload = function () {
            document.body.innerHTML = "Could not get resources from \"http://krona.sourceforge.net\".";
        };
    </script>
    <style>
        input#linkButton {
            display: none;
        }
    </style>
    <script src="js/krona-min.js"></script>
</head>
<body style='background-color: #dcddde;'>
    <img id="hiddenImage" src="img/hidden.png" hidden />
    <img id="loadingImage" src="img/loading.gif" hidden />
    <img id="logo" src="img/krona-logo-small.png" hidden />
    <noscript>Javascript must be enabled to view this page.</noscript>
    <div hidden>
        <krona key="true" collapse="true"> 
            <attributes magnitude="magnitude">
                <list>members</list> 
                <attribute display="Total">magnitude</attribute>
            </attributes>
            <datasets>
                <dataset>example.krona_input</dataset>
            </datasets>
            ${param.pieData}
        </krona>
    </div>
</body>
</html>