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
String sModule = request.getParameter("module");
if (sModule != null && !sModule.trim().isEmpty())
	response.sendRedirect("main.jsp?module=" + sModule);
%>
<!DOCTYPE html>
<%@page contentType="text/html" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController, fr.cirad.metaxplor.model.MetagenomicsProject"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
    <head>
        <meta charset="utf-8"/>
        <meta name="google" content="notranslate">
        <title>metaXplor</title>
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-select.min.css">
        <link rel="stylesheet" type="text/css" href="css/main.css" />
    </head>
    <body>
        <%@include file="navbar.jsp" %>
        <div class="container-fluid">
            <header class="jumbotron jumbotron-small text-center" style="padding-bottom:0">
                <div class="row" style="margin-top:55px; height:134px;">
                	<div class="col-md-4 col-sm-4 margin-top-sm" style='text-align:center;'>
						<img src="img/logo_metaxplor.png" width='300' />
		                <p class='nowrap'>Store, share, explore, manipulate metagenomic data</p>
		            </div>
		            <div class="col-md-1 col-sm-1"></div>
		            <div class="col-md-2 col-sm-2 nowrap" style='text-align:center; margin-top:10px;'>
			            <a href="http://www.cirad.fr/" target="_blank" class="margin-left"><img width='95' alt="CIRAD" src='img/logo_cirad.png'/></a><br/>&copy; CIRAD 2021
		                <div style="margin-top:25px;"><a class="btn btn-primary btn-large" href="main.jsp">Explore</a></div>
		            </div>
		            <div class="col-md-1 col-sm-1"></div>
		            <div class="col-md-4 col-sm-4" style='text-align:align:center;'>
		            	<div style="font-size:22px;">Current system figures</div>
		            	<div id="systemFigures"></div>
		            </div>
	            </div>
	            <div class="small nowrap" style="margin-top:15px; text-align:center;">
		            <span class='bold'>Please cite metaXplor as follows:</span>
					Guilhem Sempéré, Adrien Pétel, Magsen Abbé, Pierre Lefeuvre, Philippe Roumagnac, Frédéric Mahé, Gaël Baurens, Denis Filloux.<br/>
					<u>metaXplor: an interactive viral and microbial metagenomic data manager, GigaScience, Volume 10, Issue 2, February 2021, giab001</u>, <a href='https://doi.org/10.1093/gigascience/giab001' target='_blank'>https://doi.org/10.1093/gigascience/giab001</a>
				</div>
            </header>
            <div class="row text-center margin-top-sm">
                <div class="col-md-3 col-sm-6 hero-feature">
                    <div class="thumbnail">
                        <img src="img/krona.png" alt="" style='cursor:default;'>
                        <div class="caption">
                            <h4 class='bold'>Taxonomy</h4>
                            <p>Explore data taxonomy</p>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-sm-6 hero-feature">
                    <div class="thumbnail">
                        <img src="img/map.png" alt="" style='cursor:default;'>
                        <div class="caption">
                            <h4 class='bold'>Localisation</h4>
                            <p>Visualize samples on a map</p>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-sm-6 hero-feature">
                    <div class="thumbnail">
                        <img src="img/blast.png" alt="" style='cursor:default;'>
                        <div class="caption">
                            <h4 class='bold'>BLAST</h4>
                            <p>BLAST vs hosted sequences</p>
                        </div>
                    </div>
                </div>
                <div class="col-md-3 col-sm-6 hero-feature">
                    <div class="thumbnail">
                        <img src="img/phylo.png" alt="" style='cursor:default;'>
                        <div class="caption">
                            <h4 class='bold'>Phylogenetics</h4>
                            <p>Run phylogenetic assignment</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript" src="js/bootstrap.min.js"></script>
        <script type="text/javascript" src="js/bootstrap-select.min.js"></script>
        <script type="text/javascript">
        var projectData;
        var labels = [];
        var dataValue = [];
        $(document).ready(function () {
            $.ajax({
                url: "<c:url value='<%= MetaXplorController.GET_STATS_URL%>' />",
                method: "GET",
                success: function (jsonResult) {
					let figures = "";
					for (var key in jsonResult)
						figures += "\n" + key + ": " + jsonResult[key].toLocaleString();
                	$("#systemFigures").html("<pre style='overflow:hidden; '>" + figures + "</pre>");
                },
                error: function (xhr, status, error) {
                    var err = eval("(" + xhr.responseText + ")");
                    alert(err.Message);
                }
            });
            
            <c:if test='${param.warnAboutDefaultPassword}'>
            	alert("You are using the default administrator password. Please change it by selecting Manage data / Administer existing data and user permissions from the main menu.");
            </c:if>
        });
        </script>
        <script type="text/javascript" src="js/commons.js"></script>
    </body>
</html>
