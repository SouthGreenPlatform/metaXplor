/*******************************************************************************
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
 *******************************************************************************/
let currentProgressCheckProcess;
let currentProcessId;
let spinner;
let pageSize = 100;
let pageNumber = 0;
let sortBy;
let sortDir = 1;
let globalQueryString;
let totalRecordCount = -1, selectionSeqCount;
let gpsMap;
let markers;
let areaSelect = null;
let regexp = new RegExp("[0-9]+([\.|,][0-9]+)?");
let projectListId = [];
let selectedResultFields;
let availableResultFields;
let exportFileName;
let selectedDB = null;
let currentSelectedProjectCount = null;
let previousSelectedProjectCount = null;
let assignMethodFieldId = -1;
let showAssignMethodByDefault = false;
let onlineOutputTools;

let assignmentMethodFieldId, taxonFieldId, bestHitFieldId;
let interval = 1000;

$.ajaxSetup({
    converters: {
        "text json" : function(response) {
            return (response == "") ? null : JSON.parse(response);
        },
    },
});

$(window).on('beforeunload', function () {
    cleanupTempData();
});

function cleanupTempData() {
	if (currentProcessId != null && selectedDB != null)
	    $.ajax({
	        url: INTERFACE_CLEANUP_URL + "?module=" + selectedDB + "&processId=" + currentProcessId,
	        type: "DELETE"
	    });
}
	
$('#dbList').on('change', function () {
	sortBy = null;
	cleanupTempData();
	selectedDB = $("#dbList").val();

    $.ajax({
        url: MODULE_PROJECT_LIST_URL,
        async: false,
        method: "GET",
        data: {
            "module": $("#dbList").val(),
            "detailLevel": 2
        },
        success: function (jsonResult) {
            projectListId = [];
            $("#projectList").html("");
            let projectDescriptions = "<h3>Projects in database " + $("#dbList").val() + "</h3><table class='seqDetailTable'><tr><th>Code</th><th>Name</th><th>Description</th><th>Sequencing technology</th><th>Sequencing date</th><th>Assembly method</th><th>Samples available</th><th>Extra information</th><th>Authors</th><th>Contact</th><th>Reference</th></tr>";
            for (var key in jsonResult) {
                var projects = $_GET("projects"); // get projects from url
                if (projects != null) {	// sometimes a # appears at the end of the url so we remove it with regexp
                	projects = projects.replace(new RegExp('#([^\\s]*)', 'g'), '');
                	projects = projects.split(",");
                }
                else
                	projects = [];

                projectListId.push(jsonResult[key]._id);
                $("#projectList").append("<option" + (arrayContains(projects, jsonResult[key][FIELDNAME_ACRONYM]) ? " selected" : "") + " data-subtext='" + jsonResult[key][FIELDNAME_NAME] + "' value='" + jsonResult[key]._id + "'>" + jsonResult[key][FIELDNAME_ACRONYM] + "</option>");
                projectDescriptions += "<tr class='mainRow'><td>" + jsonResult[key][pjField_acronym] + "</td><td>" + jsonResult[key][pjField_name] + "</td><td>" + jsonResult[key][pjField_desc] + "</td><td>"
                	+ jsonResult[key][pjField_seqMethod] + "</td><td>" + (jsonResult[key][pjField_seqDate] == null ? '' : new Date(jsonResult[key][pjField_seqDate]).toISOString().split('T')[0]) + "</td><td>" + jsonResult[key][pjField_assemblyMethod] + "</td><td>"
                	+ (jsonResult[key][pjField_samplesAvailable] ? "Yes" : "No") + "</td><td>" + jsonResult[key][pjField_extraInfo] + "</td><td>" + jsonResult[key][pjField_authors] + "</td><td>"
                	+ jsonResult[key][pjField_contact] + "</td><td>" + jsonResult[key][pjField_reference] + "</td></tr>";
            }
            $("#dbProjectSummary").html(projectDescriptions + "</table>");
            $("#dbProjectSummary").show();

            $("#searchButton").attr("disabled", $("#projectList").html() == "");
            currentProcessId = "search" + "¤" + $("#dbList").val() + "¤" + sessionId + "_" + new Date().getTime();
            $("#projectList").selectpicker('refresh');
            $('#projectList').change();
        },
        error: function (xhr, ajaxOptions, thrownError) {
            handleError(xhr);
        }
    });
});

$('#projectList').on('change', function () {
	$("#resultDisplayModes").hide();
	$("#resultTableNavigation").hide();
	$("#fieldSelectionPanel").hide();
	$("#resultTableContainer").hide();
	$("#resultTreeContainer").hide();
	$("#resultPieContainer").hide();
	$("#resultMapContainer").hide();

	$("#assignMethodForTaxoResultsDiv").hide();
	$("#assignMethodForTaxoResults").html("");
	$("#assignMethodForTaxoResults").selectpicker('refresh');
	
	$("#assignMethodForTaxoFilterDiv").hide();
	$("#seqCountExplanation").hide();
	$("#assignMethodForTaxoFilter").html("");
	$("#assignMethodForTaxoFilter").selectpicker('refresh');

	$('#filterList').selectpicker('deselectAll');
    $('#filterList option').each(function () {
        let widget = document.getElementById('widgetContainer_' + $(this).val());
        widget.innerHTML = "";
        widget.setAttribute("query", "");
    });
    
    // these are likely to change from a dataset to another
	selectedResultFields = new Array();
    assignmentMethodFieldId = null;
    bestHitFieldId = null;
    
    let selectedProjects = getProjectList();
    previousSelectedProjectCount = currentSelectedProjectCount;
    currentSelectedProjectCount = selectedProjects.length;

    $.ajax({
        url: SEARCHABLE_FIELD_LIST_URL,
        method: "GET",
        data: {
            "module": $("#dbList").val(),
            "projects": selectedProjects.join(";")
        },
        success: function (fields) {
            let options = "";
            let i = 0;
            let spFields = new Array(), sqFields = new Array(), asFields = new Array();
            for (let index in fields)
            {
            	let data = fields[index];
            	if (data[1] == sampleType)
            		spFields.push(data);
            	else if (data[1] == sequenceType)
            		sqFields.push(data);
            	else
            		asFields.push(data);
            }

            let orderedFields = [sqFields, spFields, asFields];
            
            for (let entityType in orderedFields) {
                for (let index in orderedFields[entityType]) {
	                let data = orderedFields[entityType][index];
                	let cleanIndex = data[0];
	                options += "<option class='fieldType_" + data[1] + "' value='" + data[0] + "' data-type='" + data[2] + "' data-collection='" + data[1] + "'>" + data[3].replace("taxonomy_id", "taxonomy") + "</option>";
	                i++;
	                $('#leftPanelFilterZone').append("<div id='widgetContainer_" + data[0] + "' alt='" + data[0] + "'></div>");
	                
	                if (data[1] == assignmentType) {	// keep track of field IDs for default fields to display
		                if (data[3] == FIELDNAME_ASSIGNMENT_METHOD)
		                	assignmentMethodFieldId = data[0];
		                else if (FIELDNAME_TAXON.startsWith(data[3]))
		                	taxonFieldId = data[0];
		                else if (data[3] == bestHitFieldName)
		                	bestHitFieldId = data[0];
	                }
                }
            }
        	if (bestHitFieldId != null)
        		$('#highlightBestHits').show();
        	else
        		$('#highlightBestHits').hide();

            $('#filterList').html(options).selectpicker('refresh');
            
            // force display of assignment method filter if several are present in the data
        	showAssignMethodByDefault = getAssignmentMethods().length > 1;
            if (showAssignMethodByDefault)
            	$("select#filterList").selectpicker("val", [assignMethodFieldId, bestHitFieldId]);
        },
        error: function () {
            console.log("couldn't get fieldList");
        }
    });
});

