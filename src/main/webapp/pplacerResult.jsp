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
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController"%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>metaXplor - Archaeopteryx</title>
    <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
    <link rel="stylesheet" type="text/css" href="css/jquery-ui.min.css">
    <link rel="stylesheet" href="css/forester.css">
</head>
<body>
    <h1 id="title"></h1>
    <div id="content"></div>
    <div id="controls0" class="ui-widget-content"></div>
    <script type="text/javascript" src="js/d3.min.js" ></script> 
    <script type="text/javascript" src="js/jquery.min.js"></script>
    <script type="text/javascript" src="js/jquery-ui.min.js"></script>
 
    <!-- SAX XML parser:-->
    <script src="js/dependencies/sax.js"></script>

    <!-- The following five libraries are needed for download/export of images and files:-->
    <script src="js/dependencies/rgbcolor.js"></script>
    <script src="js/dependencies/Blob.js"></script>
    <script src="js/dependencies/canvas-toBlob.js"></script>
    <script src="js/dependencies/canvg.js"></script>
    <script src="js/dependencies/FileSaver.js"></script>

    <!-- Archaeopteryx.js requires forester.js and phyloxml_parser.js:-->
    <script src="js/phyloxml_0_912.js"></script>
    <script src="js/forester_1_8.js"></script>
    <script src="js/archaeopteryx_1_8.js"></script>

    <script type="text/javascript" src="js/spin.min.js"></script>
    <script type="text/javascript">
        var spinner;
        $(document).ready(function () {
            spinner = new Spinner({color: "#fff"}).spin(document.getElementById('content'));
            
            var options = {};
            options.alignPhylogram = true;
            options.branchColorDefault = '#909090';
            options.branchDataFontSize = 6;
            options.branchWidthDefault = 1;
            options.collapasedLabelLength = 7;
            options.dynahide = true;
            options.externalNodeFontSize = 9;
            options.internalNodeFontSize = 6;
            options.minBranchLengthValueToShow = 0.01;
            options.minConfidenceValueToShow = 0.5;
            options.nodeSizeDefault = 2;
            options.phylogram = true;
            options.searchIsCaseSensitive = false;
            options.searchIsPartial = true;
            options.searchUsesRegex = false;
            options.showBranchEvents = true;
            options.showBranchLengthValues = false;
            options.showConfidenceValues = true;
            options.showDisributions = true;
            options.showExternalLabels = true;
            options.showExternalNodes = false;
            options.showInternalLabels = false;
            options.showInternalNodes = false;
            options.showNodeEvents = true;
            options.showNodeName = true;
            options.showSequence = true;
            options.showSequenceAccession = true;
            options.showSequenceGeneSymbol = true;
            options.showSequenceName = true;
            options.showSequenceSymbol = true;
            options.showTaxonomy = false;
            options.showTaxonomyCode = true;
            options.showTaxonomyCommonName = true;
            options.showTaxonomyRank = true;
            options.showTaxonomyScientificName = true;
            options.showTaxonomySynonyms = true;

            var settings = {};
            settings.border = '1px solid #909090';
            settings.controls0Top = 10;
            settings.enableDownloads = true;
            settings.enableBranchVisualizations = false;
            settings.enableCollapseByBranchLenghts = false;
            settings.enableCollapseByFeature = true;
            settings.enableNodeVisualizations = false;
            settings.nhExportWriteConfidences = true;
            settings.rootOffset = 140;
            settings.controlsFontSize = 10;

            $.ajax({
                url: "<c:url value='<%= MetaXplorController.GUPPY_FAT_RESULT_BY_JOBID_READER_URL%>' />",
                data: {"module": getParam('module'), "processId": getParam('processId')},
                success: function (xmlResult) {
                    var tree = null;
                    try {
                        tree = archaeopteryx.parsePhyloXML(xmlResult);
                    } catch (e) {
                        alert("error while parsing tree: " + e);
                    }
                    if (tree) {
                        try {
                            archaeopteryx.launch('#content', tree, options, settings);
                        } catch (e) {
                            alert("error while launching archaeopteryx: " + e);
                        }
                    }
                    spinner.stop();
                },
	            error: function (xhr, ajaxOptions, thrownError) {
	            	handleError(xhr);
                    spinner.stop();
	            }
            });
            
            <c:if test="${param.module ne null && param.module ne ''}">
            $.ajax({
                url: "<c:url value='<%= MetaXplorController.GUPPY_COUNT_UNSAVED_CLASSIFY_RESULT_BY_JOBID_READER_URL%>' />",
                data: {"module": getParam('module'), "processId": getParam('processId')},
                success: function (assignmentCount) {
                	if (assignmentCount > 0) {
                		let divContents = '<div id="saveAssignments" style="cursor:pointer; position:absolute; background-color:yellow; padding:2px; padding-bottom:0; top:17px; left:109px;" onclick="saveAssignments(' + assignmentCount + ');">';
                		divContents += '<img width="25" class="blink" src="img/save-to-db.png" title="Click to save new assignments to database" /></div>';
    					$("body").append(divContents);
                	}
                },
	            error: function (xhr, ajaxOptions, thrownError) {
	            	handleError(xhr);
                    spinner.stop();
	            }
            });
            </c:if>
        });

        function saveAssignments(assignmentCount) {
            clearInterval(blinkProcess);
            if (confirm("The system found pplacer + guppy assignments for " + assignmentCount + " sequences. Do you want to add them to the database?")) {
                $("div#saveAssignments").html("Adding assignments...");
                $.ajax({
                    url: "<c:url value='<%= MetaXplorController.GUPPY_SAVE_CLASSIFY_RESULT_BY_JOBID_READER_URL%>' />",
                    data: {"module": getParam('module'), "processId": getParam('processId')},
                    success: function (n) {
                        $("div#saveAssignments").html(n + " assignments added");
                        $("div#saveAssignments").fadeOut(3000);
                    },
                    error: function (xhr, ajaxOptions, thrownError) {
                        handleError(xhr);
                        spinner.stop();
                    }
                });
            }
            clearInterval(blinkProcess);
        }

        function handleError(xhr) {
        	if (!xhr.getAllResponseHeaders())
        		return;	// user is probably leaving the current page

          	var errorMsg;
          	if (xhr != null && xhr.responseText != null) {
          		try {
          			errorMsg = $.parseJSON(xhr.responseText)['errorMsg'];
          		}
          		catch (err) {
          			errorMsg = xhr.responseText;
          		}
          	}
          	alert(errorMsg);
        }

		function getParam(param) {
		    var vars = {};
		    window.location.href.replace(location.hash, '').replace(
		        /[?&]+([^=&]+)=?([^&]*)?/gi,
		        function (m, key, value) {
		            vars[key] = value !== undefined ? value : '';
		        }
		        );
		    if (param) {
		        return vars[param] ? vars[param] : null;
		    }
		    return vars;
		}

		function blink_text() {
		    $('.blink').fadeOut(500);
		    $('.blink').fadeIn(500);
		}
		var blinkProcess = setInterval(blink_text, 1000);
	</script>
	<script type="text/javascript" src="js/commons.js"></script>
</body>
</html>
