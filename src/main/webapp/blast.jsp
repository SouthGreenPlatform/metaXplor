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
<%@page contentType="text/html" pageEncoding="UTF-8" import="fr.cirad.web.controller.metaxplor.MetaXplorController, fr.cirad.metaxplor.model.MetagenomicsProject"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
    <head>
        <title>metaXplor - BLAST</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-select.min.css">
        <link rel="stylesheet" type="text/css" href="css/main.css">
    </head>
    <body>
        <%@include file="navbar.jsp" %>
        <div id="pageSpinner">
        	<div id="pageSpinnerText">Please wait...</div>
		</div>
        <div class="container-fluid">
            <div class="col-md-1"></div>
            <div class="col-md-10">
                <div class="jumbotron">
                	<div class="row">
                		<div class="col-md-1"></div>
                        <div class="col-md-10" style='font-size:13px; margin-top:10px; margin-bottom:-10px;'>
                        	<h2 class="text-center">
                        		Run
                        		BLAST<a href='https://doi.org/10.1016/S0022-2836(05)80360-2' target='_blank' title="Altschul, Stephen F., Warren Gish, Webb Miller, Eugene W. Myers, and David J. Lipman (1990), Basic local alignment search tool. Mol. Biol. 215:403-10. https://doi.org/10.1016/S0022-2836(05)80360-2"><img src='img/books.png'/></a>
                        		or
                        		DIAMOND<a href='https://doi.org/10.1038/nmeth.3176' target='_blank' title="Buchfink B, Xie C, Huson DH. Fast and sensitive protein alignment using DIAMOND. Nat Methods. 2015 Jan;12(1):59-60. https://doi.org/10.1038/nmeth.3176"><img src='img/books.png'/></a>
                        		vs metaXplor contents
                        	</h2>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-md-2"></div>
                        <div class="col-md-8" style="margin-bottom:-5px;">
                            <div class="row margin-top-sm">
                                <div class="form-group">
                                	<div style='float:right; color:red;' id='warning'></div>
                                    <label for="textArea" id="textAreaLabel">Sequence(s)</label>
                                    <div class="form-input">
                                        <textarea class="full-width" id="textArea" title="Nucleotide or amino-acid sequence(s) depending on selected program type. Use FASTA format for multiple input." style="font-family:monospace; min-width:610px; height:187px; font-size:90%;" class="input-sm" onfocus="select();"></textarea>
                                    </div>
                                </div>
                            </div>
                            <div class="row margin-top-sm">
                                <div class="form-group col-md-6">
									<label for="dbList" id="dbLabel">Database to search in</label>
									<div class="form-input">
										<select id="dbList" class="selectpicker select-main" data-live-search="true" data-size="5"></select>
									</div>
								</div>
                                <div class="form-group col-md-6">
                                    <label for="project" id="projectLabel">DB project(s) to search in</label>
                                    <div class="form-input">
                                        <select class="selectpicker" multiple id="project" title="All projects" data-width="100%" data-live-search="true" name="variantTypes"></select>
                                    </div>
                                </div>  
                            </div>
                            <div class="row margin-top-sm">
                                <div class="form-group col-md-4">
                                    <label for="progType" id="progTypeLabel">Program type</label>
                                    <div class="form-input">
                                        <select id="progType" class="selectpicker" data-width="100%" onchange="typeChanged();">
                                    	    <option class='nucl' value="<%= MetaXplorController.DIAMOND_PREFIX%>blastx">blastx (diamond)</option>
                                            <option class='nucl'>blastx</option>
                                            <option class='prot' value="<%= MetaXplorController.DIAMOND_PREFIX%>blastp">blastp (diamond)</option>
                                            <option class='prot'>blastp</option>
                                            <option class='nucl'>blastn</option>
                                            <option class='prot'>tblastn</option>
                                            <option class='nucl'>tblastx</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="form-group col-md-4">
                                    <label for="expect" id="expectLabel">Expectation value</label>
                                    <div class="form-input">
                                        <input id="expect" type="text" value="1e-3" class="full-width">
                                    </div>
                                </div>
                                <div class="form-group col-md-4">
                                    <label for="align" id="alignLabel">Max # alignments</label>
                                    <div class="form-input">
                                        <input id="align" type="text" value="100" class="full-width">
                                    </div>
                                </div>
                            </div>
                            <div class="row margin-top-sm">
	                            <div class="col-md-4">
	                            	<label>Immutable parameters</label>