$(document).ready(function () {
    var module = $_GET("module"); // get module from url
    if (module != null)	// sometimes a # appears at the end of the url so we remove it with regexp               
    	module = module.replace(new RegExp('#([^\\s]*)', 'g'), '');

   	$.ajax({
        url: MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT,
        async: true,
        type: "GET",
        contentType: "application/json;charset=utf-8",
        success: function(maxFastaSeqCount) {
	          maxPhyloAssignFastaSeqCount = parseInt(maxFastaSeqCount);
        },
        error: function(xhr, thrownError) {
            handleError(xhr);
        }
     });
   	
    $.ajax({
    	async: false,
        url: MODULE_LIST_URL + (module != null ? "?module=" + module : ""),
        success: function (jsonResult) {
            if (jsonResult !== null) {
                for (var key in jsonResult)
                    $("#dbList").append("<option" + (module == jsonResult[key] ? " selected" : "") + ">" + jsonResult[key] + "</option>");

                $("#dbList").selectpicker('refresh');
                if ($("#dbList option").length > 0)
                	$("#dbList").change();
            }
        }, error: function (xhr, ajaxOptions, thrownError) {
            var err = eval("(" + xhr.responseText + ")");
            alert(err.Message);
        }
    });
    
    $.ajax({
        url: ONLINE_OUTPUT_TOOLS_URL,
        async: false,
        type: "GET",
        contentType: "application/json;charset=utf-8",
        success: function(labelsAndURLs) {
        	onlineOutputTools = labelsAndURLs;
        	var options = "<option value='Custom tool'>Custom tool</option>";
        	for (var label in labelsAndURLs)
        		options += '<option value="' + label + '">' + label + '</option>';
        	$("#onlineOutputTools").html(options);
	        onlineOutputTools["Custom tool"] = {"url" : ""};
        	configureSelectedExternalTool();
        },
        error: function(xhr, thrownError) {
            handleError(xhr, thrownError);
        }
    });
	
    let displayZone = document.getElementById('pageSpinner');
    spinner = new Spinner({color: "#fff"}).spin(displayZone);

    document.getElementById('exportButton').setAttribute("disabled", "disabled");

    $('#leftPanelPrimaryZone').fadeIn();
    $('#filterList').change(function () {
        buildWidgets();
    });

    gpsMap = L.map('map', {
        fullscreenControl: true
    }).setView([25, 0], 2);
    L.tileLayer('http://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>, &copy;<a href="https://carto.com/attribution">CARTO</a>'
    }).addTo(gpsMap);
    markers = L.markerClusterGroup();
    L.control.mousePosition().addTo(gpsMap);
    
    $('#exportBtn').click(function () {
    	$('#serverExportResult').html('');
        if ($("#exportToServer").is(":checked"))
        	$('#asyncProgressLink').show();
        else
        	$('#asyncProgressLink').hide();
        let format = $('#exportFormat').selectpicker('val');
		exportFileName = format == sampleType ? EXPORT_FILENAME_SP : (format == sequenceType ? EXPORT_FILENAME_SQ : (format == fastaType ? EXPORT_FILENAME_FA : (format == assignmentType ? EXPORT_FILENAME_AS : EXPORT_FILENAME_BM)));
        isBusySearching(true);
        let url = (format == sampleType ? SAMPLE_EXPORT_URL : (format == fastaType ? SEQUENCE_EXPORT_URL : (format == sequenceType ? SEQ_COMPO_EXPORT_URL : (format == assignmentType ? ASSIGNMENT_EXPORT_URL : BIOM_EXPORT_URL)))) + '?processId=' + currentProcessId + "&exportToServer=" + $("#exportToServer").is(":checked") + "&projects=" + getProjectList().join(";");
    	if ($('#biomAssignMethod option').length > 1)
    		url += "&assignMethod=" + $('#biomAssignMethod').val();
        document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
        $('#exportBtn').attr('disabled', 'disabled');
        $('#closeExportBox').attr('disabled', 'disabled');
        $.ajax({
            url: url,
	  		dataType: $("#exportToServer").is(":checked") ? null : 'binary',
            success: function (data) {
            	$('#closeExportBox').removeAttr("disabled");
            	$('#asyncProgressLink').hide();
            	if ($("#exportToServer").is(":checked")) {
            		var fileUrl = location.origin + location.pathname.substring(0, window.location.pathname.indexOf("/", 2)) + "/" + TMP_OUTPUT_FOLDER + "/" + data + "/" + exportFileName;
            		let urlDisplayHTML = "<div style='padding:10px;'>" + $("select#exportFormat option:selected").text() + " may be downloaded <a target='_blank' href=\"" + fileUrl + "\">from this link</a></div>";
            	    if (onlineOutputTools != null)
            	    	for (var toolName in onlineOutputTools)
            	    	{
            	    		var toolConfig = getOutputToolConfig(toolName);
            	    		var buttonsForThisTool = "";
        		        	if (toolConfig['url'] != null && toolConfig['url'].trim())
        	    				buttonsForThisTool += '<input class="btn-sm btn margin-bottom-sm btn-primary" type="button" value="Send file to ' + toolName + '" onclick="window.open(\'' + toolConfig['url'].replace(/\*/g, fileUrl.replace(/.zip$/g, '')) + '\');" />&nbsp;';
            		        
            	    		if (buttonsForThisTool != "");
            	    			urlDisplayHTML += buttonsForThisTool;
            	    	}
            		if (format == fastaType) {
            			if (maxPhyloAssignFastaSeqCount >= selectionSeqCount)
            				urlDisplayHTML += "<button class='btn-sm btn margin-bottom-sm btn-primary' onclick=\"$('#exportModal').modal('hide'); window.open('pplacer.jsp?module=" + $("#dbList").val() + "&exportHash=" + data + "');\">Send to phylogenetic assignment</button>"
           				else
           					urlDisplayHTML += "<span style='color:red;'>Selection is too large to be sent to phylogenetic assignment.<br/>Only " + maxPhyloAssignFastaSeqCount + " sequences are allowed.</span>"
            		}
            		$('#exportModal #serverExportResult').html(urlDisplayHTML);
            	}
            	else {
	                let urlBlob = window.URL.createObjectURL(new Blob([data]));
	                let link = document.getElementById('file');
	                link.href = urlBlob;
	                link.download = exportFileName;
	                link.click();
	                setTimeout(function () {
	                    window.URL.revokeObjectURL(urlBlob);
	                }, 1000);
	                $('#exportModal').modal('hide');
            	}
            	document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
                isBusySearching(false);
            },
            error: function () {
            	document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
            	$('#exportBtn').removeAttr('disabled');
          	  	clearInterval(currentProgressCheckProcess);
          	  	currentProgressCheckProcess = null;
                isBusySearching(false);
            }
        });
        currentProgressCheckProcess = setInterval(checkProcessProgress, 1000);
    });

    $("#resultTableContainer").scroll(function () {
        let translate = "translate(0," + this.scrollTop + "px)";
        this.querySelector("thead").style.transform = translate;
    });

    let to = false;
    $('#treeSearch').keyup(function () {
        if (to) {
            clearTimeout(to);
        }
        to = setTimeout(function () {
            $("#treeContainer").jstree('search', $('#treeSearch').val());
        }, 250);
    });
    spinner.stop();
    
    let inputObj = $('#filterList').parent().find("div.bs-searchbox input");
    inputObj.css('width', "calc(100% - 24px)");
    inputObj.before("<a href=\"#\" onclick=\"$('#filterList').selectpicker('deselectAll').selectpicker('toggle');\" style='font-size:18px; margin-top:5px; font-weight:bold; text-decoration: none; float:right;' title='Clear selection'>&nbsp;X&nbsp;</a>");
});

function exportFormatSelectionChanged() {
	$('#exportBtn').removeAttr('disabled');
	$('#exportToServerZone span').css('display', $('#exportFormat').val() == fastaType ? 'block' : 'none');
   
	$('#biomAssignMethod option').remove();
	if ($('#exportFormat').val() != biomFormatType)
		$('#exportToServerZone div').hide();
	else {
		$('#exportToServerZone div').show();
    	let values = getAssignmentMethods();
        for (var i=0; i<values.length; i++)
        	$('#biomAssignMethod').append("<option>" + values[i] + "</option>");
        $('#exportToServerZone div').css('display', values.length > 1 ? 'block' : 'none');
	}
}

function getProjectList() {
    let projects = $("#projectList").val();
    if (projects.length === 0) {
        projects = projectListId;
    }
    return projects;
}

function checkProcessProgress() {
    $.ajax({
        url: PROGRESS_INDICATOR_URL,
          method: 'GET',
          data: {"processId": currentProcessId},
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
	                	  $('#serverExportResult').html('');
	                      document.getElementById('pageSpinner').style.display = 'none';
	                      spinner.stop();
	                      document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
		            	  clearInterval(currentProgressCheckProcess);
		            	  currentProgressCheckProcess = null;
		            	  $('#closeExportBox').removeAttr("disabled");
		            	  $('#asyncProgressLink').hide();
	                      alert(jsonRes.error);
	                  }
	              }
              }
          },
          error: function () {
        	  $('#serverExportResult').html('');
              document.getElementById('pageSpinner').style.display = 'none';
              spinner.stop();
              document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
        	  clearInterval(currentProgressCheckProcess);
        	  currentProgressCheckProcess = null;
        	  $('#closeExportBox').removeAttr("disabled");
        	  $('#asyncProgressLink').hide();
              alert(jsonRes.error);
          }
      });
}

function buildWidgets() {
    let selectedProjects = getProjectList();

    $('#filterList option').each(function () {

        let fieldId = $(this).val(), fieldLabel = $(this).text();
        if (this.selected && $('#widgetContainer_' + fieldId).is(':empty')) {  
        	let entityType = $(this).data("collection");
            let type = $(this).data("type");
            if (type == GPS_TYPE)
            	addGPSWidget(fieldId, entityType, fieldLabel);
            else if (FIELDNAME_TAXON.startsWith(fieldLabel))
            	addTreeWidget(fieldId, entityType, fieldLabel);
            else
                $.ajax({
                    url: SEARCHABLE_FIELD_INFO_URL,
                    method: 'GET',
                    data: {
                        "module": $("#dbList").val(),
                        "projects": selectedProjects.join(";"),
                        "field": $('#widgetContainer_' + fieldId).attr('alt'),
                        "type": type
                    },
                    success: function (values) {
                        switch (type) {
                            case STRING_TYPE:
                            case STRING_ARRAY_TYPE:
                                addListWidget(fieldId, entityType, fieldLabel, values, values.length == 0);
                                break;
                            case DOUBLE_TYPE:
                                addRangeWidget(fieldId, entityType, fieldLabel, values[0], values[1]);
                                break;
                            case DATE_TYPE:
                                addDateWidget(fieldId, entityType, fieldLabel, values[0], values[1]);
                                break;
                        }
                    },
                    error: function () {
                        console.log("couldn't build widget for field " + fieldLabel);
                    }
                });

        } else if (!this.selected) {
            $('#widgetContainer_' + fieldId).html("");
        }
    });
}

function doSearch() {
    isBusySearching(true);

    let queryData = {
        "module": $("#dbList").val(),
        "sortBy": sortBy,
        "processId": currentProcessId
    };
    if (getProjectList().length < $("#projectList option").length)
    	queryData['projects'] = getProjectList().join(";")

    // register all filters, each filter is a parameter in the http request below
    $("#filterList option:selected").each(function () {
        let fieldId = $(this).val();
        let type = $(this).data("type");
        let collectionName = $(this).data("collection");

        let widget = document.getElementById("widgetContainer_" + fieldId.replace(/ /g, "_"));
        let missingDataAllowed = $(widget).find("input.missingData").is(":checked");

        if (widget.classList.contains("usedWidget") || !missingDataAllowed) {
        	queryData[(!missingDataAllowed ? "*" : "") + $(widget).attr('alt')] = collectionName + "§" + type + "§" + (widget.getAttribute('query') == null ? "" : widget.getAttribute('query'));
        }
    });

    document.getElementById("tableBody").innerHTML = "";
    $("#tree").jstree("destroy").empty();
    document.getElementById("resultPieContainer").innerHTML = "";
    $('#resCount').html("<img width='18' src='css/throbber.gif'>");
    $("#pagination").hide();
    toggleResultViewMode("table");

    queryData = queryData;
    $.ajax({
        url: CREATE_TEMP_VIEW_URL,
        method: 'GET',
        data: queryData,
        success: function (response) {
            document.getElementById('pageSpinnerText').innerHTML = "Counting...";
            count();
            loadResults();
        },
        error: function () {
            console.log("couldn't create temp collection");
        }
    });
}

