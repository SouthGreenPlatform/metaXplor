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
<%@ page language="java" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController, fr.cirad.tools.AppConfig,fr.cirad.metaxplor.model.MetagenomicsProject" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
    <head>
		<meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate" />
		<meta http-equiv="Pragma" content="no-cache" />
		<meta http-equiv="Expires" content="0" />
        <title>import</title>
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-select.min.css">
        <link type="text/css" rel="stylesheet" href="css/dropzone.css" />
        <link rel="stylesheet" type="text/css" href="css/main.css">
        <script type="text/javascript" src="js/dropzone.js"></script>
    </head>
    <body>
        <%@include file="navbar.jsp" %>
        <div id="pageSpinner"><div id="pageSpinnerText">Please wait...</div></div>
        <div class="container-fluid">
            <div class="col-md-1"></div>
            <div class="col-md-10" style='padding:0;'>
                <div class="jumbotron margin-top-sm" style="padding-bottom:20px;">
                    <div class="row">
                       <div>
                           <h3>
                           	Import a new project into database
                           	<select id="dbList" name="module" class="selectpicker select-main" data-live-search="true" data-size="10" onchange="$('#module').val($(this).val());"></select>
                           </h3>
                           <div align='right' style='margin:-50px 0 10px 0; font-style:italic;'>Fields with <span class="text-red">*</span><br/>are mandatory</div>
                       </div>

	                    <form autocomplete="off" class="dropzone" id="importDropzone" action="<c:url value='<%= MetaXplorController.IMPORT_CONTENT_CHECK_URL%>' />" method="post">
	                   	<input type="hidden" id="processId" name="processId" />
	                   	<input type="hidden" id="module" name="module" />
	                       	<div class="margin-top-md row small">
	                               <div class="form-group col-md-3" data-toggle="tooltip" data-placement="bottom" title="Short acronym, must be unique (avoid special characters)">
	                                   <label id="codeLabel" for="projectCode" class="form-control-label">Project code<span class="text-red"> *</span></label>
	                                   <input type="text" class="form-control input-sm" value="" id="projectCode" name="code">
	                               </div>
	                               <div class="form-group col-md-7" data-toggle="tooltip" data-placement="bottom" title="Full project name">
	                                   <label id="nameLabel" for="projectName" class="form-control-label">Project name<span class="text-red"> *</span></label>
	                                   <input type="text" class="form-control input-sm" value="" id="projectName" name="name">
	                               </div>
	                               <div class="form-group col-md-2" data-toggle="tooltip" data-placement="bottom" title="Access level for this data (see 'Permission rules' section in documentation for more details)">
	                                   <label for="access" class="form-control-label">Project access<span class="text-red"> *</span></label>
										<br/>
	                                       <label class="form-check-label">
	                                           <input class="form-check-input" type="radio" name="access" id="public" value="true" checked> public
	                                       </label>
	                                       &nbsp;
	                                       <label class="form-check-label">
	                                           <input class="form-check-input" type="radio" name="access" id="private" value="false"> private
	                                       </label>
	                               </div>
							</div>
							<div class="row small">
	                               <div class="form-group col-md-8 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="You may provide as much information as you wish here">
	                                   <label for="description" class="form-control-label">Project description</label>
	                                   <textarea class="form-control input-sm" id="description" name="description" rows="4" style='height:97px;'></textarea>
	                               </div>
	                               <div class="form-group col-md-4 margin-top-sm">
	                               		<div data-toggle="tooltip" data-placement="bottom" title="Information for contacting the person in charge of this dataset (e-mail address, ORCID...)">
		                                   <label for="contact" class="form-control-label">Contact information</label>
		                                   <input type="text" class="form-control input-sm" value="" id="adress" name="adress">
	                               		</div>
	                               		<div data-toggle="tooltip" data-placement="bottom" class='margin-top-sm' title="Name / description of sequencing technology">
		                                   <label for="seqTech" class="form-control-label">Sequencing technology</label>
		                                   <input type="text" class="form-control input-sm" value="" id="seqTech" name="seqTech">
		                                </div>
	                               </div>
							</div>
							<div class="row small">
	                               <div class="form-group col-md-6 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Names of persons who were involved in producing this dataset">
	                                   <label for="authors" class="form-control-label">Authors</label> 
	                                   <textarea class="form-control input-sm" id="authors" name="authors"></textarea>
	                               </div>
	                               <div class="form-group col-md-2 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Number of accessions to take into account when several are provided for a single assignment">
	                                   <label for="maxAcc" class="form-control-label">Max # accessions per assignment</label>
	                                   <select class="form-control input-sm" id="maxAcc" name="maxAcc">
	                                   	<option value="1">1</option>
	                                   	<option selected value="5">5</option>
	                                   	<option value="10">10</option>
	                                   	<option value="20">20</option>
	                                   </select>
	                               </div>
	                               <div class="form-group col-md-2 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Date when sequencing was performed">
	                                   <label id="dateLabel" for="seqDate" class="form-control-label">Sequencing date<br/>(yyyy-MM-dd)</label>
	                                   <input type="text" class="form-control input-sm" value="" id="seqDate" name="seqDate">
	                               </div>
	                               <div class="form-group col-md-2 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Please indicate whether or not original samples are still physically avaialble">
	                                   <label for="checkbox" class="form-control-label">Samples available? <span class="text-red"> *</span></label>
	                                   <div class="form-check form-check-inline" id="checkbox">
	                                       <label class="form-check-label">
	                                           <input class="form-check-input" type="radio" name="avail" id="availTrue" value="true"> Yes
	                                       </label>
	                                   </div>
	                                   <div class="form-check form-check-inline">
	                                       <label class="form-check-label">
	                                           <input class="form-check-input" type="radio" name="avail" value="false" checked> No
	                                       </label>
	                                   </div>
	                               </div>
							</div>
							<div class="row small">
                               <div class="form-group col-md-4 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Assembly method if applicable (Abyss, CAP3, SPAdes, Velvet, ...)">
                                   <label for="assemblTech" class="form-control-label">Assembly method</label>
                                   <textarea class="form-control input-sm" id="assemblTech" name="assemblTech"></textarea>
                               </div>
                               <div class="form-group col-md-4 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="References or links to publications associated with the data">
                                   <label for="refPub" class="form-control-label">Publication reference(s)</label>
                                   <textArea class="form-control input-sm" id="pub" name="pub"></textarea>
                               </div>
                               <div class="form-group col-md-4 margin-top-sm" data-toggle="tooltip" data-placement="bottom" title="Any additional information / comments">
                                   <label for="extra" class="form-control-label">Other informations</label>
                                   <textArea class="form-control input-sm" id="extra" name="extra"></textarea>
                               </div>
							</div>
							<div class="row small" style="margin-left:-20px; margin-right:-20px;">
								<div class="col-md-3"><div style='width:200px; background-color:#fff9dd; border-radius:8px; padding:4px; text-align:center; font-size:13px; border:1px solid #eeddbb; margin-top:15px;'>We <span style='color:red;'>strongly advise</span> to use, whenever possible, <span style='color:red;'>standard attribute names</span> such as defined in the <a href='https://www.ncbi.nlm.nih.gov/biosample/docs/attributes/' target='_blank'>BioSample database</a>.</div></div>
	                   	        <div class="col-md-6 margin-top-sm" style="margin-left:20px;">
									<div class="dz-default dz-message">
	    								Please drop project archive here 
	    								<div style='margin-top:10px;'>(or click to browse filesystem)</div>
	 								</div>
		                            <div class="text-red" style="float:right; margin-top:-18px; margin-right:8px;">You may upload up to <span id="maxUploadSize" class="text-bold"></span> Mb. <span id="maxImportSizeSpan"></span></div>
	                            </div>
	                            <div class="col-md-3" id="dropZonePreviews" style="margin-left:-20px;"></div>
							</div>
							<div class="row small">
	                            <div class="col-md-8 margin-top-sm">
	                                <input class="form-control input-sm" id="dataPath" name="dataPath" type="text" style="width:100%;" placeholder="Alternatively, project archive URI"/>
	                            </div>
	                            <div class="col-md-4 margin-top-sm"style="text-align:right;">
									<button class="btn btn-primary btn-sm" id="checkProjectDataButton" type="button">Check</button>
                            		&nbsp;<label for="dontCheckAllEntries" class="form-control-label" title="Un-tick this box to check all data (may take long!)">only first 10,000 entries</label>
                            		<input type='checkbox' name="dontCheckAllEntries" id="dontCheckAllEntries" value="true" checked title="Un-tick this box to check all data (may take long!)" />
	                            </div>
	                       	 </div>
				        </form>
	                </div>
	        	  </div>
	       	  </div>
          </div>
          <script type="text/javascript" src="js/jquery.min.js"></script>
          <script type="text/javascript" src="js/spin.min.js"></script>
          <script type="text/javascript" src="js/jquery.ui.widget.min.js"></script>
          <script type="text/javascript" src="js/jquery-file-upload.min.js"></script>
          <script type="text/javascript" src="js/jquery.iframe-transport.min.js"></script>
          <script type="text/javascript" src="js/bootstrap.min.js"></script>
          <script type="text/javascript" src="js/bootstrap-select.min.js"></script>
          <script type="text/javascript" src="js/CollapsibleLists.min.js"></script>
          <script type="text/javascript">
          var expectedHeaders = [];
          var spinner;
          var projName;
          var projCode;
          var interval = 1000;
          var currentProgressCheckProcess;
          var pageLoadTime = new Date().getTime();
          var maxUploadSizeInMb, maxImportSizeInMb;
          var maxUploadSizeURL = '<c:url value="<%=MetaXplorController.MAX_UPLOAD_SIZE_PATH%>"/>';

     	  	$.ajaxSetup({
       		    converters: {
       		        "text json" : function(response) {
       		            return (response == "") ? null : JSON.parse(response);
       		        },
       		    },
       		});
     	  	
     	  	$(document).ready(function () {
          	$('#dbList').change(function () {
          		fillAvailableDb($("#dbList").val());
          		$('#projectName').change();
          		$('#projectCode').change();
          	});

           	$.ajax({
         	   async: false,
               url: '<c:url value="<%=MetaXplorController.MODULE_LIST_URL%>" />',
                    headers: { "Writable": true },
	                success: function (jsonResult) {
	                    if (jsonResult !== null) {
	                        for (var key in jsonResult) {
	                            $("#dbList").append("<option>" + jsonResult[key] + "</option>");
	                        }
	                        $("#dbList").selectpicker('refresh');
	                        $("#dbList").change();
	                    }
	                }, error: function (xhr, ajaxOptions, thrownError) {
	                	handleError(xhr);
	                }
	            });

                $('#seqDate').change(function () {
                    var dateString = $(this).val();
                    if (!dateString.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/)) {
                        $('#dateLabel').html('Sequencing date (yyyy-MM-dd) <span class="text-red"> *  Invalid date </span>');
                        $(this).addClass('error');
                        $("#checkProjectDataButton").attr("disabled", "disabled");
                    } else {
                        $('#dateLabel').html('Sequencing date (yyyy-MM-dd) <span class="text-red"> *</span>');
                        $(this).removeClass('error');
                        if ($(".form-control.error").length == 0)
                        	$("#checkProjectDataButton").removeAttr("disabled");
                    }
                });
                $('#projectName').change(function () {
                    var inName = $(this).val();
                    var dupl = false;
                    for (var name in projName) {
                        if (inName.trim() == projName[name].trim()) {
                            dupl = true;
                        }
                    }
                    if (dupl) {
                        $('#nameLabel').html('Project name<span class="text-red nowrap"> * &nbsp;&nbsp;&nbsp;Already exists!</span>');
                        $(this).addClass('error');
                        $("#checkProjectDataButton").attr("disabled", "disabled");
                    } else {
                        $('#nameLabel').html('Project name<span class="text-red "> *</span>');
                        $(this).removeClass('error');
                        if ($(".form-control.error").length == 0)
                        	$("#checkProjectDataButton").removeAttr("disabled");
                    }
                });
                $('#projectCode').change(function () {
                    var inCode = $(this).val();
                    var dupl = false;
                    for (var code in projCode) {
                        if (inCode.trim() == projCode[code].trim()) {
                            dupl = true;
                        }
                    }
                    if (dupl) {
                        $('#codeLabel').html('Project code<span class="text-red nowrap"> * &nbsp;&nbsp;&nbsp;Already exists!</span>');
                        $(this).addClass('error');
                        $("#checkProjectDataButton").attr("disabled", "disabled");
                    } else {
                        $('#codeLabel').html('Project code<span class="text-red"> *</span>');
                        $(this).removeClass('error');
                        if ($(".form-control.error").length == 0)
                        	$("#checkProjectDataButton").removeAttr("disabled");
                    }
                });
                
                var importDropzone = new Dropzone("#importDropzone");
                $('#contentCheckResultDialog').on('hidden.bs.modal', function () {
	                $.each(importDropzone.files, function(i, file) {
	                    file.status = Dropzone.QUEUED;
	                });
	                if (document.getElementById('pageSpinner').style.display == "none")	// otherwise data is being imported
	                	onBeforeUnload();
                });

				importDropzone.on("success", function (file, jsonResult) {
	                var correct = true;
	                var listContent = "<ul class='treeView'><li>&nbsp;<ul class='collapsibleList'>";
	                for (var key in jsonResult.files) {
	                    var span = "<span";
	                    if (jsonResult.files[key] === "ok") {
	                        span += " class='span-ok'>" + jsonResult.files[key] + "</span>";
	                    } else {
	                        span += " class='span-error' style='font-size:13px;'>" + jsonResult.files[key] + "</span>";
	                        correct = false;
	                    }
	                    listContent += "<li>" + key + span + "</li>";
	                }

	                listContent += "</li></ul></li></ul>";
	                $('#labelFiles').html("<b>Files checked in project archive:</b>");
					$('#fileList').html(listContent);
	                CollapsibleLists.apply();
	                document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
	                document.getElementById('pageSpinner').style.display = 'none';
	                spinner.stop();
					
	                if (correct) {
	                	$('#importProjectDataButton').siblings("div").html("into <b>" + $("#dbList").val() + "</b>");
		                $('#importProjectDataButton').show();
		                
		                let fieldSummary = "";
		                let newSampleFieldCount = 0, newAssignmentFieldCount = 0;
		                // show existing / provided field comparison tables if needed
		                if (jsonResult.assignment_fields.existing.length != 0) {	// otherwise database seems empty
			                let allFields = new Set(jsonResult.assignment_fields.existing);
			                jsonResult.assignment_fields.provided.forEach(function(e) { allFields.add(e); });
			                allFields = Array.from(allFields);
			                allFields.sort(function(a, b) {	/* case insensitive comparison */
			                    var comparison = a.toLowerCase().localeCompare(b.toLowerCase());
			                    return comparison === 0 ? a.localeCompare(b) : comparison;
			                });

							fieldSummary += "<div class='col-md-1'></div><div class='col-md-4'><table border='1'><tr bgcolor='lightblue' align='center'><td colspan='3'>Assignment fields</td></tr><tr bgcolor='lightgrey'><th>Field name</th><th>Existing</th><th>Provided</th></tr>";
							for (var i=0; i<allFields.length; i++) {
								let fIsExisting = arrayContainsIgnoreCase(jsonResult.assignment_fields.existing, allFields[i]);
								if (!fIsExisting)
									newAssignmentFieldCount++;
								let fIsProvided = arrayContainsIgnoreCase(jsonResult.assignment_fields.provided, allFields[i]);
								fieldSummary += "<tr><th>" + allFields[i] + "</th><td>" + (fIsExisting ? "<span class='glyphicon glyphicon-ok'></span>" : "") + "</td><td>" + (fIsProvided ? "<span class='glyphicon glyphicon-ok' style='color:" + (fIsExisting ? 'green' : 'red') + ";'></span>" : "")  + "</td></tr>";
							}
							fieldSummary += "</table></div>";
		                }
						
		                if (jsonResult.sample_fields.existing.length != 0) {
							let allFields = new Set(jsonResult.sample_fields.existing);
			                jsonResult.sample_fields.provided.forEach(function(e) { allFields.add(e); });
			                allFields = Array.from(allFields);
			                allFields.sort(function(a, b) {	/* case insensitive comparison */
			                    var comparison = a.toLowerCase().localeCompare(b.toLowerCase());
			                    return comparison === 0 ? a.localeCompare(b) : comparison;
			                });

							fieldSummary += "<div class='col-md-2'></div><div class='col-md-4'><table border='1'><tr bgcolor='lightblue' align='center'><td colspan='3'>Sample fields</td></tr><tr bgcolor='lightgrey'><th>Field name</th><th>Existing</th><th>Provided</th></tr>";
							for (var i=0; i<allFields.length; i++) {
								let fIsExisting = arrayContainsIgnoreCase(jsonResult.sample_fields.existing, allFields[i]);
								if (!fIsExisting)
									newSampleFieldCount++;
								let fIsProvided = arrayContainsIgnoreCase(jsonResult.sample_fields.provided, allFields[i]);
								fieldSummary += "<tr><th>" + allFields[i] + "</th><td>" + (fIsExisting ? "<span class='glyphicon glyphicon-ok'></span>" : "") + "</td><td>" + (fIsProvided ? "<span class='glyphicon glyphicon-ok' style='color:" + (fIsExisting ? 'green' : 'red') + ";'></span>" : "")  + "</td></tr>";
							}
							fieldSummary += "</table></div><div class='col-md-1'></div>";
		                }
		                
						if (newAssignmentFieldCount + newSampleFieldCount > 0)
							$("#fieldSummary").html("<hr /><h5 style='color:red; text-align:center; margin:10px 50px;'>Your archive is acceptable for import. However it contains " + (newAssignmentFieldCount + newSampleFieldCount) + " fields which are new to this database. Please make sure all existing fields correspond to existing names, otherwise data of the same nature will be spread between different fields, and will not be efficiently searchable.</h5>" + fieldSummary);
		                else
		                	$("#fieldSummary").html("");
	                }
	                else
	                	$('#importProjectDataButton').hide();
	                $('#contentCheckResultDialog').modal();
			    });

         	  	$('button#checkProjectDataButton').on("click", function() {checkContent()});
                $('button#importProjectDataButton').on("click", function() {importProjectData()});
                
        	    $(window).on('beforeunload', function() {
        	    	if (document.getElementById('pageSpinner').style.display == "none")	// otherwise data is being imported
        	    		onBeforeUnload();
        	    });
            });
          
          function arrayContainsIgnoreCase(array, element)
          {
          	for (let i = 0; i < array.length; i++) 
			if ((array[i] == null && element == null) || (array[i] != null && element != null && array[i].toLowerCase() == element.toLowerCase())) 
              	return true;
          	return false;
          }
            
          function onBeforeUnload() {
        	    if ($('#processId').val() == null || $("#dbList").val() == null) {
        	        return;
        	    }

  	    	    $.ajax({
  	    	        url: '<c:url value="<%=MetaXplorController.IMPORT_INTERFACE_CLEANUP_URL%>" />?module=' + $("#dbList").val() + "&processId=" + $('#processId').val(),
	    	        async: false,
	    	        type: "DELETE",
    	    	    error: function (xhr, ajaxOptions, thrownError) {
    	    	    	handleError(xhr);
                 }
	    	    });
          }

          function fillAvailableDb(moduleName) {
              $.ajax({
                  url: '<c:url value="<%=MetaXplorController.MODULE_PROJECT_LIST_URL%>" />',
                  async: false,
                  data: {
                      "module": moduleName,
                      "detailLevel": 1
                  },
                  success: function (jsonResult) {
                      $("#projects").append("<option>new Project</option>").append("<optgroup id='optgrp' label='Existing project'></optgroup>");
                      projName = [];
                      projCode = [];
                      for (var key in jsonResult) {
                          projName.push(jsonResult[key].<%=MetagenomicsProject.FIELDNAME_NAME%>);
                          projCode.push(jsonResult[key].<%=MetagenomicsProject.FIELDNAME_ACRONYM%>);
                          $("#optgrp").append("<option data-subtext='" + jsonResult[key].name + "' value='" + jsonResult[key].id + "'>" + jsonResult[key].acronym + "</option>");
                      }
                      $("#projects").selectpicker('refresh');
                  }, error: function (xhr, ajaxOptions, thrownError) {
                  	handleError(xhr);
                  }
              });
          }
          
	      $.ajax({
            url: maxUploadSizeURL + "?capped=true",
            async: false,
            type: "GET",
            contentType: "application/json;charset=utf-8",
            success: function(maxUploadSize) {
            	maxUploadSizeInMb = parseInt(maxUploadSize);
            	$("span#maxUploadSize").html(maxUploadSizeInMb);
    	        $.ajax({
    	            url: maxUploadSizeURL + "?capped=false",
    	            async: false,
    	            type: "GET",
    	            contentType: "application/json;charset=utf-8",
    	            success: function(maxImportSize) {
    	            	if (maxImportSize != null && maxImportSize != "" && maxImportSize != maxUploadSize) {
    	            		maxImportSizeInMb = parseInt(maxImportSize);
    	            		$("span#maxImportSizeSpan").html("Your local or http import volume is limited to <b>" + maxImportSizeInMb + "</b> Mb.");
    	            	}
    	            	
    	           	  	Dropzone.options.importDropzone = {
   	                 		maxFiles: 1,
   	                 		previewsContainer: "#dropZonePreviews",
   	                 	    dictResponseError: 'Error importing data',
   	                 	    acceptedFiles: ".zip",
   	                 	  	previewTemplate: "<div class=\"dz-preview dz-file-preview\">\n <div class=\"dz-details\">\n  <div class=\"dz-filename\"><span data-dz-name></span></div>\n  <div class=\"dz-size\"><span data-dz-size></span></div>\n  <a style=\"float:right;\" class=\"dz-remove\" href=\"javascript:undefined;\" data-dz-remove>Remove file</a>\n  </div>\n  <div class=\"dz-progress\"><span class=\"dz-upload\" data-dz-uploadprogress></span></div>\n  <div class=\"dz-error-message\"><span data-dz-errormessage></span></div>\n  <div class=\"dz-success-mark\">\n  <svg width=\"54px\" height=\"54px\" viewBox=\"0 0 54 54\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:sketch=\"http://www.bohemiancoding.com/sketch/ns\">\n   <title>Check</title>\n   <defs></defs>\n   <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\" sketch:type=\"MSPage\">\n    <path d=\"M23.5,31.8431458 L17.5852419,25.9283877 C16.0248253,24.3679711 13.4910294,24.366835 11.9289322,25.9289322 C10.3700136,27.4878508 10.3665912,30.0234455 11.9283877,31.5852419 L20.4147581,40.0716123 C20.5133999,40.1702541 20.6159315,40.2626649 20.7218615,40.3488435 C22.2835669,41.8725651 24.794234,41.8626202 26.3461564,40.3106978 L43.3106978,23.3461564 C44.8771021,21.7797521 44.8758057,19.2483887 43.3137085,17.6862915 C41.7547899,16.1273729 39.2176035,16.1255422 37.6538436,17.6893022 L23.5,31.8431458 Z M27,53 C41.3594035,53 53,41.3594035 53,27 C53,12.6405965 41.3594035,1 27,1 C12.6405965,1 1,12.6405965 1,27 C1,41.3594035 12.6405965,53 27,53 Z\" id=\"Oval-2\" stroke-opacity=\"0.198794158\" stroke=\"#747474\" fill-opacity=\"0.816519475\" fill=\"#FFFFFF\" sketch:type=\"MSShapeGroup\"></path>\n   </g>\n  </svg>\n  </div>\n  <div class=\"dz-error-mark\">\n  <svg width=\"54px\" height=\"54px\" viewBox=\"0 0 54 54\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:sketch=\"http://www.bohemiancoding.com/sketch/ns\">\n   <title>Error</title>\n   <defs></defs>\n   <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\" sketch:type=\"MSPage\">\n    <g id=\"Check-+-Oval-2\" sketch:type=\"MSLayerGroup\" stroke=\"#747474\" stroke-opacity=\"0.198794158\" fill=\"#ff9999\" fill-opacity=\"0.816519475\">\n     <path d=\"M32.6568542,29 L38.3106978,23.3461564 C39.8771021,21.7797521 39.8758057,19.2483887 38.3137085,17.6862915 C36.7547899,16.1273729 34.2176035,16.1255422 32.6538436,17.6893022 L27,23.3431458 L21.3461564,17.6893022 C19.7823965,16.1255422 17.2452101,16.1273729 15.6862915,17.6862915 C14.1241943,19.2483887 14.1228979,21.7797521 15.6893022,23.3461564 L21.3431458,29 L15.6893022,34.6538436 C14.1228979,36.2202479 14.1241943,38.7516113 15.6862915,40.3137085 C17.2452101,41.8726271 19.7823965,41.8744578 21.3461564,40.3106978 L27,34.6568542 L32.6538436,40.3106978 C34.2176035,41.8744578 36.7547899,41.8726271 38.3137085,40.3137085 C39.8758057,38.7516113 39.8771021,36.2202479 38.3106978,34.6538436 L32.6568542,29 Z M27,53 C41.3594035,53 53,41.3594035 53,27 C53,12.6405965 41.3594035,1 27,1 C12.6405965,1 1,12.6405965 1,27 C1,41.3594035 12.6405965,53 27,53 Z\" id=\"Oval-2\" sketch:type=\"MSShapeGroup\"></path>\n    </g>\n   </g>\n  </svg>\n </div>\n</div>",
   	                 	    init:function() {
   	                 	      var self = this;
   	                 	      self.options.maxFilesize = maxUploadSizeInMb;
   	                 	   	  self.options.autoProcessQueue = false;
   	                 	   	  self.options.uploadMultiple = true;
   	                 	      self.on("sending", function (file) {
   	                 	        $('.meter').show();
   	                 	      });
   	                 	    },
   	                 	   error: function(file, response) {
   	                     	  $('#asyncProgressLink').hide();
   	                  		  clearInterval(currentProgressCheckProcess);
   	                		  currentProgressCheckProcess = null;
   	                          document.getElementById('pageSpinner').style.display = 'none';
   	                          spinner.stop();

   	                          new Dropzone("#importDropzone").removeAllFiles();

   	                          var errorMsg;
   	                          if (response != null)
   	                          	errorMsg = response['errorMsg'];
   	                          alert(errorMsg == null ? response : errorMsg);
   	                 	    }
   	                 	};
    	            },
    	            error: function(xhr, thrownError) {
    	                handleError(xhr);
    	            }
    	        });
            },
            error: function(xhr, thrownError) {
                handleError(xhr);
            }
	      });

          function importProjectData() {
          	document.getElementById('pageSpinner').style.display = 'flex';
          	$('#contentCheckResultDialog').modal('hide');
          	spinner = new Spinner({color: "#fff"}).spin(document.getElementById('pageSpinner'));
            
          	$('#asyncProgressLink').show();
          	
          	$('#processId').val("import_" + $("#dbList").val() + "_${pageContext.session.id}_" + pageLoadTime);

              $.ajax({
                  url: "<c:url value='<%= MetaXplorController.IMPORT_DATA_URL%>' />",
                  type: 'POST',
                  data: {
                      processId: $('#processId').val(),
                      module: $("#dbList").val(),
                      code: $('#projectCode').val().trim(),
                      name: $('#projectName').val().trim(),
                      description: $('#description').val().trim(),
                      authors: $('#authors').val().trim(),
                      adress: $('#adress').val().trim(),
                      seqDate: $('#seqDate').val().trim(),
                      seqTech: $('#seqTech').val().trim(),
                      assemblTech: $('#assemblTech').val().trim(),
                      maxAcc: $('#maxAcc').val().trim(),
                      pub: $('#pub').val().trim(),
                      avail: $('#availTrue').is(':checked') ? true : false,
                      extra: $('#extra').val().trim(),
                      access: $('#public').is(':checked') ? true : false
                  },
                  success: function (result) {
                	$('#asyncProgressLink').hide();
					let figures = "";
					for (var key in result)
						if (key != "error" && result[key] != null)
							figures += "\n" + key + ": " + result[key];
					if (figures.length > 0)
                  		document.getElementById('pageSpinnerText').innerHTML = '<p>Upload complete</p><pre>Import succeeded. Stats:' + figures + "</pre><p>You may <a href='main.jsp?module=" + $("#dbList").val().trim() + "&projects=" + $('#projectCode').val().trim() + "'>consult this data here</a></p>";
                  	spinner.stop();
                  	$('#import').prop('disabled', true);
                  },
                  error: function (xhr, ajaxOptions, thrownError) {
                      if (xhr.status == 401) {
                          alert(xhr.responseText + "\nYou probably lost your session and need to login again");
                          location.reload();
                          return;
                      }
                      handleError(xhr);
                  }
              });
              currentProgressCheckProcess = setInterval(checkProcessProgress, interval);
          }

          function handleError(xhr) {
        	if (!xhr.getAllResponseHeaders())
        		return;	// user is probably leaving the current page (we also have a sporadic bug with Firefox resetting the connection during imports)
        		
          	$('#asyncProgressLink').hide();
      		clearInterval(currentProgressCheckProcess);
    		currentProgressCheckProcess = null;
            document.getElementById('pageSpinner').style.display = 'none';
            spinner.stop();

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

          function checkContent() {
        	  let moduleName = $("#dbList").val();
        	  if (moduleName == null) {
                  alert("A destination database must be selected!");
                  return;
              }

              var importDropzone = new Dropzone("#importDropzone"), dataFile1 = $("#dataPath").val().trim();
              var totalDataSourceCount = importDropzone.files.length + (dataFile1 != "" ? 1 : 0);

              if (totalDataSourceCount > 1) {
                  alert("You may not provide more than 1 project data file!");
                  return;
              }

              if (importDropzone.getRejectedFiles().length > 0) {
                  alert("Please remove any rejected files before submitting!");
                  return;
              }
              
              if (totalDataSourceCount < 1) {
                  alert("You must provide a project data file!");
                  return;
              }
              if ($("input#projectCode").val().trim().length == 0 || $("input#projectName").val().trim().length == 0) {
                  alert("You must provide valid entries for all mandatory fields!");
                  return;
              }

              document.getElementById('pageSpinner').style.display = 'flex';
              spinner = new Spinner({color: "#fff"}).spin(document.getElementById('pageSpinner'));
              
              $('#progress').data('error', false);
              $('#progress').modal({backdrop: 'static', keyboard: false, show: true});

              $('#processId').val("projectDataCheck_" + $("#dbList").val() + "_${pageContext.session.id}_" + pageLoadTime);
              
              if (importDropzone.getQueuedFiles().length > 0) {
            	  	document.getElementById('pageSpinnerText').innerHTML = "Uploading...";
					importDropzone.processQueue();
              }
              else
              {
					document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
					var blob = new Blob();
					blob.upload = { name:"nofiles" };
					importDropzone.uploadFile(blob);
              }

              currentProgressCheckProcess = setInterval(checkProcessProgress, interval);
          }

          function checkProcessProgress() {
              $.ajax({
                  url: "<c:url value='<%= MetaXplorController.PROGRESS_INDICATOR_URL%>' />",
                    method: 'GET',
                    data: {"processId": $('#processId').val()},
                    success: function (jsonRes) {
                        if (jsonRes != null) {
                        	if (jsonRes.complete == true) {
                        		clearInterval(currentProgressCheckProcess);
                        		currentProgressCheckProcess = null;
                        	}
                        	else {
	                            if (jsonRes.error == null) {
	                            	if (jsonRes.progressDescription != null)
	                                	document.getElementById('pageSpinnerText').innerHTML = jsonRes.progressDescription;
	                            } else {
	                                document.getElementById('pageSpinner').style.display = 'none';
	                                spinner.stop();
	                                document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
	                        		clearInterval(currentProgressCheckProcess);
	                        		currentProgressCheckProcess = null;
	                                alert(jsonRes.error);
	                            }
                        	}
                        }
                    },
	                error: function (xhr, status, error) {
	                	if (xhr.status == 502) {
	                		console.log("Error 502");
	                		console.log(xhr);
	              	 	}
	                	else
	                		handleError(xhr);
                    }
                });
            }
			</script>
			<div id="contentCheckResultDialog" class="modal" tabindex="-1" role="dialog">
			    <div class="modal-dialog modal-lg" role="document">
			        <div class="modal-content">
			            <div class="modal-body" id="contentCheckResultBody">
			            	<div class="row">
			           	       	<div class="col-md-3" style='padding-right:0;'>
			           	       		<p id="labelFiles" style='text-align:right;'></p>
			           	       	</div>
			                  	<div id="fileList" class="col-md-7"></div>
								<div class="col-md-2" style="text-align:center;"><button class="btn btn-primary btn-sm" style="margin-top:30px;" id="importProjectDataButton" type="button">Import</button><div class="small margin-top-sm"></div></div>
							</div>
							<div class="row small" id="fieldSummary"></div>
			            </div>
			        </div>
			    </div>
			</div>
			
			<div style="display:none;" class='center' id="asyncProgressLink">
				<button class="btn btn-info btn-sm" onclick="window.open('ProgressWatch.jsp?processId=' + $('#processId').val() + '&successURL=' + escape('<c:url value='/main.jsp' />?' + 'module=' + $('#dbList').val() + '&projects=' + $('#projectCode').val().trim()));" title="This will open a separate page allowing to watch import progress at any time. Leaving the current page will not abort the import process.">Open async progress watch page</button>
			</div>
	<script type="text/javascript" src="js/commons.js"></script>			
</body>
</html>