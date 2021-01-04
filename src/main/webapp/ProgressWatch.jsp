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
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController,fr.cirad.tools.Helper" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
<head>
    <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
	<link rel="stylesheet" type="text/css" href="css/main.css">
    <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
	<script type="text/javascript" src="js/jquery.min.js"></script>
	<script type="text/javascript">
		var movingToViewerPage = false;
		var PROGRESS_INDICATOR_URL = "<c:url value='<%= MetaXplorController.PROGRESS_INDICATOR_URL%>' />";

		var destinationLink = "${param.successURL}";
		if (destinationLink.startsWith("<%=MetaXplorController.TMP_OUTPUT_FOLDER%>") && destinationLink.indexOf("PROCESS_ID_HASH") > 0)
			destinationLink = destinationLink.replace("PROCESS_ID_HASH", "<%=Helper.convertToMD5(request.getParameter("processId")) %>");

		var processAborted = false;
		
		function checkProcessProgress()
		{
			$.getJSON(PROGRESS_INDICATOR_URL, { module:'${param.module}',processId:'${param.processId}' }, function(jsonResult){
				if (jsonResult == null)
				{
					movingToViewerPage = true;
					$('#progress').html('<center><p style="margin-top:60px;" class="bold">No such process is running at the moment.</p><p>Refresh to try again or use the link below to access resulting data in case the process has already finished:<br/><br/><a style="cursor:pointer;" href="' + destinationLink + '">' + (/*fileName == '?' ?*/ destinationLink/* : fileName*/) + '</a></p></center>');
				}
				else
				{
					if (jsonResult['error'] != null)
					{
						alert("Error occured:\n\n" + jsonResult['error']);
						window.onbeforeunload = null;
						window.close();
					}
					else
					{
	                	if (processAborted)
	                		$('#progress').html('<center><p style="margin-top:60px;" class="bold">Process aborted</p></center>');
	                	else if (jsonResult['complete'] == true)
	                		$('#progress').html('<center><p style="margin-top:60px;" class="bold">Process has completed.<br/>Data is now <a style="cursor:pointer;" href="' + destinationLink + '">available here</a></p></center>');
	                	else {
							$('#progress').html('<center><p style="margin-top:60px;" class="bold">' + jsonResult['progressDescription'] + '</p></center>');
							setTimeout("checkProcessProgress()", 1000);	                		
	                	}
					}
				}
			})
			;
		}

		<c:if test='${param.abortURL ne null && param.abortURL ne ""}'>
		window.onbeforeunload = function(e) {
			if (movingToViewerPage == true)
				return;	// we only want to deal with the case when the window is being closed

			<c:if test='${param.emailNotificationSpecificationUrl ne null && param.emailNotificationSpecificationUrl ne ""}'>
			if ($("#notificationEmail").attr("disabled") == "disabled")
				return;	// we do not want to abort if the user specified a notification e-mail
			</c:if>
					
			$.ajax({
				url: '<c:url value="${param.abortURL}" />',
				async: false,
				traditional: true,
				data: { processId:"${param.processId}" },
				success: function(jsonResult){/* do nothing special */}
			});
		};
		</c:if>
	</script>
	<title>metaXplor process watcher</title>
</head>

<body style='background-color:#f0f0f0;' onload="$('button#abortButton').css('display', ${param.abortable eq 'true'} ? 'inline' : 'none'); checkProcessProgress();">
	<div style='background-color:white; width:100%; padding:5px;'>
		<b style='color:#337ab7;'>metaXplor</b> progress watcher
	</div>
	<div id='progress' style='margin-top:50px; width:100%; display:block; text-align:center;'>
		<p>This process is running as a background task.</p>
		<p>You may leave the main metaXplor page and either keep this one open or copy its URL to check again later.</p>
		<h4 id="progressText" class="loading-message" style='margin-top:50px'>Please wait...</h4>
		<button class="btn btn-danger btn-sm" id="abortButton" style="display:none;" type="button" name="abort" onclick="abort('${param.processId}');">Abort</button>
	</div>
	<script type="text/javascript" src="js/commons.js"></script>
</body>

</html>