function count() {
    $.ajax({
        url: RECORD_COUNT_URL,
        data: {
            "module": $("#dbList").val(),
            "processId": currentProcessId,
            "entityLevel": entityLevel
        },
        success: function (count) {
            totalRecordCount = count;
        	if (entityLevel == sequenceType)
        		selectionSeqCount = count;
            createPaginationWidget();
            adaptDisplayToResultCount();
        },
        error: function () {
            console.log("count failed");
        }
    });
}

function adaptDisplayToResultCount() {
    if (totalRecordCount < 1) {
    	$('#resultTableNavigation').hide();
    	$('#resultTableContainer').hide();
    	$('#resultDisplayModes').hide();
    }
    else {
    	$('#resultDisplayModes').show();
    	toggleResultViewMode('table');
        document.getElementById('exportButton').removeAttribute("disabled");
    }
    
	if (totalRecordCount == 0)
		$('#noResultsMessage').show();
	else
    	$('#noResultsMessage').hide();
}

function equalArray(a, b) {
    if (a.length === b.length) {
        for (var i = 0; i < a.length; i++) {
            if (a[i] !== b[i]) {
                return false;
            }
        }
        return true;
    } else {
        return false;
    }
}

function loadResults() {
    $('#pageSpinnerText').html("Loading...");
    isBusySearching(true);
    
    let sortFieldPath;
    if (sortBy != null) {
    	let splitSortFieldPath = sortBy.split(".");
    	let sortField = availableResultFields[splitSortFieldPath[0]][sortBy];
    	if (sortField != null)	// may be null if current page contained no data for this field
    		sortFieldPath = sortField.join(".");
    	else
    		try {
    			sortFieldPath = splitSortFieldPath[0] + "." + $("select#filterList option[value=" + splitSortFieldPath[1] + "]").attr("data-type") + "." + splitSortFieldPath[1];
    		}
    		catch(err) {
    			console.log(err);
    		}
    }

    $.ajax({
        url: RECORD_SEARCH_URL,
        method: 'GET',
        data: {
            "module": $("#dbList").val(),
            "processId": currentProcessId,
            "page": pageNumber,
            "size": pageSize,
            "sortBy": sortFieldPath,
            "sortDir": sortDir,
            "entityLevel": entityLevel
        },
        success: function (jsonResult) { // build list of all available fields
        	let newAvailableResultFields = {};
        	newAvailableResultFields[sequenceType] = new Array();
        	newAvailableResultFields[sampleType] = new Array();
        	if (entityLevel == sampleType)
        		newAvailableResultFields[sampleType][sampleType + ".sample"] = ["_id"];	// only shown when displaying samples
        	newAvailableResultFields[assignmentType] = new Array();
    		
        	for (let index in jsonResult)
	        	for (var key in jsonResult[index]) {
	        		if (sampleType == key || assignmentType == key) {
	                    let headerPositions = new Array();
	
	        			for (let spIndex in jsonResult[index][key])
	        				for (let spFieldKey in jsonResult[index][key][spIndex])
	        					if (spFieldKey != "_id" && spFieldKey != FIELDNAME_PROJECT)
	        						for (let sfFieldKey in jsonResult[index][key][spIndex][spFieldKey]) {
	                            		let headerPos = headerPositions[sfFieldKey];
	                            		if (headerPos == null)
	                            			headerPositions[key + "." + sfFieldKey] = [key, spFieldKey, sfFieldKey];
	            					}
	        			
	                    for (let header in headerPositions) {
	                    	let label = !isNaN(headerPositions[header][2]) ? $("#filterList option[value=\"" + headerPositions[header][2] + "\"]").text().toLowerCase().replace(/_/g, " ") : headerPositions[header][2];
	                    	if (label.trim() != "")	// if it's empty then it probably doesn't exist for the current project selection
	                    		newAvailableResultFields[key][header] = headerPositions[header];
	                    }
	        		}
	        	}
        	
        	if (entityLevel == sampleType) {
            	if (selectedResultFields[entityLevel] == null || Object.keys(selectedResultFields[entityLevel]).length == 0) {
            		selectedResultFields[sampleType] = [sampleType + ".sample"];
		        	$("#filterList option.fieldType_" + sampleType).each(function () {
	        			let header = $(this).val();
	        			var key = sampleType + "." + header;
	        			selectedResultFields[sampleType].push(key);
		            });
            	}
        	}
        	else {
        		newAvailableResultFields[sequenceType][sequenceType + ".project"] = ["_id", FIELDNAME_PROJECT];
        		newAvailableResultFields[sequenceType][sequenceType + ".qseqid"] = ["_id", qseqidFieldName];
        		newAvailableResultFields[sequenceType][sequenceType + ".sequence length"] = [DOUBLE_TYPE, seqLengthFieldId];
        		newAvailableResultFields[sequenceType][sequenceType + ".sample"] = [sampleCompoFieldName, "sp"];
            	if (selectedResultFields[entityLevel] == null || Object.keys(selectedResultFields[entityLevel]).length == 0) {
            		if (entityLevel == sequenceType)
            			selectedResultFields[entityLevel] = [sequenceType + ".qseqid", sequenceType + ".sequence length", sequenceType + ".sample"];	// default fields to display
            		else
            			selectedResultFields[entityLevel] = [sequenceType + ".qseqid", sequenceType + ".sequence length", sequenceType + ".sample", assignmentType + "." + assignmentMethodFieldId, assignmentType + "." + sseqidFieldId, assignmentType + "." + taxonFieldId];	// default fields to display
            		if (getProjectList().length != 1)
            			selectedResultFields[entityLevel].splice(0, 0, sequenceType + ".project");
            	}
        	}
        	
        	// check if field list in current page is the same as in previous page
        	let resultFieldListChanged = false;
        	for (var key in newAvailableResultFields)
        		if (previousSelectedProjectCount != currentSelectedProjectCount || availableResultFields == null || Object.keys(availableResultFields[key]).join(";") != Object.keys(newAvailableResultFields[key]).join(";")) {
        			resultFieldListChanged = true;
        			break;
        	}

            availableResultFields = newAvailableResultFields;

        	if (resultFieldListChanged) {
	        	let htmlForSelection = "";

	        	for (let entityType in availableResultFields) {
	        		htmlForSelection += "<div class='fieldType_" + entityType + "'><a href='#' onclick='toggleSelectedFields(\"" + entityType + "\");' style='background-color:white; text-align:center; padding:3px; width:30px; border-radius:5px; float:right;' title='Toggle all " + (entityType == sampleType ? "sample" : (entityType == assignmentType ? "assignment" : "sequence")) + " level fields'><img src='img/checkall.gif' /></a><ul class='sortable'>";
        			let fieldList = Array.from(Object.keys(availableResultFields[entityType]));	// keep track of entire list so we can also add "static" fields
        			let dynamicFields = "", staticFields = "";
	        		if (entityType == sequenceType) {	// no sorting order to apply for these few fields
		        		for (var key in availableResultFields[entityType]) {
		        			let header = key.split(".")[1];
		        			htmlForSelection += "<li class='ui-state-default'><input id=\"selectField_" + key + "\" type='checkbox' " + (arrayContains(selectedResultFields[entityLevel], key) ? "checked " : "") + "value=\"" + key + "\"> <label title='Drag me to move me!' for=\"selectField_" + key + "\">" + (!isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().toLowerCase().replace(/_/g, " ") : header) + "</label></li>";
		        		}
	        		}
	        		else if (Object.keys(availableResultFields[entityType]).length > 0)
		        	{
	        			// loop on filter list to use the same sorting order
			        	$("#filterList option.fieldType_" + entityType).each(function () {
		        			let header = $(this).val();
		        			var key = entityType + "." + header;
		        			let label = !isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().toLowerCase().replace(/_/g, " ") : header;
		        			if (label.trim() != "")	// if it's empty then it probably doesn't exist for the current project selection
		        				dynamicFields += "<li class='ui-state-default'><input id=\"selectField_" + key + "\" type='checkbox' " + (arrayContains(selectedResultFields[entityLevel], key) ? "checked " : "") + "value=\"" + key + "\"> <label title='Drag me to move me!' for=\"selectField_" + key + "\">" + (!isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().toLowerCase().replace(/_/g, " ") : header) + "</label></li>";
		        			fieldList = fieldList.filter(function(value, index, arr){ return value != key;});
			            });
			        	// add remaining fields
			        	for (var i=0; i<fieldList.length; i++) {
		        			var key = fieldList[i];
		        			let header = key.split(".")[1];
		        			let label = !isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().toLowerCase().replace(/_/g, " ") : header;
		        			if (label.trim() != "")	// if it's empty then it probably doesn't exist for the current project selection
		        				staticFields += "<li class='ui-state-default'><input id=\"selectField_" + key + "\" type='checkbox' " + (arrayContains(selectedResultFields[entityLevel], key) ? "checked " : "") + "value=\"" + key + "\"> <label title='Drag me to move me!' for=\"selectField_" + key + "\">" + (!isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().toLowerCase().replace(/_/g, " ") : header) + "</label></li>";
			            }
		        	}
	        		htmlForSelection += (entityType == sampleType ? (staticFields + dynamicFields) : (dynamicFields + staticFields)) + "</ul></div>";	// for samples, the sample ID must appear first
	        	}
	        	$("div#fieldSelectionPanel div.panel-body").html(htmlForSelection);
	        	$("div#fieldSelectionPanel div div div").each(function() {
	        		if ($(this).find("li").length == 0)
	        			$(this).hide();
	        	});

	            $(".sortable").sortable();
	            $(".sortable").disableSelection();
        	}

            // dynamically create the headers
            let sseqidColumn;
            let tableHead = new StringBuffer("<tr class='resultHeader'>");
            for (var key in selectedResultFields[entityLevel]) {
            	let splitFieldPath = selectedResultFields[entityLevel][key].split(".");
            	let fieldName = splitFieldPath[1];
            	if (!isNaN(fieldName)) {
            		if (sseqidFieldId == fieldName)
            			sseqidColumn = key;
            		fieldName = $("#filterList option[value=\"" + fieldName + "\"]").text().toLowerCase().replace(/_/g, " ");
            	}
            	tableHead.append("<th class='sortableTableHeader fieldType_" + splitFieldPath[0] + "' id=\"resultCol_" + selectedResultFields[entityLevel][key] + "\"" + (entityLevel != sampleType && splitFieldPath[0] == sampleType ? " title=\"Sorting by this column may be slow on large datasets!\"" : "") + ">" + fieldName + "</th>");
            }
            tableHead.append("</tr>");            
            document.getElementById("tableHead").innerHTML = tableHead;

            let tableBody = new StringBuffer();
            for (let index in jsonResult) {
        		let isBestHitAssignment = false;
        		if (assignmentType == entityLevel && bestHitFieldId != null) {
        			let bh = readNestedFields(jsonResult[index], [assignmentType, STRING_TYPE, bestHitFieldId]);
        			if (Array.isArray(bh) && bh.length == 1 && bh[0].trim() != "")
        				isBestHitAssignment = true;
        		};

        		if ($("#compactMultipleValues").css("display") == "none") { // compact mode: multiple values are dereplicated and displayed as CSV
            		tableBody.append("<tr class='mainRow result_" + index + "' onmouseover=\"$('tr.result_" + index + "').css('background-color', '#f8f8c0');\" onmouseout=\"$('tr.result_" + index + "').css('background-color', '');\" onclick=\"showDetails(" + jsonResult[index]._id.pj + ", '" + (entityLevel == sampleType ? jsonResult[index]._id : jsonResult[index]._id.qseqid) + "');\">");
            		for (var key in selectedResultFields[entityLevel]) {
	                	let fieldWithEntityPrefix = selectedResultFields[entityLevel][key];
	                	let pathToField = availableResultFields[fieldWithEntityPrefix.split(".")[0]][fieldWithEntityPrefix];
	                	if (pathToField == null)
	                		tableBody.append("<td></td>");
	                	else {
	                    	tableBody.append("<td class='" + (key == sseqidColumn ? "sseqid" : "") + (isBestHitAssignment && pathToField[0] == assignmentType ? " bestHitAssignment" + ($('#highlightBestHits input').is(":checked") ? " highlighted" : "") : "") + "'>");
		                    let fields = readNestedFields(jsonResult[index], pathToField);
		                    if (fields.length > 1)
		                    	fields = Array.from(new Set(fields));	// remove duplicates
		
		                    for (let j=0; j<fields.length; j++) {
		                    	let currentField = selectedResultFields[entityLevel][key];
		                    	let isDateType = availableResultFields[currentField.split(".")[0]][currentField][1] == DATE_TYPE;
		                    	let fieldVal;
		                    	if (isDateType)
		                    		try {
		                    			fieldVal = new Date(fields[j]).toISOString().split('T')[0];
		                    		}
			                    	catch (err) {
			                    		console.log(fields[j] + ": " + err);
			                    	}
			                    else if (fields[j] != null && fields[j].constructor == String)
		                    		fieldVal = fields[j];
		                    	else if (pathToField[pathToField.length -1] == FIELDNAME_PROJECT)
		                    		fieldVal = $("#projectList option[value=" + fields[j] + "]").text();	// display project acronym instead of ID
		                    	else
		                    		fieldVal = JSON.stringify(fields[j]);
			                	tableBody.append((j == 0 ? "" : " ; ") + (fields[j] == null ? "" : fieldVal));
		                    }
		                	tableBody.append("</td>");
		                }
        			}
                	tableBody.append("</tr>");
            	}
    			else { // detailed mode
    	       		let maxSubFieldCount = entityLevel == sampleType ? 1 : Math.max(jsonResult[index][assignmentType].length, jsonResult[index][sampleType].length);
            		let subRows = [];
            		for (let i=0; i<=maxSubFieldCount; i++)
            			subRows.push(new StringBuffer());
            		for (var key in selectedResultFields[entityLevel]) {
	                	let fieldWithEntityPrefix = selectedResultFields[entityLevel][key];
	                	let pathToField = availableResultFields[fieldWithEntityPrefix.split(".")[0]][fieldWithEntityPrefix];
	                    let fields = pathToField == null ? [] : readNestedFields(jsonResult[index], pathToField, pathToField[pathToField.length - 2] == GPS_TYPE ? ", " : " ; ");
                    	let rowspanAttr = fields.length < maxSubFieldCount ? " rowspan='" + (maxSubFieldCount - fields.length + 1) + "'" : "";
                    	if (fields.length == 0)
	                		subRows[0].append("<td" + rowspanAttr + "></td>");
	                    for (let j=0; j<fields.length; j++) {
		                	if (pathToField == null)
		                		subRows[j].append("<td" + (j == fields.length - 1 ? rowspanAttr : "") + "></td>");
		                	else {
		                		subRows[j].append("<td" + (j == fields.length - 1 ? rowspanAttr : "") + " class='" + (key == sseqidColumn ? "sseqid" : "") + (isBestHitAssignment && pathToField[0] == assignmentType ? " bestHitAssignment" + ($('#highlightBestHits input').is(":checked") ? " highlighted" : "") : "") + "'>");

		                    	let currentField = selectedResultFields[entityLevel][key];
		                    	let isDateType = availableResultFields[currentField.split(".")[0]][currentField][1] == DATE_TYPE;
		                    	let fieldVal;
		                    	if (isDateType)
		                    		try {
		                    			fieldVal = new Date(fields[j]).toISOString().split('T')[0];
		                    		}
			                    	catch (err) {
			                    		console.log(fields[j] + ": " + err);
			                    	}
		                    	else if (fields[j] != null && fields[j].constructor == String)
		                    		fieldVal = fields[j];
		                    	else if (pathToField[pathToField.length -1] == FIELDNAME_PROJECT)
		                    		fieldVal = $("#projectList option[value=" + fields[j] + "]").text();	// display project acronym instead of ID
		                    	else
		                    		fieldVal = JSON.stringify(fields[j]);
		                    	subRows[j].append((fields[j] == null ? "" : (key == sseqidColumn ? "<span hidden>" + fieldVal.substring(0, ACCESSION_ID_NUCL_PREFIX.length) + "</span>" + fieldVal.substring(ACCESSION_ID_NUCL_PREFIX.length) : fieldVal)));
			                    subRows[j].append("</td>");
		                    }
	        			}
        			}
            		for (var key in subRows)
            			tableBody.append("<tr class='" + (key == 0 ? "mainRow " : "") + "result_" + index + "'onmouseover=\"$('tr.result_" + index + "').css('background-color', '#f8f8c0');\"  onmouseout=\"$('tr.result_" + index + "').css('background-color', '');\" onclick=\"showDetails(" + jsonResult[index]._id.pj + ", '" + (entityLevel == sampleType ? jsonResult[index]._id : jsonResult[index]._id.qseqid) + "');\">" + subRows[key] + "</tr>");
    			}
	        }
            document.getElementById("tableBody").innerHTML = tableBody;

            if (sseqidColumn != null)
            	$("table.searchResultTable td.sseqid").each(function() {
                	let processedCellContents = new StringBuffer();
            		var sseqidCell = $(this);
            		sseqidCell.text().split(" ; ").forEach(function (accId) {
    				   	let isProt = accId.indexOf(ACCESSION_ID_PROT_PREFIX) == 0;
    				   	let prefixLessId = isProt ? accId.substring(ACCESSION_ID_PROT_PREFIX.length) : (accId.indexOf(ACCESSION_ID_NUCL_PREFIX) == 0 ? accId.substring(ACCESSION_ID_NUCL_PREFIX.length) : accId);
    				   	processedCellContents.append((processedCellContents.buffer.length == 0 ? "" : " ; ") + "<span class='accession-info' title='Click to open accession page' onclick=\"event.stopPropagation(); window.open('http://www.ncbi.nlm.nih.gov/" + (isProt ? 'protein' : 'nuccore') + "/" + prefixLessId.split('\.')[0] + "');\">" + prefixLessId + "</span>");
            		});
                	setTimeout(function () { // do this asynchronously otherwise it takes several seconds
                		sseqidCell.html(processedCellContents.toString());
	            	}, 1);
	            });

            $("#resultTableContainer table th.sortableTableHeader").each(function () {
                this.onclick = function () {
                    applySorting(this.id);
                };
            });
            $("#resultCol_" + $.escapeSelector(sortBy)).addClass("selectedSortableTableHeaderActive_" + (sortDir === 1 ? "asc" : "desc"));
            $("#resultCol_" + $.escapeSelector(sortBy)).prepend("<img src='img/cancel_sort.png' style='margin-left:-7px; margin-right:-7px; margin-top:-6px; float:right;' title='Stop sorting' onclick=\"applySorting(null); event.stopPropagation();\" />");

            isBusySearching(false);
        },
        error: function (xhr, ajaxOptions, thrownError) {
            handleError(xhr);
        }
    });
}

