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
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri = "http://java.sun.com/jsp/jstl/functions" prefix = "fn" %>
<%@page contentType="text/html" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController,fr.cirad.metaxplor.model.Sample,fr.cirad.tools.mongo.DBConstant,fr.cirad.tools.AppConfig"%>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <title>BLAST results</title>
        <style type="text/css">
        #pageSpinner {
		    width: 100%;
		    height: 100%;
		    background-color: rgba(0, 0, 0, .8);
		    display: none;
		    align-items: center;
		    justify-content: center;
		    position: fixed;
		    z-index: 1000;
		    top: 0;
		    left: 0;
		}
		
		div#pageSpinnerText{
			position:absolute;
		    color:#ffffff;
		    margin-top:-100px;
		    font-size:16px;
		    font-weight:bold;
		}
		
		table.seqDetailTable td div:not(:first-child), table.searchResultTable div:not(:first-child) {
			border-top:1px dashed lightgrey;
		}
		
		table.seqDetailTable th, table.seqDetailTable td {
			font-size:11px;
			border: 1px solid grey;
		}
		
		table.seqDetailTable th {
			background-color:#dcddde;
			padding:5px;
		}
		
		.tooltip.in {
		    opacity:1;
		    filter:alpha(opacity=100);
		}
		
		.tooltip > .tooltip-inner {
		    background-color:#f5f5f5;
 		    color:black;  
		    box-shadow 0 3px 14px rgba(0,0,0,0.4);
		    border: 1px solid #777777; 
		    padding:10px;
		    max-width:none;
		  }
        </style>
    </head>
    <body class="bg-dark">
        <%@include file="navbar.jsp" %>
        
        <c:if test="${fn:length(param.processId) gt 0}">     
	        <div class="container-fluid container-fluid-no-padding full-width" style="margin-top:70px;">	        
	            <div id="pageSpinner">
	                <div id="pageSpinnerText">Loading...</div>        
	            </div>
	            <div class="row">
		            <div class="col-md-8">
		                <div class="row text-center">
		                    <h2 id="title" class="margin-top-md"></h2>
		                    Displayed using BlasterJS<a href='https://doi.org/10.1371/journal.pone.0205286' target='_blank' title="Blanco-Míguez, A., Fdez-Riverola, F., Sánchez, B., & Lourenço, A. (2018). BlasterJS: A novel interactive JavaScript visualisation component for BLAST alignment results. https://doi.org/10.1371/journal.pone.0205286"><img src='img/books.png'/></a>
		                </div>
		            </div>
		            <div class="col-md-4">
			        Results for project
			        <select id="projectList" class="select-main" onchange="loadCurrentProjectResults();"></select>
		            </div>
		        </div>
	            <div class="row">
	                <div class="col-md-8">
	                    <p id="queryId" class="text-sm"></p>
	                    <p id="evalue" class="text-sm"></p>
	                    <p id="nbalign" class="text-sm"></p>
	                    <button type="button" class="btn btn-primary" onclick="resubmit();">Re-submit</button>
	                </div>
	                <div class="col-md-4">
	                	<p>You may download:</p>
						<button type="button" class="btn btn-primary" id="downloadButton" onclick="saveAs(new Blob([blasterJsInstance.opt.string], {type: 'text/plain;charset=utf-8'}), 'blast.out');">Raw blast output</button>
	                	<button type="button" class="btn btn-primary" id="downloadButton" onclick="downloadSequences();">Subject sequence(s)</button>
	                </div>
	            </div>
	            <a id="file" hidden></a>
	            <div class="row">
	                <div id="content" class="result-div font-sm"></div>
	            </div>
	        </div>
	
		    <script type="text/javascript" src="js/jquery.min.js"></script>
			<script type="text/javascript" src="js/jquery.binarytransport.js"></script>
		    <script type="text/javascript" src="js/spin.min.js"></script>
			<script type="text/javascript" src="js/html2canvas.js"></script>
		    <script type="text/javascript" src="js/blaster.min.js"></script>
		    <script type="text/javascript" src="js/bootstrap.min.js"></script>
    		<script type="text/javascript" src="js/dependencies/FileSaver.js"></script>
		    <script type="text/javascript">
		    var spinner;
		    var queryId;
		    var sequence;
		    var database;
		    var align;
		    var evalue;
		    var type;
		    var currentProcessId;
		    var interval = 1000;
		    var projIDs = new Array();
		    var sampleDetails = new Array(), fieldNames = {};
		    var blasterJsInstance;
		
		    $(document).ready(function () {
		    	currentProcessId = "BlastResult_${pageContext.session.id}_" + new Date().getTime();

				$.ajax({
				    url: "<c:url value="<%=MetaXplorController.BLASTED_PROJECTS_URL%>" />",
				    async: false,
				    method: "GET",
				    data: {
				        "module": "${param.module}",
				        "processId": "${param.processId}"
				    },
				    success: function (jsonResult) {
				    	for (var key in jsonResult) {
				    		$("#projectList").append("<option value=\"" + key + "\">" + jsonResult[key] + "</option>")
				    		projIDs.push(key);
				    	}
				    	$("#projectList").change();
				    	
					    $.ajax({
					        url: "<c:url value="<%=MetaXplorController.SEARCHABLE_FIELD_LIST_URL%>" />",
					        method: "GET",
					        data: {
					            "module": "${param.module}",
					            "projects": projIDs.join(";")
					        },
					        success: function (fields) {
					        	for (var key in fields)
					        		if (fields[key][1] == "<%=Sample.TYPE_ALIAS%>")
					        			fieldNames[fields[key][0]] = fields[key][3];
					        }
					    });
				    },
				    error: function (xhr, ajaxOptions, thrownError) {
				        handleJsonError(xhr, ajaxOptions, thrownError);
				    }
				});
		    });
		
		    function loadCurrentProjectResults() {
        		$("div#blast-alignments-table").html("");
        		$("div#blast-single-alignment").html("");
		        spinner = new Spinner({color: '#fff'});
		        loading(true);
		        $.ajax({
		            url: "<c:url value='<%= MetaXplorController.BLAST_RESULT_BY_JOBID_URL%>' />",
		            contentType: "application/json",
	                data: {"module": "${param.module}", "project": $("#projectList").val(), "processId": "${param.processId}", "paramOnly": false},
		            success: function (result) {
		                type = result.parameters.type;
		                sequence = result.parameters.sequence;
		                database = result.parameters.db;
		                align = result.parameters.align;
		                evalue = result.parameters.expect;
		                document.getElementById('title').innerHTML = type.replace("<%=MetaXplorController.DIAMOND_PREFIX%>", "Diamond - ").replace("blast", "BLAST") + " results</div>";
		                if (sequence.split(">").length == 2) {
			                queryId = sequence.substring(1, sequence.indexOf("\n"));
			                document.getElementById('queryId').innerHTML = "<b>queryId:</b> " + queryId;
		                }
		                document.getElementById('evalue').innerHTML = "<b>e-value:</b> " + evalue;
		                document.getElementById('nbalign').innerHTML = "<b>align:</b> " + align;
		                document.getElementById('queryId').title = sequence;
		                var multiple = document.createElement('div');
		                multiple.id = "blast-multiple-alignments";
		                var table = document.createElement('div');
		                table.id = "blast-alignments-table";
		                var single = document.createElement('div');
		                single.id = "blast-single-alignment";
		                rootDiv = document.getElementById('content');
		                rootDiv.appendChild(document.createElement('br'));
		                rootDiv.appendChild(document.createElement('br'));
		                rootDiv.appendChild(multiple);
		                rootDiv.appendChild(table);
		                rootDiv.appendChild(single);
		                
		                var blasterjs=require("biojs-vis-blasterjs");
		                blasterJsInstance = new blasterjs({
		                    string: result.blastOutput.replace(/ unnamed protein product/g, ""),
		                    multipleAlignments: "blast-multiple-alignments",
		                    alignmentsTable: "blast-alignments-table",
		                    singleAlignment: "blast-single-alignment",
		                    samples: result.samples,
		                    prot: (type === 'blastp' || type === 'blastx' || type === '<%=MetaXplorController.DIAMOND_PREFIX%>blastp' || type === '<%=MetaXplorController.DIAMOND_PREFIX%>blastx') ? true : false
		                });
		                
	                	setTimeout(function () {
			                $("#alignments-container div div a").on("click", function () {setTimeout("scrollBy(0, -200);", 10);});	  
						    insertSampleInfo();
	                	}, 100);
		                loading(false);
		            },
		            error: function (xhr, ajaxOptions, thrownError) {
                        handleError(xhr);
                        spinner.stop();
		            }
		        });
		    }
		    
		    function insertSampleInfo() {
        		let resultRows = $("div#blast-alignments-table tbody tr");
        		if (resultRows.length > 0) {
        			$("button#downloadButton").removeAttr("disabled")
        			$("div#blast-alignments-table th:eq(0)").after("<th>Sample(s)</th>");
            		resultRows.each(function () {
            			let firstCell = $(this).find("td:eq(0)");
            			let seqName = firstCell.text().trim();
            			if (type == 'blastp' || type == 'blastx' || type == '<%=MetaXplorController.DIAMOND_PREFIX%>blastp' || type == '<%=MetaXplorController.DIAMOND_PREFIX%>blastx')
            				seqName = seqName.substring(0, seqName.length - 2);
            			let sampleList = blasterJsInstance.opt.samples[seqName.trim()], csvSamples = "";
            			if (sampleList != null)
	            			for (var i=0; i<sampleList.length; i++)
	            				csvSamples += (i == 0 ? "" : ", ") + "<span data-toggle='tooltip' data-html='true' style='cursor:pointer;' onmouseover='showSampleDetails(this);'>" + sampleList[i] + "</span>";
            			firstCell.after("<td>" + csvSamples + "</td>");
    		        });
        		}
        		else
        			$("button#downloadButton").attr("disabled", "disabled");

                $("#alignments-container select").on("change", function () {
                	insertSampleInfo();
	            });  
		    }

		    function showSampleDetails(spanObj) {
		    	if ($(spanObj).attr('title') == null) {
		    		let sampleId = $(spanObj).text();
		    		if (sampleDetails[sampleId] == null)
		    			sampleDetails[sampleId] = getSampleDetailsTable(sampleId, "${param.module}", "<c:url value='<%= MetaXplorController.SAMPLE_DETAILS_URL%>' />", "<%=DBConstant.FIELDNAME_PROJECT%>", '<%=DBConstant.DATE_TYPE%>');
			    	$(spanObj).attr('title', sampleDetails[sampleId]);
			    	$(spanObj).tooltip('show');
		    	}
		    }
		    
		    function getFieldName(fieldId) {
		    	return fieldNames[fieldId].replace(/_/g, " ");
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

		    function downloadSequences() {
		    	let ids = new Array(); 
		    
		        $("div#blast-alignments-table button.alignment-table-description").each(function () {
		        	ids.push($(this).attr("id"));
		        });
		        
		        loading(true);		        
		        var isProt = type == 'blastp' || type == 'blastx' || type == '<%=MetaXplorController.DIAMOND_PREFIX%>blastp' || type == '<%=MetaXplorController.DIAMOND_PREFIX%>blastx';
		        if (isProt) {
		            var uniqId = [];
		            for (var id in ids) {
		                var correctId = ids[id].substring(0, ids[id].lastIndexOf('_'));
		                if (uniqId.indexOf(correctId) === -1) {
		                    uniqId.push(correctId);
		                }
		            }
		            ids = uniqId;
		        }
		        $.ajax({
		            url: "<c:url value="<%=MetaXplorController.BLAST_SUBJECT_SEQUENCE_EXPORT_URL%>" />",
                    method: 'POST',
		            dataType: 'binary',
		            data: {
		            	"module": "${param.module}",
		            	"project": $("#projectList").val().split("_")[0],
		                "processId": currentProcessId,
		                "seqIDs": ids.join(";")
		            },
		            success: function (data) {
		            	console.log(data);
		                var url = window.URL.createObjectURL(new Blob([data]));
		                var link = document.getElementById('file');
		                link.href = url;
		                link.download = "blast_${param.module}_" + ids.length + "_results.fasta.zip";
		                link.click();
		                setTimeout(function () {
		                    window.URL.revokeObjectURL(url);
		                }, 1000);
		                loading(false);
		            },
		            error: function () {
		                loading(false);
		            }
		        });
		        
		        setTimeout(checkProcessProgress, interval);
		  }
		    
          function checkProcessProgress() {
              $.ajax({
                  url: "<c:url value='<%= MetaXplorController.PROGRESS_INDICATOR_URL%>' />",
                    method: 'GET',
                    data: {"processId": currentProcessId},
                    success: function (jsonRes) {
                        if (jsonRes.complete !== true) {
                            if (jsonRes.error == null) {
                            	if (jsonRes.progressDescription != null)
                                	document.getElementById('pageSpinnerText').innerHTML = jsonRes.progressDescription
                                setTimeout(checkProcessProgress, interval);
                            } else {
                                document.getElementById('pageSpinner').style.display = 'none';
                                spinner.stop();
                                document.getElementById('pageSpinner').innerHTML = "Please wait...";
                                alert(jsonRes.error);
                            }
                        }
                    },
                    error: function () {
                        document.getElementById('pageSpinner').style.display = 'none';
                        spinner.stop();
                    }
                });
            }
		
		    function resubmit() {
		        var url = 'blast.jsp?module=${param.module}&processId=${param.processId}' + '&projects=' + projIDs;
		        var win = window.open(url, '_blank');
		        win.focus();
		    }
		
		
		    function loading(isLoading) {
		        var content = document.getElementById('pageSpinner');
		        if (isLoading) {
		            spinner.spin(content);
		            content.style.display = "flex";
		        } else {
		            spinner.stop();
		            content.style.display = "none";
		        }
		    }
		    </script>
        </c:if>
		<script type="text/javascript" src="js/commons.js"></script>
</body>
</html>