<pre style="font-size:11px; padding:5px; display:none;" id='blastParams'>-num_threads 2
-outfmt 0
</pre>
<pre style="font-size:11px; padding:5px;" id='diamondParams'>--num_threads 2
--outfmt 5
</pre>
	                            </div>
                            	<div class="col-md-4"><label>&nbsp;</label>
									<pre style="font-size:11px; padding:5px;" id="specificParams"></pre>
	                            </div>
                            	<div class="col-md-4 text-center">
	                            	<input type='checkbox' id='resultsInNewWindow' checked /> <label for="resultsInNewWindow" style="margin:-5px 20px 10px 0;"> Display results<br/>in a new window</label>
	                                <button type="button" class="btn btn-primary" onclick="run();" id='launchButton'> Launch </button>
	                            </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
		<div style="display:none;" class='center' id="asyncProgressLink">
			<button class="btn btn-info btn-sm" onclick="window.open('ProgressWatch.jsp?processId=' + currentProcessId + '&successURL=' + escape('<c:url value='/blastResult.jsp' />?' + 'module=' + $('#dbList').val() + '&processId=' + currentProcessId));" title="This will open a separate page allowing to watch import progress at any time. Leaving the current page will not abort the import process.">Open async progress watch page</button>
		</div>
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript" src="js/spin.min.js"></script>
        <script src="js/bootstrap.min.js"></script>
        <script src="js/bootstrap-select.min.js"></script>
        <script type="text/javascript">
	        var spinner;
	        var currentProcessId;
	        var projects = [];
	        var blastResultIDs;
	        var protRegex = /[^(ACDEFGHIKLMNPQRSTVWYacdefghiklmnpqrstvwy)]/g;
	        var nuclRegex = /[^(CAGTUcagtu)]/g;
	        var blastParams = {	"diamond_blastp":"--matrix BLOSUM62\n", 
								"blastp":"-seg yes\n-matrix BLOSUM62\n-task blastp", 
								"diamond_blastx":"--strand both",
	        					"blastx":"-strand both",
	        					"blastn":"-dust yes\n-task blastn\n-strand both",
	        					"tblastn":"",
	        					"tblastx":"-strand both" };
	        
            $('#dbList').on('change', function () {
	            $.ajax({
	                url: "<c:url value='<%= MetaXplorController.MODULE_PROJECT_LIST_URL%>' />",
	                async: false,
	                type: 'GET',
	                data: {
	                    "module": $("#dbList").val(),
	                    "detailLevel": 1
	                },
	                success: function (jsonResult) {
	                    var options = '';
	                    for (var proj in jsonResult) {
	                        options += '<option value="' + jsonResult[proj]["_id"] + '"data-subtext="' + jsonResult[proj].<%=MetagenomicsProject.FIELDNAME_NAME%> + '">' + jsonResult[proj].<%=MetagenomicsProject.FIELDNAME_ACRONYM%> + '</option>';
	                        projects.push(jsonResult[proj]["<%=MetagenomicsProject.FIELDNAME_ACRONYM%>"]);
	                    }
	                    $('#project').html(options).selectpicker('refresh');
	
	                },
	                error: function (xhr, status, error) {
	                    var err = eval("(" + xhr.responseText + ")");
	                    alert(err.Message);
	                }
	            });
            });
	
	        $(document).ready(function () {
	            var vars = {};
	            window.location.href.replace(location.hash, '').replace(
                    /[?&]+([^=&]+)=?([^&]*)?/gi,
                    function (m, key, value) {
                        vars[key] = value !== undefined ? value : '';
                    }
	            );

	            var module = vars["module"]; // get module from url
	            if (module != null)	// sometimes a # appears at the end of the url so we remove it with regexp               
	            	module = module.replace(new RegExp('#([^\\s]*)', 'g'), '');
	            
	            typeChanged();

	            $.ajax({
	            	async: false,
	                url: "<c:url value="<%=MetaXplorController.MODULE_LIST_URL%>" />" + (module != null ? "?module=" + module : ""),
	                success: function (jsonResult) {
	                    if (jsonResult !== null) {
	                        //fillAvailableDb(jsonResult[0]);
	                        for (var key in jsonResult) {
	                            $("#dbList").append("<option" + ("${param.module}" == jsonResult[key] ? " selected" : "") + ">" + jsonResult[key] + "</option>");
	                        }
	                        $("#dbList").selectpicker('refresh');
	                        $("#dbList").change();
	                    }
	                }, error: function (xhr, ajaxOptions, thrownError) {
	                    var err = eval("(" + xhr.responseText + ")");
	                    alert(err.Message);
	                }
	            });

	            if (vars["processId"] != null)  {	// re-submitting: pre-fill widgets with previous values
	                $.ajax({
	                    url: "<c:url value='<%= MetaXplorController.BLAST_RESULT_BY_JOBID_URL%>' />",
	                    contentType: "application/json",
	                    data: {
	                    	"processId": vars["processId"],
	                        "paramOnly": true,
	                        "module": $("#dbList").val()},
	                    success: function (result) {
	                        $('#textArea').val(result.parameters.sequence);
	                        $('#progType').val(result.parameters.type);
	                        $('#progType').selectpicker('refresh');
	                        $('#align').val(result.parameters.align);
	                        $('#align').selectpicker('refresh');
	                        $('#expect').val(result.parameters.expect);
	                        $('#expect').selectpicker('refresh');
	                        $('#project').val(vars["projects"].split(','));
	                        $('#project').selectpicker('refresh');
	                    },
			            error: function (xhr, ajaxOptions, thrownError) {
			            	alert(xhr.responseText);
			            }
	                });
	            }
	        });