function setResultTableEntityLevel(selectedType) {
	entityLevel = selectedType;
	availableResultFields[entityLevel] = new Array();
	if (totalRecordCount > 0) {
		applySorting(null);
		$('#resCount').html("<img width='18' src='css/throbber.gif'>");
		$("#pagination").hide();
		count();
		pageNumber=0;
	}
}

function Ascending_sort(a, b) { 
    return ($(b).text().toUpperCase()) <  
        ($(a).text().toUpperCase()) ? 1 : -1;  
} 

function toggleSelectedFields(entityType) {
	let inputs = $("#fieldSelectionPanel .fieldType_" + entityType + " input");
	let allChecked = inputs.length == $("#fieldSelectionPanel .fieldType_" + entityType + " input:checked").length;
	inputs.prop('checked', !allChecked);
}

function collapseMultipleValues(flag) {
	if (flag) {
		$("#compactMultipleValues").hide();
		$("#expandedMultipleValues").show();
	}
	else {
		$("#compactMultipleValues").show();
		$("#expandedMultipleValues").hide();
	}
}

function readNestedFields(obj, pathToField, arraySeparator) {
	let result = new Array();
    let field = obj;
	for (let i=0; i<pathToField.length; i++) {
		if (field instanceof Array) {
			for (let j=0; j<field.length; j++) {
				result = result.concat(readNestedFields(field[j], pathToField.slice(i), arraySeparator));
			}
			field = null;
		}
		else {
			if (field != null)
				field = field[pathToField[i]];
			else
				break;
		}
	}
	if (field == null) {
		if (result.length == 0)
			result.push("");
	}
	else {
		if (field instanceof Array) {
			result = arraySeparator == null ? result.concat(field) : field.join(arraySeparator);
		}
		else
			result.push(field);
	}
	return result;
}

