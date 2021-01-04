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
<!DOCTYPE html>
<%@ page language="java" pageEncoding="UTF-8" import="fr.cirad.metaxplor.model.Accession,fr.cirad.metaxplor.model.Assignment,fr.cirad.metaxplor.model.SampleReadCount,fr.cirad.metaxplor.model.Sample,fr.cirad.metaxplor.model.Sequence,fr.cirad.metaxplor.model.DBField,fr.cirad.tools.mongo.DBConstant,fr.cirad.web.controller.metaxplor.MetaXplorController,fr.cirad.tools.AppConfig,fr.cirad.metaxplor.model.MetagenomicsProject,fr.cirad.metaxplor.model.Sample,fr.cirad.metaxplor.model.Sequence,fr.cirad.metaxplor.model.AssignedSequence" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<html>
    <head>
        <title>metaXplor</title>
        <meta charset="utf-8">
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/leaflet.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-select.min.css">
        <link rel="stylesheet" type="text/css" href="css/leaflet-areaselect.css">
        <link rel="stylesheet" type="text/css" href="css/main.css">
        <link rel="stylesheet" type="text/css" href="css/MarkerCluster.css">
        <link rel="stylesheet" type="text/css" href="css/MarkerCluster.Default.css">
        <link rel="stylesheet" type="text/css" href="css/L.Control.MousePosition.css">
        <link rel="stylesheet" type="text/css" href="css/Control.FullScreen.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-datepicker3.min.css">
        <link rel="stylesheet" type="text/css" href="css/style.min.css">
    </head>
    <body>
        <%@include file="navbar.jsp" %>
        <div class="container-fluid container-fluid-no-padding full-width" id="container" style="display:flex;">
            <div id="pageSpinner">
                <div id="pageSpinnerText">Please wait...</div>
            </div>
            <div id="menuwidget">
                <div class="margin-top">
                    <div class="col-md-12">
                        <div class="row  text-center">
                            <button id="searchButton" onclick='if ($("#dbList").val() == null) alert("A database must be selected!"); else doSearch();' class="btn btn-primary">Search</button>
							<a href="#" onclick='$("div#outputToolConfigDiv").modal("show");'><span class="glyphicon glyphicon-share" style="font-size:18px; margin:0 5px 0 25px; cursor:pointer; cursor:hand;" title="Click to configure online output tools"></span></a>
                            <button id="exportButton" disabled data-toggle="modal" class="btn btn-primary" onclick="$('#serverExportResult').html(''); $('#exportBtn').removeAttr('disabled'); exportFormatSelectionChanged(); $('#exportModal').modal({ backdrop:'static', keyboard:false, show:true });">Export</button>
                        </div>
                        <div class="row margin-top-sm" id="leftPanelPrimaryZone" hidden>
                            <div class="col-md-12">
                                <div class="form-group">
                                    <label for="dbList" id="dbListLabel" class="white">Database</label>
                                    <div class="form-input">
                                        <select id="dbList" class="selectpicker select-main" data-live-search="true" data-size="5"></select>
                                    </div>
                                </div>
                                <div class="form-group margin-top-sm">
                                    <label for="projectList" id="projectListLabel" class="white">Projects</label>
                                    <div class="form-input">
                                        <select id="projectList" class="selectpicker select-main" title="All projects" multiple data-live-search="true" data-size="5"></select>
                                    </div>
                                </div>
                                <div class="form-group margin-top-sm">
                                    <label for="filterList" id="filterListLabel" class="white">Filters</label>
                                    <div class="form-input">
                                        <select id="filterList" class="selectpicker select-main" multiple data-live-search="true" data-size="10"></select>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="row" style="min-height:450px; overflow-y:auto; padding-bottom:10px;">
                            <div id="leftPanelFilterZone" class="col-md-12"></div>
                        </div>
                    </div>
                </div>
            </div>
            <div id="resultDisplayZone">
                <div class="row margin-top" style="margin-left:-25px; margin-right:5px;">
                    <div class="col-md-3">
                        <div id="resultDisplayModes" hidden align='center'>
                            <div>Show results as</div>
                            <img id="tableResultModeButton" title="Dynamic table" src="img/resultsAsTable.png" onclick="toggleResultViewMode('table');"/>
                            <img id="treeResultModeButton" title="Taxonomy tree" src="img/resultsAsTree.png" onclick="toggleResultViewMode('tree');"/>
                            <img id="pieResultModeButton" title="Taxonomy pie" src="img/resultsAsPie.png" onclick="toggleResultViewMode('pie');"/>
                            <img id="mapResultModeButton" title="Sample collection location map" src="img/resultsAsMap.png" onclick="toggleResultViewMode('map');"/>      
                        </div>
                    </div>
                    <div class="col-md-9" id="noResultsMessage" hidden>No matching results</div>
                    <div class="col-md-9" id="resultTableNavigation" hidden>
                		<div class="row">
                			<div class="col-md-6" style="padding-left:0;">
		                        <div class="row center">
		                            <p id="resCount" class="bold"></p>
		                        </div>
		                        <div class="row center">
		                            <nav aria-label="Search results pages" style='height:40px;'>
		                                <ul id="pagination" class="no-margin"></ul>
		                            </nav>
		                        </div>
		                    </div>
	                        <div class="col-md-5" style='margin-top:-3px;'>
		                    	<div style="padding-left:0;">View results by</div>
								<div class="btn-group" role="group" id="resultTableEntityLevel" data-toggle="buttons">
								  	<label class="btn btn-sm btn-default fieldType_SQ active" onclick="setResultTableEntityLevel(sequenceType);"><input type="radio">sequence</label>
									<label class="btn btn-sm btn-default fieldType_SP" onclick="setResultTableEntityLevel(sampleType);"><input type="radio">sample</label>
								  	<label class="btn btn-sm btn-default fieldType_AS" onclick="setResultTableEntityLevel(assignmentType);"><input type="radio">assignment</label>
								  	<div id='highlightBestHits' style="top:30px; margin-left:113px; position:absolute; white-space:nowrap;"><input type='checkbox' onclick="if (entityLevel== assignmentType && checked) $('td.bestHitAssignment').addClass('highlighted'); else $('td.bestHitAssignment').removeClass('highlighted');" /><div class="bestHitAssignment highlighted" style='padding:2px; margin:-21px 15px; font-size:11px;'>highlight best hits</div></div>
								</div>
							</div>
	                        <div class="col-md-1">
								<div class="row" id="fieldSelectionPanel" style="position:absolute; margin-left:-275px; width:300px; margin-top:45px; z-index:5;" hidden>
									<div class="panel panel-default panel-bluegrey shadowed-panel" style="padding:10px 15px;">
										<div style='float:right; margin-top:-5px;'>
											<a href="javascript:collapseMultipleValues(true);" id="compactMultipleValues" title="Display a field's multiple values in compact mode" style="display:inline;"><img src="img/collapse.png" height="16" width="16"></a>
											<a href="javascript:collapseMultipleValues(false);" style="display:none;" id="expandedMultipleValues" title="Display a field's multiple values in expanded mode"><img src="img/expand.png" height="16" width="16"></a>
										</div>
										<div style='font-weight:bold;'>Please select fields to show in table</div>
										<div class="panel-body panel-center text-center" style="padding:0; text-align:left; max-height:500px; overflow-y:scroll;"></div>
									</div>
								</div>
								<span class="glyphicon glyphicon-list-alt margin-icon pointer" aria-hidden="true" style="font-size:20px; margin-top:20px;" onclick="$('#fieldSelectionPanel').toggle(); if (!$('#fieldSelectionPanel').is(':visible')) updateDisplayedFields();" title="Configure fields to display in table">
								</span>
                        	</div>
						</div>
                    </div>
                </div>
                <div class="row">
                    <div id="content">
                        <div id="dbProjectSummary">
                        </div>
                    </div>
                </div>
                <div style="float:right; display:none; margin:-60px 20px 0 0;" id="assignMethodForTaxoResultsDiv" data-toggle="tooltip" title="This kind of representation requires to select an assignment method for consistency reasons">Assignment method used<br/><select id="assignMethodForTaxoResults" class="selectpicker select-main" data-width="160px" onchange="$('#tree').jstree('destroy').empty(); $('#resultPieContainer').html(''); isBusySearching(true); loadResultTree($('#resultPieContainer').is(':visible'));"></select></div>
                <div class="row">
                    <div id="content">
                        <div id="resultTableContainer" hidden>
                            <table class="table searchResultTable" id="resultTable">
                                <thead id="tableHead"></thead>
                                <tbody id="tableBody"></tbody>
                            </table>
                        </div>
                        <div id="resultTreeContainer" class="margin-top-md" hidden>
                            <div style="position:absolute; color:#e55300; margin-left:170px; margin-top:-70px; width:400px;">
	                            <b>Numbers between brackets indicate sequence counts</b>
                            </div>
                            <div id="tree"></div>
                        </div>
                        <div id="resultPieContainer" class="margin-top-md" hidden></div>
                        <div id="resultMapContainer" class="margin-top-md" hidden>
                        	<div id="mapInfo"></div>
                            <div id="map"></div>
                        </div>
                    </div>
                </div>
            </div>
            <div id="sequenceInfoDialog" class="modal" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-lg" role="document">
                    <div class="modal-content">
                        <div class="modal-body" id="sequenceInfoBody">
                        	<div id="seqDetailContent" class="margin-bottom-sm"></div>
                        </div>
                        <div class="modal-footer center">
                            <button type="button" class="btn" data-dismiss="modal" onclick="clearDetailContent();">Close</button>
                        </div>
                    </div>
                </div>
            </div>
            <div id="treeModal" class="modal" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-lg" role="document">
                    <div class="modal-content">
                        <div class="form-group margin-top-sm margin-left-sm">
              				<div style="float:right; display:none; margin:0 25px;" id="assignMethodForTaxoFilterDiv" data-toggle="tooltip" title="This kind of representation requires to select an assignment method for consistency reasons">Assignment method used<br/><select id="assignMethodForTaxoFilter" class="selectpicker select-main" data-width="160px" onchange="$('#treebox' + taxonFieldId).click();"></select></div>
                            <div style="float:right; color:#e55300; margin:0 25px; width:500px;"><b>Numbers between brackets indicate sequence counts</b>
                            <span id='seqCountExplanation' hidden><b>for the selected assignment method.</b> Your search might lead to more results unless you also select this assignment method<span hidden id="bestHitMention"> along with the best-hit flag</span> in the search filters.</span>
                            </div>
                            <label for="treeSearch" id="exportFormatLabel">Enter taxon name</label>
                            <div class="form-input">
                                <input id="treeSearch" type="text" placeholder="search">
                            </div>
                        </div>
                        <div id="treeContent"></div>
                        <div class="modal-footer center">
                            <button id="choose" type="button" class="btn btn-primary">Choose</button>
                            <button type="button" class="btn" data-dismiss="modal">Close</button>
                        </div>
                    </div>
                </div>
            </div>
            <div id="exportModal" class="modal" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-sm center margin-top-sm" role="document">
                    <div class="modal-content">
                        <div class="form-group">
                        	<br/>
                            <label for="exportFormat" id="exportFormatLabel">Select file to export</label>
                            <div class="form-input">
                                <select id="exportFormat" class="selectpicker" data-width="250px" onchange="exportFormatSelectionChanged();">
                                    <option value="<%=Sample.TYPE_ALIAS%>">Sample file (tsv)</option>
                                    <option value="<%=AssignedSequence.TYPE_ALIAS%>">Sequence composition file (tsv)</option>
                                    <option value="FA">Sequence file (fasta)</option>
                                    <option value="<%=AssignedSequence.FIELDNAME_ASSIGNMENT%>">Assignment file (tsv)</option>
                                    <option value="<%=MetaXplorController.EXPORT_FORMAT_BIOM%>">BIOM file (json)</option>
                                </select>
                                <div class="margin-top-sm" style="height:50px;">
                                	<div id="exportToServerZone">
	                                	<input type="checkbox" id="exportToServer" />
	                                	<label for="exportToServer">Create URL on server</label>
	                                	<span style="display:none;"> (required for phylogenetic assignment)</span>
	                                	<div style="display:none; margin-top:7px; width:100%;" data-toggle="tooltip" title="This kind of representation requires to select an assignment method for consistency reasons"><center>Assignment method used: <select id="biomAssignMethod" class="btn btn-default"></select></center></div>
                                	</div>
                                </div>
                            </div>
                        </div>
                        <div class="margin-top-md center">
                            <button id="exportBtn" type="button" class="btn btn-primary">Export</button>
                            <button id='closeExportBox' type="button" class="btn" data-dismiss="modal" onclick="isBusySearching(false, true); $('#asyncProgressLink').hide();">Close</button>

                        <div id="serverExportResult" style='text-align:center; background-color:#eeeeee;' class='padding-sm margin-top-md margin-bottom-sm'></div>
                    </div>
                </div>
                <a id="file" hidden></a>
	            </div>
	        </div>
			<!-- modal which displays a box for configuring online output tools -->
			<div id="outputToolConfigDiv" class="modal" tabindex="-1" role="dialog">
				<div class="modal-dialog modal-large" role="document">
				<div class="modal-content" style="padding:10px; text-align:center;">
					<b>Configure this to be able to push exported data into external online tools</b><br />
					(feature available when the 'Create URL on server' box is ticked)<br /><br />
					<p class='bold'>Configuring external tool <select id="onlineOutputTools" onchange="configureSelectedExternalTool();"></select></p>
					<br />Online tool URL (any * will be replaced with exported file location)<br />
					<input type="text" style="font-size:11px; width:400px; margin-bottom:5px;" onfocus="$(this).prop('previousVal', $(this).val());" onkeyup="checkIfOuputToolConfigChanged();" id="outputToolURL" placeholder="http://some-tool.org/import?fileUrl=*" />
					<p>
						<input type="button" style="float:right; margin:10px;" class="btn btn-sm btn-primary" disabled id="applyOutputToolConfig" value="Apply" onclick='if ($("input#outputToolURL").val().trim() == "") { localStorage.removeItem("outputTool_" + $("#onlineOutputTools").val()); configureSelectedExternalTool(); } else localStorage.setItem("outputTool_" + $("#onlineOutputTools").val(), JSON.stringify({"url" : $("input#outputToolURL").val()})); $(this).prop("disabled", "disabled");' />
						<br/>
						(Set URL blank to revert to default)
					</p>
				</div>
				</div>
			</div>
        </div>

       	<div style="display:none;" class='center' id="asyncProgressLink">
			<button class="btn btn-info btn-sm" onclick="$('#closeExportBox').removeAttr('disabled'); window.open('ProgressWatch.jsp?processId=' + currentProcessId + '&successURL=' + TMP_OUTPUT_FOLDER + '/PROCESS_ID_HASH/' + exportFileName);" title="This will open a separate page allowing to watch export progress at any time. Leaving the current page will not abort the export process.">Open async progress watch page</button>
		</div>
			
        <script type="text/javascript" src="js/jquery.min.js"></script>
		<script type="text/javascript" src="js/jquery.binarytransport.js"></script>
        <script src="js/jquery-ui.min.js"></script>
        <script type="text/javascript" src="js/jstree.min.js" async></script>
        <script type="text/javascript" src="js/bootstrap.min.js"></script>
        <script src="js/bootstrap-select.min.js"></script>
        <script type="text/javascript" src="js/spin.min.js"></script>
        <script type="text/javascript" src="js/jquery.slimscroll.min.js"></script>
        <script type="text/javascript" src="js/bootstrap-datepicker.js"></script>
        <script type="text/javascript" src="js/leaflet.js"></script>
        <script type="text/javascript" src="js/leaflet.markercluster.js"></script>
        <script type="text/javascript" src="js/Control.FullScreen.min.js"></script>
        <script type="text/javascript" src="js/leaflet-areaselect-min.js"></script>
        <script type="text/javascript" src="js/L.Control.MousePosition-min.js"></script>
        <script type="text/javascript" src="js/moment.min.js" async></script>
        <script type="text/javascript" src="js/jquery.twbsPagination.min.js" async></script>
        <script type="text/javascript">
	        var FIELDNAME_PROJECT = "<%=DBConstant.FIELDNAME_PROJECT%>";
	        var FIELDNAME_ASSIGNMENT_METHOD = "<%=Assignment.FIELDNAME_ASSIGN_METHOD%>";
	        var FIELDNAME_TAXON = "<%=DBConstant.FIELDNAME_TAXON%>";
	        var FIELDNAME_SAMPLE_COMPOSITION = '<%=Sequence.FIELDNAME_SAMPLE_COMPOSITION%>';
	        var FIELDNAME_SAMPLE_CODE = "<%=SampleReadCount.FIELDNAME_SAMPLE_CODE%>";
	        var FIELDNAME_SAMPLE_WEIGHT = "<%=SampleReadCount.FIELDNAME_SAMPLE_COUNT%>";
	        var FIELDNAME_NAME = "<%=MetagenomicsProject.FIELDNAME_NAME%>";
	        var FIELDNAME_ACRONYM = "<%=MetagenomicsProject.FIELDNAME_ACRONYM%>";
	        var EXPORT_FILENAME_SP = "<%=MetaXplorController.EXPORT_FILENAME_SP%>.zip";
	        var EXPORT_FILENAME_FA = "<%=MetaXplorController.EXPORT_FILENAME_FA%>.zip";
	        var EXPORT_FILENAME_SQ = "<%=MetaXplorController.EXPORT_FILENAME_SQ%>.zip";
	        var EXPORT_FILENAME_AS = "<%=MetaXplorController.EXPORT_FILENAME_AS%>.zip";
	        var EXPORT_FILENAME_BM = "<%=MetaXplorController.EXPORT_FILENAME_BM%>.zip";
	        var INTERFACE_CLEANUP_URL = '<c:url value="<%= MetaXplorController.SEARCH_INTERFACE_CLEANUP_URL%>"/>';
	        var MODULE_LIST_URL = '<c:url value="<%=MetaXplorController.MODULE_LIST_URL%>" />';
	        var MODULE_PROJECT_LIST_URL = "<c:url value="<%=MetaXplorController.MODULE_PROJECT_LIST_URL%>" />";
	        var SEARCHABLE_FIELD_LIST_URL = "<c:url value="<%=MetaXplorController.SEARCHABLE_FIELD_LIST_URL%>"/>";
	        var SEARCHABLE_FIELD_INFO_URL = "<c:url value='<%= MetaXplorController.SEARCHABLE_FIELD_INFO_URL%>' />";
	        var SEARCHABLE_LIST_FIELD_LOOKUP_URL = "<c:url value="<%=MetaXplorController.SEARCHABLE_LIST_FIELD_LOOKUP_URL%>"/>";
	        var SAMPLE_EXPORT_URL = '<c:url value="<%=MetaXplorController.SAMPLE_EXPORT_URL%>" />';
	        var SEQUENCE_EXPORT_URL = '<c:url value="<%=MetaXplorController.SEQUENCE_EXPORT_URL%>" />';
	        var SEQ_COMPO_EXPORT_URL = '<c:url value="<%=MetaXplorController.SEQ_COMPO_EXPORT_URL%>" />';
	        var ASSIGNMENT_EXPORT_URL = '<c:url value="<%=MetaXplorController.ASSIGNMENT_EXPORT_URL%>" />';
	        var BIOM_EXPORT_URL = '<c:url value="<%=MetaXplorController.BIOM_EXPORT_URL%>" />';
	        var PROGRESS_INDICATOR_URL = "<c:url value='<%= MetaXplorController.PROGRESS_INDICATOR_URL%>' />";
	        var GPS_TYPE = '<%=DBConstant.GPS_TYPE%>';
	        var STRING_TYPE = '<%=DBConstant.STRING_TYPE%>';
	        var STRING_ARRAY_TYPE = '<%=DBConstant.STRING_ARRAY_TYPE%>';
	        var DOUBLE_TYPE = '<%=DBConstant.DOUBLE_TYPE%>';
	        var DATE_TYPE = '<%=DBConstant.DATE_TYPE%>';
	        var MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT = "<c:url value="<%=MetaXplorController.MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT%>"/>";
	        var CREATE_TEMP_VIEW_URL = "<c:url value="<%=MetaXplorController.CREATE_TEMP_VIEW_URL%>"/>";
	        var RECORD_COUNT_URL = '<c:url value="<%=MetaXplorController.RECORD_COUNT_URL%>" />';
	        var RECORD_SEARCH_URL = '<c:url value="<%=MetaXplorController.RECORD_SEARCH_URL%>" />';
	        var SEQUENCE_DETAILS_URL = '<c:url value="<%= MetaXplorController.SEQUENCE_DETAILS_URL%>"/>'
	        var SAMPLE_DETAILS_URL = '<c:url value="<%= MetaXplorController.SAMPLE_DETAILS_URL%>"/>'
	        var GPS_POSITION_URL = "<c:url value="<%=MetaXplorController.GPS_POSITION_URL%>"/>";
	        var TMP_OUTPUT_FOLDER = "<c:url value="<%=MetaXplorController.TMP_OUTPUT_FOLDER%>"/>";
	        var FIELDNAME_COLLECT_GPS = "<%=Sample.FIELDNAME_COLLECT_GPS%>";
	        var TAXO_TREE_URL = "<c:url value="<%=MetaXplorController.TAXO_TREE_URL%>"/>";
	        var NUCL_SEQUENCE_URL = "<c:url value="<%=MetaXplorController.NUCL_SEQUENCE_URL%>"/>";
	        var ONLINE_OUTPUT_TOOLS_URL = "<c:url value="<%=MetaXplorController.ONLINE_OUTPUT_TOOLS_URL%>"/>";
	        var selectedModule;
	        var sessionId = "${pageContext.session.id}";
	        var projectType = "<%=MetagenomicsProject.TYPE_ALIAS%>";
	        var sampleType = "<%=Sample.TYPE_ALIAS%>";
	        var fastaType = "FA";
	        var biomFormatType = "<%=MetaXplorController.EXPORT_FORMAT_BIOM%>";
	        var sequenceType = "<%=AssignedSequence.TYPE_ALIAS%>";
	        var assignmentType = "<%=AssignedSequence.FIELDNAME_ASSIGNMENT%>";
	        var entityLevel = sequenceType;
	        var seqLengthFieldId = <%= DBField.seqLengthFieldId %>;
	        var gpsPosFieldId = <%= DBField.gpsPosFieldId %>;
	        var qseqidFieldName = "<%=Sequence.FIELDNAME_QSEQID%>";
	        var sseqidFieldName = "<%=Assignment.FIELDNAME_SSEQID%>";
	        var bestHitFieldName = "<%=DBField.bestHitFieldName%>";
	        var assignMethodFieldName = "<%=Assignment.FIELDNAME_ASSIGN_METHOD%>";
	        var sseqidFieldId = "<%=DBField.sseqIdFieldId%>";
	        var sampleCompoFieldName = "<%=Sequence.FIELDNAME_SAMPLE_COMPOSITION%>";
	        var sampleCodeFieldName = "<%=SampleReadCount.FIELDNAME_SAMPLE_CODE%>";
	        var pjField_acronym = "<%=MetagenomicsProject.FIELDNAME_ACRONYM%>";
	        var pjField_assemblyMethod = "<%=MetagenomicsProject.FIELDNAME_ASSEMBLY_METHOD%>";
	        var pjField_seqMethod = "<%=MetagenomicsProject.FIELDNAME_SEQUENCING_TECHNOLOGY%>";
	        var pjField_seqDate = "<%=MetagenomicsProject.FIELDNAME_SEQUENCING_DATE%>";
	        var pjField_authors = "<%=MetagenomicsProject.FIELDNAME_AUTHORS%>";
	        var pjField_contact = "<%=MetagenomicsProject.FIELDNAME_CONTACT_INFO%>";
	        var pjField_samplesAvailable = "<%=MetagenomicsProject.FIELDNAME_DATA_AVAIL%>";
	        var pjField_desc = "<%=MetagenomicsProject.FIELDNAME_DESCRIPTION%>";
	        var pjField_extraInfo = "<%=MetagenomicsProject.FIELDNAME_META_INFO%>";
	        var pjField_name = "<%=MetagenomicsProject.FIELDNAME_NAME%>";
	        var pjField_reference = "<%=MetagenomicsProject.FIELDNAME_PUBLICATION%>";
	        var ACCESSION_ID_NUCL_PREFIX = "<%=Accession.ID_NUCLEOTIDE_PREFIX%>";
	        var ACCESSION_ID_PROT_PREFIX = "<%=Accession.ID_PROTEIN_PREFIX%>";
        </script>
        <script type="text/javascript" src="js/main.js"></script>
        <script type="text/javascript" src="js/commons.js"></script>
		<script type="text/javascript" src="js/ajax-bootstrap-select.js"></script>
    </body>
</html>