// 	        function checkSeq(dbType, seqData /* if null, takes the textarea contents */) {
// 	        	let warning = "";
// 	        	let regexp = eval("" + dbType + 'Regex');
// 	        	let splitInput = seqData == null ? $("#textArea").val().trim().split("\n") : seqData.trim().split("\n");
// 	        	if (splitInput.length && splitInput[0].trim() == "") {
// 	        		$("#warning").html("");
// 	        		return false;
// 	        	}

// 	        	let valid = true;
// 	        	let cleanInput = "";
// 	        	for (let i in splitInput) {
// 	        		let line = splitInput[i].trim();
// 	        		if (line[0] == ">" || !regexp.test(line))
// 	        			cleanInput += (i == 0 ? "" : "\n") + line;
// 	        		else {
// 	        			valid = false;
// 	        			cleanInput += (i == 0 ? "" : "\n") + line.replace(regexp, "");
// 	        		}
// 	        	}

// 	        	if (seqData == null && !valid) {
// 	        		$("#textArea").val(cleanInput);
// 	        		warning += "Invalid characters were removed!&nbsp;";
// 	        	}
//             	if (seqData == null && regexp == protRegex && checkSeq("nucl", cleanInput))
//             		warning += "&nbsp;Are you sure this is protein data?";

// 	        	$("#warning").html(warning);
// 	        	return valid;
// 	        }

	        function typeChanged() {
	        	let progType = $('#progType').val();
	        	if (progType.indexOf("<%= MetaXplorController.DIAMOND_PREFIX%>") == -1) {
	        		$('#diamondParams').hide();
	        		$('#blastParams').show();
	        	}
	        	else {
	        		$('#diamondParams').show();
	        		$('#blastParams').hide();
	        	}
	        	let params = blastParams[progType];
	        	$('#specificParams').text(params);
	        	if (params.trim() == "")
	        		$('#specificParams').hide();
	        	else
	        		$('#specificParams').show();
	        }

	        function run() {
	        	var sequence = $('#textArea').val().replace(/^\s+|\s+$/g, '');	        	
	        	let regexp = eval("" + $('#progType option:selected').attr('class') + 'Regex');	        	
	        	let cleanInput = "";
	        	let splitInput = sequence.trim().split("\n");

	        	for (let i in splitInput) {
	        		let line = splitInput[i].trim();
	        		if (line[0] == ">" || !regexp.test(line))
	        			cleanInput += (i == 0 ? "" : "\n") + line;
	        		else {
	        			valid = false;
	        			cleanInput += (i == 0 ? "" : "\n") + line.replace(regexp, "");
	        		}
	        	}

	        	if (cleanInput == "") {
	        		alert("Please specify a sequence!");
	        		return;
	        	}
	        	
	        	currentProcessId = $('#progType').val() + "_${pageContext.session.id}_" + new Date().getTime();
	        	
	        	if (!$("#resultsInNewWindow").is(':checked')) {
		            document.getElementById('pageSpinner').style.display = 'flex';
		            spinner = new Spinner({color: "#fff"}).spin(document.getElementById('pageSpinner'));
		            enable(true);
		            $('#asyncProgressLink').show();
	        	}

	            $.ajax({
	                url: "<c:url value='<%= MetaXplorController.SUBMIT_BLAST_URL%>' />",
	                type: "POST",
	                data: {
	                    "processId": currentProcessId,
	                    "module": $("#dbList").val(),
	                    "banks": getSelectedProjects(),
	                    "sequence": cleanInput,
	                    "program": $('#progType').val(),
	                    "expect": $('#expect').val(),
	                    "align": $('#align').val()
	                },
	                success: function (jsonResult) {
	                	blastResultIDs = jsonResult;
	                	if ($("#resultsInNewWindow").is(':checked'))
	                		window.open('ProgressWatch.jsp?processId=' + currentProcessId + '&successURL=' + escape('<c:url value='/blastResult.jsp' />?' + 'module=' + $('#dbList').val() + '&processId=' + currentProcessId));
	                	else
	                    	checkProcessProgress();
	                },
	                error: function (xhr, status, error) {
	                    spinner.stop();
	                    $('#asyncProgressLink').hide();
	                    var err = eval("(" + xhr.responseText + ")");
	                    alert(err.Message);
	                    enable(false);
	                }
	            });
	        }
	        
	        function checkProcessProgress() {
	            setTimeout(function () {
	                $.ajax({
	                    url: "<c:url value='<%= MetaXplorController.PROGRESS_INDICATOR_URL%>' />",
                        method: "GET",
                        data: {"processId": currentProcessId},
                        success: function (jsonRes) {
                            if (jsonRes != null) {
	                            if (jsonRes.complete !== true) {
	                                if (jsonRes.error == null) {
	                                    document.getElementById('pageSpinnerText').innerHTML = jsonRes.progressDescription;
	                                    checkProcessProgress(currentProcessId);
	                                } else {
	            	                	$('#asyncProgressLink').hide();
	                                    document.getElementById('pageSpinner').style.display = 'none';
	                                    spinner.stop();
	                                    alert(jsonRes.error);
	                                    enable(false);
	                                }
	                            } else {
	        	                	$('#asyncProgressLink').hide();
	                                document.getElementById('pageSpinner').style.display = 'none';
	                                location.href = 'blastResult.jsp?module=' + $("#dbList").val() + '&processId=' + currentProcessId;
	                            }
                            }
                        },
    	                error: function (xhr, status, error) {
    	                    var err = eval("(" + xhr.responseText + ")");
    	                    alert(err.Message);
                            document.getElementById('pageSpinner').style.display = 'none';
                            spinner.stop();
                        }
                    });
                }, 1000);
            }

            function getSelectedProjects() {
                var list = $('#project').val();
                if (list.length === 0) {
                    $("#project option").each(function () {
                        list.push($(this).val());
                    });
                }
                return list.join(";");
            }

            function enable(doEnable) {
                $('#textArea').prop('disabled', doEnable);
                $('#progType').prop('disabled', doEnable);
                $('#expect').prop('disabled', doEnable);
                $('#align').prop('disabled', doEnable);
            }
        </script>
        <script type="text/javascript" src="js/commons.js"></script>
    </body>
</html>