function createPaginationWidget() {
    if (!$("#pagination").is(":empty"))
        $("#pagination").twbsPagination('destroy');

    pageNumber = 0;
    let totalPage = (totalRecordCount / pageSize) < 1 ? 1 : Math.ceil(totalRecordCount / pageSize);
    let lastRecordIndex = Math.min(totalRecordCount, pageSize * (1 + pageNumber));
    $('#resCount').html((pageSize * pageNumber + 1) + " - " + lastRecordIndex + " / " + totalRecordCount + " results");
    $('#resCount').show();
    $("#pagination").twbsPagination({
        totalPages: totalPage,
        hideOnlyOnePage: false,
        visiblePages: totalPage < 3 ? totalPage : 3,
        initiateStartPageClick: false,
        first: '\|<<',
        last: '>>\|',
        prev: ' < ',
        next: ' > ',
        onPageClick: function (event, page) {
            pageNumber = page - 1;
            lastRecordIndex = Math.min(totalRecordCount, pageSize * (1 + pageNumber));
            $('#resCount').html(Math.min(totalRecordCount, 1 + pageSize * pageNumber) + " - " + lastRecordIndex + " / " + totalRecordCount + " results");
            loadResults();
        }
    });
    $("#pagination").show();
}

function isBusySearching(fBusy) {
    let pageSpinner = document.getElementById('pageSpinner');
    if (fBusy) {
        spinner.spin(pageSpinner);
        pageSpinner.style.display = "flex";
        $('#fieldSelectionPanel').hide();
    } else {
    	if (spinner != null)
    		spinner.stop();
        pageSpinner.style.display = "none";
        document.getElementById('searchButton').removeAttribute("disabled");
    }
}

function applySorting(sortByCol) {
    $(".selectedSortableTableHeaderActive_asc").removeClass("selectedSortableTableHeaderActive_asc");
    $(".selectedSortableTableHeaderActive_desc").removeClass("selectedSortableTableHeaderActive_desc");

    if (sortByCol == null) {
    	sortBy = null;
    	sortDir = 1;
    }
    else {
    	if ("resultCol_" + sortBy == sortByCol)
            sortDir = sortDir === 1 ? -1 : 1;
        sortBy = sortByCol.replace("resultCol_", "");
    }
    createPaginationWidget();
    loadResults();
}

function showDetails(projId, entityId) {
	if (sampleType == entityLevel)
		$('#seqDetailContent').html("<p class='bold'>" + getSampleDetailsTable(entityId, $("#dbList").val(), SAMPLE_DETAILS_URL, FIELDNAME_PROJECT, DATE_TYPE) + "</p>");
	else
	    $.ajax({
	        url: SEQUENCE_DETAILS_URL,
	        type: 'get',
	        data: {
	            "module": $("#dbList").val(),
	            "pj": projId,
	            "qseqid": entityId
	        },
	        success: function (jsonResult) {                                   
	        	let sequenceTableHeader = "<th>project</th><th>query sequence (qseqid)</th><th>length</th>";
	        	let sequenceTableRows = "<td>" + (jsonResult[projectType][0] == null ? "?" : jsonResult[projectType][0][pjField_name]) + "</td><td style='max-width: 300px; word-break:break-all;'>" + jsonResult._id[qseqidFieldName] + "</td><td>" + (jsonResult[DOUBLE_TYPE] == null ? "" : jsonResult[DOUBLE_TYPE][seqLengthFieldId]) + " bp</td>";
	        	let otherTables = new Array();
	        
	        	let sampleWeights = new Array();
	        	for (var key in jsonResult[FIELDNAME_SAMPLE_COMPOSITION])
	        		sampleWeights[jsonResult[FIELDNAME_SAMPLE_COMPOSITION][key][FIELDNAME_SAMPLE_CODE]] = jsonResult[FIELDNAME_SAMPLE_COMPOSITION][key][FIELDNAME_SAMPLE_WEIGHT];

	        	for (var key in jsonResult) {
	        		if (sampleType == key || assignmentType == key) {
	        			let sseqidColumn, headerPositions = new Array(), tableRows = new Array();
	        			
	        			let maxSubFieldCount = 1;
	
	        			for (let spIndex in jsonResult[key]) {
							let tableRow = new Array();

	        				if (sampleType == key)
	        				{
	    						headerPositions["sample"] = 0;
	    						tableRow[headerPositions["sample"]] = jsonResult[key][spIndex]["_id"];
	    						headerPositions["weight"] = 1;
	    						tableRow[headerPositions["weight"]] = sampleWeights[jsonResult[key][spIndex]["_id"]];
	        				}
	        				
	        				// hack to make sseqid and hit-def appear before numeric fields
	        				let fieldTypeList = assignmentType == key && Object.keys(jsonResult[key][spIndex]).length == 3 ? [STRING_TYPE, STRING_ARRAY_TYPE, DOUBLE_TYPE] : Object.keys(jsonResult[key][spIndex]);

	        				for (let fieldTypeIndex in fieldTypeList) {
	        					let spFieldKey = fieldTypeList[fieldTypeIndex];
	        					if (spFieldKey != "_id" && spFieldKey != FIELDNAME_PROJECT)
	        						for (let sfFieldKey in jsonResult[key][spIndex][spFieldKey]) {

	                            		let headerPos = headerPositions[sfFieldKey];
	                            		if (headerPos == null)
	                            		{
	                            			headerPos = Object.keys(headerPositions).length;
	                            			headerPositions[sfFieldKey] = headerPos;
	            	                    	if (sfFieldKey == sseqidFieldId) {
	            	                    		sseqidColumn = headerPos;
	            	    						if (assignmentType == key)
	            	    							try {
	            	    								maxSubFieldCount = jsonResult[key][spIndex][STRING_ARRAY_TYPE][sseqidFieldId].length;
	            	    							}
	            	    							catch (err) {}
	            	                    	}
	                            		}
	                            		let cellContents = jsonResult[key][spIndex][spFieldKey][sfFieldKey];
	                            		tableRow[headerPos] = GPS_TYPE == spFieldKey && Array.isArray(cellContents) ? cellContents.join(", ") : (DATE_TYPE == spFieldKey ? new Date(cellContents).toISOString().split('T')[0] : cellContents);
	            					}
	        				}
        					tableRows.push(tableRow);
	        			}
	        			
	                    let tableHeader = new Array();
	                    for (let header in headerPositions) {
	                    	tableHeader[headerPositions[header]] = !isNaN(header) ? $("#filterList option[value=\"" + header + "\"]").text().replace(/_/g, " ") : header;
	                    }
	                    
	                    let htmlTableContents = new StringBuffer();
	                    htmlTableContents.append('<tr>');
	                    for (let headerPos in tableHeader)
	                    	htmlTableContents.append('<th>' + tableHeader[headerPos] + '</th>');
	                    htmlTableContents.append('</tr>');
	                    	                    
	                    for (let row in tableRows)
	                    {
	                		let subRows = [];
	                		for (let i=0; i<=maxSubFieldCount; i++)
	                			subRows.push(new StringBuffer());

	                        for (let i=0; i<tableHeader.length; i++) {
	                        	let dataAsArray = Array.isArray(tableRows[row][i]) ? tableRows[row][i] : [tableRows[row][i]];
								let rowspanAttr = dataAsArray.length < maxSubFieldCount ? " rowspan='" + (maxSubFieldCount - dataAsArray.length + 1) + "'" : "";

	                        	for (let j=0; j<dataAsArray.length; j++) {
		                        	let onclickString = "";
		                        	if (sseqidColumn == i && dataAsArray[j] != null) {		                        		

	                				   	let isProt = dataAsArray[j].indexOf(ACCESSION_ID_PROT_PREFIX) == 0;
	                				   	dataAsArray[j] = isProt ? dataAsArray[j].substring(ACCESSION_ID_PROT_PREFIX.length) : (dataAsArray[j].indexOf(ACCESSION_ID_NUCL_PREFIX) == 0 ? dataAsArray[j].substring(ACCESSION_ID_NUCL_PREFIX.length) : dataAsArray[j]);
		                            	
		                        		onclickString = " class='accession-info pointer' title='Click to open accession page' onclick='window.open(\"http://www.ncbi.nlm.nih.gov/" + (isProt ? "protein" : "nuccore") + "/" + dataAsArray[j].split("\.")[0] + "\");'";
		                        	}
		                        	subRows[j].append("<td" + (j == dataAsArray.length - 1 ? rowspanAttr : "") + onclickString + (dataAsArray[j] != null && dataAsArray[j].length > 50 ? " style='min-width:150px; word-break:break-all;'" : "") + ">" + (dataAsArray[j] != null ? dataAsArray[j] : "") + "</td>");
	                        	}
	                        }
	                		for (var i in subRows)
	                			htmlTableContents.append("<tr" + (i == 0 ? " class='mainRow'" : "") + ">" + subRows[i] + "</tr>");
	                    }
	                    otherTables[key] = htmlTableContents;
	        		}
	        	}

	            $('#seqDetailContent').html("<div style='overflow-wrap: break-word; width:510px; height:70px; border:1px dashed blue; overflow-y:auto; font-size:10px; font-family:monospace; position:absolute; right:15px;'><div style='width:100%; text-align:center;'><button style='margin-top:25px;' onclick=\"loadNuclSeq(this, '" + projId + "', '" + entityId + "');\">Click to display nucleotide sequence</button></div></div>");
	            $('#seqDetailContent').append("<p class='bold'><table class='seqDetailTable'><tr>" + sequenceTableHeader + "</tr><tr class='mainRow'>" + sequenceTableRows + "</tr></table></p>");
	            $('#seqDetailContent').append("<p class='bold'>Sample(s) sequence is issued from<table class='seqDetailTable'>" + otherTables[sampleType] + "</table></p>");
	            $('#seqDetailContent').append("<p class='bold'>Assignments for this sequence<table class='seqDetailTable'>" + otherTables[assignmentType] + "</table></p>");
	        },
	        error: function (xhr, ajaxOptions, thrownError) {
	            handleError(xhr);
	        }
	    });
    $("#sequenceInfoDialog").modal();
}

function loadNuclSeq(buttonObj, projId, qseqid) {
	let nuclSeqZone = $(buttonObj).parent(), zoneContainer = nuclSeqZone.parent();
	$(buttonObj).replaceWith("<div style='position:absolute; margin-left:45%; margin-top:15px;'><img width='32' src='css/throbber.gif'></div>");
    $.ajax({
        url: NUCL_SEQUENCE_URL + "?module=" + $("#dbList").val() + "&project=" + projId + "&qseqid=" + encodeURIComponent(qseqid),
        async: true,
        method: "GET",
        success: function (nuclSeq) {
        	nuclSeqZone.replaceWith(nuclSeq.replace(/\n/g, "<br/>"));
        	zoneContainer.click(function () {
        		zoneContainer.selectText();
        	});
        },
        error: function (xhr, ajaxOptions, thrownError) {
        	nuclSeqZone.replaceWith("<div style='margin-left:30%;' class='margin-top-md'>Unable to load sequence, sorry!</div>");
            handleError(xhr);
        }
    });
}
	
function getAssignmentMethods() {
    $('select#filterList option').filter(function() { 
        if ($(this).text() == assignMethodFieldName)
        	assignMethodFieldId = $(this).val();
    });

	let assignmentMethods;
    $.ajax({
    	async: false,
        url: SEARCHABLE_FIELD_INFO_URL,
        method: 'GET',
        data: {
            "module": $("#dbList").val(),
            "projects": getProjectList().join(";"),
            "field": assignMethodFieldId,
            "type": STRING_TYPE
        },
        success: function (values) {
        	assignmentMethods = values;
        },
        error: function () {
            console.log("couldn't read values for assignment method field");
        }
    });
    return assignmentMethods;
}

function toggleResultViewMode(mode) {
    $('#resultDisplayModes > img').each(function () {
        $(this).removeClass('selectedMode');
    });
    $('#content > div').each(function () {
        $(this).hide();
    });

    $('#pagination').hide();
    $('#resCount').hide();
    $("#dbProjectSummary").html("");
    
	$("#assignMethodForTaxoResults").html("");
    if (mode == "pie" || mode == "tree") {
    	let values = getAssignmentMethods();
        if (values.length > 1)
        {
        	let methodToSelect = $('#widget_' + assignMethodFieldId).selectpicker('val').length == 1 ? $('#widget_' + assignMethodFieldId).selectpicker('val') : null;
            for (var i=0; i<values.length; i++)
            	$("#assignMethodForTaxoResults").append("<option" + (methodToSelect == values[i] ? " selected" : "") + ">" + values[i] + "</option>");
            $("#assignMethodForTaxoResults").selectpicker('refresh');
        	$("#assignMethodForTaxoResultsDiv").show();
        }
        else
        	$("#assignMethodForTaxoResultsDiv").hide();
    }
    else
    	$("#assignMethodForTaxoResultsDiv").hide();

    switch (mode) {
        case "table":
            document.getElementById('tableResultModeButton').classList.add('selectedMode');
            document.getElementById('resultTableContainer').style.display = 'block';
            document.getElementById('resultTableNavigation').style.display = 'block';
            $('#pagination').show();
            $('#resCount').show();
            break;

        case "tree":
        	document.getElementById('resultTableNavigation').style.display = 'none';
            let treeContainer = document.getElementById('resultTreeContainer');
            var height = window.innerHeight - (treeContainer.offsetTop + 160);
            treeContainer.style.display = 'block';
            isBusySearching(true);
            document.getElementById('treeResultModeButton').classList.add('selectedMode');
            if ($('#tree').jstree("get_node", "1")['id'] == null) {
                loadResultTree(false);
            } else {
                isBusySearching(false);
            }
            break;

        case "map":
        	showMap();
            break;

        case "pie":
        	document.getElementById('resultTableNavigation').style.display = 'none';
            document.getElementById('pieResultModeButton').classList.add('selectedMode');
            isBusySearching(true);
            if ($('#tree').jstree("get_node", "1")['id'] == null) {
                loadResultTree(true);
            } else {
                showResultPie();
            }
            document.getElementById('resultPieContainer').style.display = 'block';
            break;
    }
}

function applyGpsSelection(fieldId) {
	let bounds = areaSelect.getBounds();
	let area = "[" + bounds.getSouthWest().lng.toFixed(2) + "," + bounds.getSouthWest().lat.toFixed(2) + "], [" + bounds.getNorthEast().lng.toFixed(2) + "," + bounds.getNorthEast().lat.toFixed(2) + "]";
	$('#GPSpos').val(area).prop('disabled', true);
	document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
	$('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
	document.getElementById('widgetContainer_' + fieldId).setAttribute('query', bounds.getSouthWest().lat + "¤" + bounds.getSouthWest().lng + "¤" + bounds.getNorthEast().lat + "¤" + bounds.getNorthEast().lng);
	$('#resultMapContainer').hide();
}

function showMap(gpsFieldId	/* if null, displaying results, if not null, displaying filter */) {
	$('#resultTableNavigation').hide();
	$('#noResultsMessage').hide();
    let mapDiv = document.getElementById('map');

    $(window).resize(function () {
        mapDiv.style.height = (Math.min(880, window.innerHeight - (mapDiv.offsetTop + 160))) + 'px';
        mapDiv.style.width = (Math.min(1025, window.innerWidth - $("#menuwidget").width() - 15)) + 'px';
    });
    $(window).resize();

    gpsMap.removeLayer(markers);
    markers.clearLayers();
    let data = { "module": $("#dbList").val() };
   	data["projects"] = getProjectList().join(";");
    if (gpsFieldId == null)
    	data["processId"] = currentProcessId;

    isBusySearching(true);
    $("div#resultMapContainer div#mapInfo").html((gpsFieldId != null ? "<input type='button' class='btn btn-primary btn-sm' style='margin-top:-20px; margin-right:20px;' onclick='applyGpsSelection(\"" + gpsFieldId + "\");' value='Apply region selection' />&nbsp;<input type='button' class='btn btn-cancel btn-sm' style='margin-top:-20px; margin-right:50px;' onclick=\"$('#resultMapContainer').hide(); adaptDisplayToResultCount();\" value='Cancel selection' />" : "") + "<span></span>");
    $.ajax({
        url: GPS_POSITION_URL,
        method: 'GET',
        data: data,
        success: function (sampleList) {
            isBusySearching(false);
        	let nUnpositionedCount = 0;
            for (let index in sampleList) {
            	if (sampleList[index][FIELDNAME_COLLECT_GPS] == null)
            		nUnpositionedCount ++;
            	else {
	                let marker = new L.marker(sampleList[index][FIELDNAME_COLLECT_GPS]).bindPopup(sampleList[index]._id);
	                markers.addLayer(marker);
            	}
            }
            if (nUnpositionedCount > 0)
            	$("div#resultMapContainer div#mapInfo").append(nUnpositionedCount + " samples have no position info");
            gpsMap.addLayer(markers);
        },
        error: function () {
            console.log("fail to load map markers");
        }
    });
    
    gpsMap.on('zoom', function (e) {
    	gpsMap.closePopup();
    });

    gpsMap.on('popupopen', function (e) {
        if (! /<\/?[a-z][\s\S]*>/i.test(e.popup._content))
        	e.popup.setContent(getSampleDetailsTable(e.popup._content, $("#dbList").val(), SAMPLE_DETAILS_URL, FIELDNAME_PROJECT, DATE_TYPE));	// load complete sample description
        
    	let oldWidth = $(e.popup._contentNode).width();
    	$(e.popup._contentNode).css('width', "");
    	let newWidth = $(e.popup._contentNode).width();	
    	let node = $(e.popup._contentNode).parent().parent();
    	let newLeft = parseInt(node.css('left').replace("px", "")) - (newWidth - oldWidth) / 2;
    	node.css('left', newLeft + 'px');
    });

    document.getElementById('mapResultModeButton').classList.add('selectedMode');
    document.getElementById('resultMapContainer').style.display = 'block';
    setTimeout(function () {
        gpsMap.setView([25, 0], 2);
        gpsMap.invalidateSize();
    }, 100);
    $(".leaflet-control-attribution a").attr("target", "_blank");
    
    if (gpsFieldId == null /* displaying results */ && areaSelect !== null) {	// we don't need to be able to select a zone
    	areaSelect.remove();
    	areaSelect = null;
    }
    else if (gpsFieldId != null /* displaying filter */ && areaSelect == null) {
        areaSelect = L.areaSelect({width: 50, height: 50});
        areaSelect.addTo(gpsMap);
    }
}

function getFieldName(fieldId) {
	return $("#filterList option[value=\"" + fieldId + "\"]").text();
}

function loadResultTree(showPie) {
    $.ajax({
        url: TAXO_TREE_URL,
        method: 'GET',
        data: {
            "module": $("#dbList").val(),
            "processId": currentProcessId,
            "assignMethod": $("#assignMethodForTaxoResults").val(),
            "projects": getProjectList().join(";")
        },
        success: function (jsonTree) {
        	if (jsonTree == null || Object.keys(jsonTree).length == 0) {
        		isBusySearching(false);
        		alert("No data to display");
        		return;
        	}
            let tree = $("#tree");
            tree.jstree({
                'core': {
                    'data': jsonTree,
                    'themes': {
                        'dots': true,
                        'icons': false
                    },
                    'multiple': true
                }
            }).on("loaded.jstree", function (e, data) {
            	let currentNode = "1";
            	tree.jstree("open_node", currentNode);
                while (tree.jstree('get_node', currentNode).children.length == 1) {
                	currentNode = tree.jstree('get_node', currentNode).children[0];
                	tree.jstree("open_node", currentNode);
                }
                if (showPie)
                    showResultPie();
                isBusySearching(false);
            });
        },
        error: function (xhr, ajaxOptions, thrownError) {
            handleError(xhr);
        }
    });
}

function showResultPie() {
    if ($('#resultPieContainer').html() === "") {
        let tree = $('#tree');
        document.getElementById('resultPieContainer').innerHTML = '<form action="pie.jsp" method="post" target="pieFrame" id="pieForm"><input type="hidden" name="pieData" id="pieData" /></form>'
                + '<iframe name="pieFrame" id="pieFrame" style="position:absolute; width:calc(100% - 300px); height:calc(100% - 170px); border:0;">';
        $("#pieData").val(convertNodeToXml(tree, 1));
        $('#pieForm').submit();
    }
    isBusySearching(false);
}

function convertNodeToXml(tree, parentTaxonId) {
    let parentTaxon = tree.jstree('get_node', parentTaxonId);
    let children = parentTaxon['children'];
    let bracketPos = parentTaxon.text.lastIndexOf(" {");
    let nodeXml = "<node name=\"" + (bracketPos != -1 ? parentTaxon.text.substring(0, bracketPos) : parentTaxon.text) + "\"><magnitude><val>" + (bracketPos != -1 ? "" + parentTaxon.text.substring(bracketPos + 2).replace("}", "") : 1) + "</val></magnitude>";
    for (let i = 0; i < children.length; i++) {
        nodeXml += convertNodeToXml(tree, children[i]);
    }
    nodeXml += "</node>";
    return nodeXml;
}

function handleError(xhr) {
	if (!xhr.getAllResponseHeaders())
		return;	// user is probably leaving the current page
	
    if (xhr.status == 403) {
        isBusySearching(false);
        alert("You do not have access to this resource");
        return;
    }

  	var errorMsg;
  	if (xhr != null && xhr.responseText != null) {
  		try {
  			errorMsg = $.parseJSON(xhr.responseText)['errorMsg'];
  		}
  		catch (err) {
  			errorMsg = xhr.responseText;
  		}
  	}
    isBusySearching(false);
  	alert(errorMsg);
}

function addListWidget(fieldId, entityType, fieldLabel, values, ajax) {
    let options = "";
    for (var key in values)
        options += "<option value='" + values[key] + "' >" + values[key] + "</option>";

    $('#widgetContainer_' + fieldId).html('<div class="form-group margin-top-sm"><input style="margin-left:5px;" class="missingData" type="checkbox" checked title="Allow data with missing value" /> <label for="widget_' + fieldId + '" class="fieldTypeTextColor_' + entityType + '">' + fieldLabel + '</label><div class="form-input form-input-sm"><select id="widget_' + fieldId + '" class="selectpicker" data-container="body" multiple data-live-search="true" data-size="10"></select></div></div>');
    
    if (!ajax)
        $("#widget_" + fieldId).html(options).selectpicker('refresh');
    else {
    	var ajaxSelectPickerOptions = {
		    ajax          : {
		        url     : SEARCHABLE_LIST_FIELD_LOOKUP_URL,
		        type    : 'POST',
		        dataType: 'json',
		        data    : {
		            "module": $("#dbList").val(),
		            "projects": getProjectList().join(";"),
		            "field": fieldId,
		            q: '{{{q}}}'
		        }
		    },
		    cache : false,
	        preserveSelectedPosition : "before",
		    log           : 2 /*warn*/,
		    preprocessData: function (data) {
		    	$("div.bs-container.dropdown.bootstrap-select.show-tick.open > div > div.inner.open > ul").css("margin-bottom", "0");
		    	var asp = this;
		    	if (data.length == 1 && data[0].indexOf("Too many results (") == 0) {
		    		setTimeout(function() {asp.plugin.list.setStatus(data[0]);}, 50);
		    		return;
		    	}
		    	
		        var array = [];
	            for (i=0; i<data.length; i++) {
	                array.push($.extend(true, data[i], {
	                    value: data[i]
	                }));
		        }
		        return array;
		    }
		};
    	let asp = $("#widget_" + fieldId).selectpicker();
    	asp.ajaxSelectPicker(ajaxSelectPickerOptions);
    }

    $('#widget_' + fieldId).change(function () {
        if ($(this).selectpicker('val').length > 0) {
            document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', $(this).selectpicker('val').join("¤"));
        } else {
            document.getElementById('widgetContainer_' + fieldId).classList.remove("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", true);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', '');
        }
    });
    
    let inputObj = $('#widgetContainer_' + fieldId).find("div.bs-searchbox input");
    inputObj.css('width', "calc(100% - 24px)");
    inputObj.before("<a href=\"#\" onclick=\"$('#widget_" + fieldId + "').selectpicker('deselectAll').selectpicker('toggle');\" style='font-size:18px; margin-top:5px; font-weight:bold; text-decoration: none; float:right;' title='Clear selection'>&nbsp;X&nbsp;</a>");
    
    if (showAssignMethodByDefault && (fieldId == assignMethodFieldId || fieldId == bestHitFieldId))
    	$('#widget_' + fieldId).selectpicker('val', fieldId == assignMethodFieldId ? values[0] : values);
}

function addRangeWidget(fieldId, entityType, fieldLabel, rangeMin, rangeMax) {

    let title = '<div class="row margin-top-sm"><p class="label-widget fieldTypeTextColor_' + entityType + ' text-nowrap"><input class="missingData" type="checkbox" checked title="Allow data with missing value" /> ' + fieldLabel + '</p></div>';
    let input_grp = '<div style="position:absolute; left:125px; padding-left:1px;"><div class="input-group input-group-sm"><span class="input-group-addon">max</span><input type="text" id="Max_' + fieldId + '" class="form-control" value="' + rangeMax + '" rel="' + rangeMax + '"></div></div>';
    input_grp += '<div style="padding-right:1px;"><div class="input-group input-group-sm"><span class="input-group-addon">min</span><input type="text" id="Min_' + fieldId + '" class="form-control" value="' + rangeMin + '" rel="' + rangeMin + '" ></div></div>';
    let widgetContainer = $('#widgetContainer_' + fieldId);
    widgetContainer.html(title + input_grp);

    $('#Min_' + fieldId).change(function () {
        let absoluteMax = parseFloat($('#widgetContainer_' + fieldId + ' #Max_' + fieldId).attr('rel'));
        let absoluteMin = parseFloat($(this).attr('rel'));
        let rangeMax = parseFloat($('#Max_' + fieldId).val());
        let currentVal = $(this).val();
        if (regexp.test(currentVal)) {
            let rangeMin = parseFloat(currentVal);
            if (rangeMin > rangeMax)
                $(this).addClass('highlighterror');
            else
                $(this).removeClass('highlighterror');

            if (rangeMin < absoluteMin || currentVal == "") {
                $(this).val(absoluteMin);
            } else {
                document.getElementById('widgetContainer_' + fieldId).setAttribute('query', $(this).val() + "¤" + $('#Max_' + fieldId).val());
            }
        } else {
            $(this).val(absoluteMin);
            $(this).removeClass('highlighterror');
        }

        let rangeMin = parseFloat($(this).val());
        if (rangeMin > absoluteMin || rangeMax < absoluteMax) {
            document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', rangeMin + "¤" + rangeMax);
        }
        else {
            document.getElementById('widgetContainer_' + fieldId).classList.remove("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", true);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', absoluteMin + "¤" + absoluteMax);
    	}
    });

    $('#Max_' + fieldId).change(function () {
        let absoluteMin = parseFloat($('#widgetContainer_' + fieldId + ' #Min_' + fieldId).attr('rel'));
        let absoluteMax = parseFloat($(this).attr('rel'));
        let rangeMin = parseFloat($('#Min_' + fieldId).val());
        let currentVal = $(this).val();
        if (regexp.test(currentVal)) {
            let rangeMax = parseFloat(currentVal);
            if (rangeMax < rangeMin)
                $(this).addClass('highlighterror');
            else
                $(this).removeClass('highlighterror');

            if (rangeMax > absoluteMax || currentVal == "") {
                $(this).val(absoluteMax);
            }
        } else {
            $(this).val(absoluteMax);
            $(this).removeClass('highlighterror');
        }
        
        let rangeMax = parseFloat($(this).val());
        if (rangeMin > absoluteMin || rangeMax < absoluteMax) {
            document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', rangeMin + "¤" + rangeMax);
        }
        else {
            document.getElementById('widgetContainer_' + fieldId).classList.remove("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", true);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', absoluteMin + "¤" + absoluteMax);
    	}
    });
}

function addGPSWidget(fieldId, entityType, fieldLabel) {
    let title = '<div class="row margin-top-sm"><p class="label-widget fieldTypeTextColor_' + entityType + ' text-nowrap"><input class="missingData" type="checkbox" checked title="Allow data with missing value" /> ' + fieldLabel + '</p></div>';
    let input_grp = "<a href=\"#\" onclick=\"$('#GPSpos').val(''); $('#widgetContainer_" + fieldId + " input.missingData').prop('checked', true); document.getElementById('widgetContainer_" + fieldId + "').classList.remove('usedWidget'); document.getElementById('widgetContainer_" + fieldId + "').setAttribute('query', ''); if (areaSelect != null) $('#resultMapContainer').hide();\" style='font-size:18px; margin-top:5px; font-weight:bold; text-decoration:none; position:absolute; left:220px;' title='Clear selection'>&nbsp;X&nbsp;</a>" + '<div class="input-group input-group-sm" style="width:210px;"><div class="input-group-btn"><input type="text" id="GPSpos" style="width:155px !important; padding:5px;" class="form-control form-control-grp input-sm text-sm"><button type="button" style="padding:5px 7px;" id="showMapButton" class="btn btn-left-corner">Select</button></div></div>';
    document.getElementById('widgetContainer_' + fieldId).innerHTML = title + input_grp;
    $('#showMapButton').click(function () {
        $('#content > div').each(function () {
            $(this).hide();
        });

        $('#resultDisplayModes').hide();
        $('#pagination').hide();
        $('#resCount').hide();
        $("#dbProjectSummary").html("");
      	$("#assignMethodForTaxoResultsDiv").hide();
    	
	    showMap(fieldId);
    });
}

function addDateWidget(fieldId, entityType, fieldLabel, start, end) {
    let content = '<div class="row margin-top-sm"><p class="label-widget fieldTypeTextColor_' + entityType + ' text-nowrap"><input class="missingData" type="checkbox" checked title="Allow data with missing value" /> ' + fieldLabel + " (yyyy-MM-dd)" + '</p></div><div class="input-daterange input-group" id="datepicker"><input type="text" id="start_' + fieldId + '" class="input-sm form-control" name="start" /><span class="input-group-addon">to</span><input type="text" id="end_' + fieldId + '" class="input-sm form-control" name="end" /></div>';
    document.getElementById('widgetContainer_' + fieldId).innerHTML = content;
    
    let splitStartDate = start.split("-");
    $('.input-daterange input#start_' + fieldId).datepicker({
        format: 'yyyy-mm-dd',
        endDate: moment(end).toDate(),
        startDate: moment(start).toDate(),
        defaultViewDate: {
	        year: splitStartDate[0],
	        month: splitStartDate[1] - 1,
	        day: splitStartDate[2]
	    }
    }).on('changeDate', function (ev) {
    	onDateWidgetUpdate(fieldId);
    }).on('clearDate', function (ev) {
    	onDateWidgetUpdate(fieldId);
    });
    
    let endDate = end.split("-");
    $('.input-daterange input#end_' + fieldId).datepicker({
        format: 'yyyy-mm-dd',
        endDate: moment(end).toDate(),
        startDate: moment(start).toDate(),
        defaultViewDate: {
	        year: endDate[0],
	        month: endDate[1] - 1,
	        day: endDate[2]
	    }
    }).on('changeDate', function (ev) {
    	onDateWidgetUpdate(fieldId);
    }).on('clearDate', function (ev) {
    	onDateWidgetUpdate(fieldId);
    });
}

function onDateWidgetUpdate(fieldId) {
    if (null != $("#start_" + fieldId).datepicker('getDate') || null != $("#end_" + fieldId).datepicker('getDate')) {
        document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
        $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
        document.getElementById('widgetContainer_' + fieldId).setAttribute('query', $("#start_" + fieldId).val() + "¤" + $("#end_" + fieldId).val());
    } else {
        document.getElementById('widgetContainer_' + fieldId).classList.remove("usedWidget");
        $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", true);
        document.getElementById('widgetContainer_' + fieldId).setAttribute('query', "");
    }
    console.log(document.getElementById('widgetContainer_' + fieldId).getAttribute('query'));
}

function addTreeWidget(fieldId, entityType, fieldLabel, treeDataUrl) {
    let title = '<div class="row margin-top-sm"><p class="label-widget fieldTypeTextColor_' + entityType + ' text-nowrap"><input class="missingData" type="checkbox" checked title="Allow data with missing value" /> ' + fieldLabel + '</p></div>';
    let input_grp = '<div class="input-group input-group-sm"><div class="input-group-btn"><input readonly style="width: 165px !important;" type="text" id="' + fieldId + '" class="form-control form-control-grp input-sm" /><button id="treebox' + fieldId + '" class="btn btn-primary btn-left-corner">Select</button></div></div>';
    document.getElementById('widgetContainer_' + fieldId).innerHTML = title + input_grp;

    $('#' + fieldId).change(function () {
        if ($(this).val() !== "") {
            document.getElementById('widgetContainer_' + fieldId).classList.add("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", false);
            document.getElementById('widgetContainer_' + fieldId).setAttribute('query', $(this).val());
        } else {
            document.getElementById('widgetContainer_' + fieldId).classList.remove("usedWidget");
            $('#widgetContainer_' + fieldId + ' input.missingData').prop("checked", true);
        }
    });

    $('#treebox' + fieldId).click(function () {
    	isBusySearching(true);
        if ($("#assignMethodForTaxoFilter").html() == "") {
        	let values = getAssignmentMethods();
            if (values.length > 1) {
                for (var i=0; i<values.length; i++)
                	$("#assignMethodForTaxoFilter").append("<option>" + values[i] + "</option>");
                $("#assignMethodForTaxoFilter").selectpicker('refresh');
            	$("#assignMethodForTaxoFilterDiv").show();
            	$("#seqCountExplanation").show();
            	if (bestHitFieldId == null)
            		$("#bestHitMention").hide();
            	else
            		$("#bestHitMention").show();
            }
            else {
            	$("#assignMethodForTaxoFilterDiv").hide();
            	$("#seqCountExplanation").hide();
            }
        }
        else {
        	$("#assignMethodForTaxoFilterDiv").show()
        	$("#seqCountExplanation").show();
        	if (bestHitFieldId == null)
        		$("#bestHitMention").hide();
        	else
        		$("#bestHitMention").show();
        }

        setTimeout(function() {
	        $.ajax({
	            url: TAXO_TREE_URL,
	            async: false,
	            method: "GET",
	            data: {
	                "module": $("#dbList").val(),
	                "assignMethod": $("#assignMethodForTaxoFilter").val(),
	                "projects": getProjectList().length == $("#projectList option").length ? null : getProjectList().join(";")
	            },
	            success: function (jsonTree) {
	            	isBusySearching(false);
	                $('#treeContent').html('<div class="modal-body" id="treeContainer"></div>');
	                tree = $('#treeContainer');
	                tree.jstree({
	                    'core': {
	                        'data': jsonTree,
	                        'themes': {
	                            'dots': true,
	                            'icons': false
	                        },
	                        'multiple': true
	                    },
	                    'search': {
	                        'case_insensitive': true,
	                        'show_only_matches': true
	                    },
	                    'plugins': ['search']
	                }).on("loaded.jstree", function (e, data) {
	                	let currentNode = "1";
	                	tree.jstree("open_node", currentNode);
	                    while (tree.jstree('get_node', currentNode).children.length == 1) {
	                    	currentNode = tree.jstree('get_node', currentNode).children[0];
	                    	tree.jstree("open_node", currentNode);
	                    }
	                    $('#' + fieldId).val().split("¤").forEach(taxId => tree.jstree('select_node', taxId));
	                    $('#treeModal').modal();
	                })/*.bind("open_node.jstree", function (event, data) {
	                    let children = tree.jstree('get_node', data.node.id)['children'];
	                    for (let i = 0; i < children.length; i++) {
	                        let childNode = tree.jstree('get_node', children[i]);
	                        if (childNode['children'].length == 0) {
	                            continue;
	                        }
	                        let speciesArray = new Array();
	                        findTaxonDescendence(tree, children[i], speciesArray, true);
	                        let suffix = " {" + speciesArray.length + "}";
	                        if (!childNode.text.endsWith(suffix)) {
	                            tree.jstree('set_text', childNode, childNode.text + suffix);
	                        }
	                    }
	                })*/;
	                $("#choose").click(function () {
	                    tree = $('#treeContainer');
	                    let topSelectionArray = tree.jstree('get_selected');
	                    if (topSelectionArray.length == 0 || (topSelectionArray.length == 1 && topSelectionArray[0] == "1")) {
	                        $('#' + fieldId).val("");
	                        $('#treeWidget' + fieldId).val("");
	                        $('#' + fieldId).attr("rel", "");
	                        $('#' + fieldId).trigger("change");
	                    } else {
	                        $('#' + fieldId).val(topSelectionArray.join("¤"));
	                        $('#treeWidget' + fieldId).val(topSelectionArray.length == 1 ? tree.jstree('get_node', topSelectionArray[0])['text'] : ("{" + topSelectionArray.length + " items selected]"));
	                        $('#' + fieldId).attr("rel", topSelectionArray.join("¤"));
	                        $('#' + fieldId).trigger("change");
	                    }
	                    $('#treeModal').modal('hide');
	                    $('#treeWidget' + fieldId).change();
	                });
	            },
	            error: function (xhr, ajaxOptions, thrownError) {
	                handleError(xhr);
	            }
	        });
        }, 1);
    });
}

function findTaxonDescendence(tree, parentTaxonId, taxonIdArray) {
    let children = tree.jstree('get_node', parentTaxonId)['children'];
    taxonIdArray.push(parentTaxonId);
    for (let i = 0; i < children.length; i++) {
        findTaxonDescendence(tree, children[i], taxonIdArray);
    }
}

function clearDetailContent() {
    $('#seqDetailContent').html('');
}

function arrayContains(array, element) 
{
	for (let i = 0; i < array.length; i++) 
        if (array[i] == element) 
        	return true;
	return false;
}

function arrayContainsIgnoreCase(array, element)
{
	for (let i = 0; i < array.length; i++) 
        if ((array[i] == null && element == null) || (array[i] != null && element != null && array[i].toLowerCase() == element.toLowerCase())) 
        	return true;
	return false;
}

function updateDisplayedFields() {
	selectedResultFields[entityLevel] = new Array();
	$("div#fieldSelectionPanel input").each(function() {
		if ($(this).is(":checked"))
			selectedResultFields[entityLevel].push($(this).val())
	});
	loadResults();
}

function configureSelectedExternalTool() {
	var config = getOutputToolConfig($("#onlineOutputTools").val());
	$('#outputToolURL').val(config['url']);
	$("#applyOutputToolConfig").prop('disabled', 'disabled');
}

function checkIfOuputToolConfigChanged() {
	var changed = $('#outputToolURL').val() != $('#outputToolURL').prop('previousVal');
	$("#applyOutputToolConfig").prop('disabled', changed ? false : 'disabled');
}

function getOutputToolConfig(toolName)
{
	var storedToolConfig = localStorage.getItem("outputTool_" + toolName);
	return storedToolConfig != null ? JSON.parse(storedToolConfig) : onlineOutputTools[toolName];
}