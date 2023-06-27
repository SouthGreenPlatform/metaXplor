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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>metaXplor - Phylogenetic assignment</title>
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/bootstrap-select.min.css">
        <link type="text/css" rel="stylesheet" href="css/dropzone.css" />
        <link rel="stylesheet" type="text/css" href="css/main.css">
        <script type="text/javascript" src="js/dropzone.js"></script>
    </head>
    <body>
        <%@include file="navbar.jsp" %>
        <c:set var="req" value="${pageContext.request}" />
        <div id="pageSpinner">
        	<div id="pageSpinnerText">Please wait...</div>
        </div>
        <div class="container-fluid">
            <div class="col-md-1"></div>
            <div class="col-md-10">
                <div class="jumbotron margin-top-md">
                    <div class="row">
                        <h2 class="text-center">Phylogenetic assignment with Pplacer<a href='https://doi.org/10.1186/1471-2105-11-538' target='_blank' title="Matsen, F.A., Kodner, R.B. &amp; Armbrust, E., pplacer: linear time maximum-likelihood and Bayesian phylogenetic placement of sequences onto a fixed reference tree. BMC Bioinformatics 11, 538 (2010) doi:10.1186/1471-2105-11-538"><img src='img/books.png'/></a> &amp; Guppy</h2>
                        <form autocomplete="off" enctype="multipart/form-data;charset=UTF-8" class="dropzone margin-top-md" id="uploadDropzone" action="<c:url value='<%= MetaXplorController.PHYLO_ASSIGN_URL%>' />" method="post">
                        	<input type="hidden" id="processId" name="processId" />
                        	<input type="hidden" id="exportHash" name="exportHash" value="${param.exportHash}"/>
                        	<input id="module" name="module" type='hidden' value="${param.module}" />
                        	<c:choose>
								<c:when test="${param.exportHash ne null && param.module ne null}">
	                        	<div class="row" style="padding:15px;">
			                        <div class="col-md-1"></div>
			                        <div class="col-md-10">
			                            <div class="row margin-top-sm">
			                                <div class="form-group">
			                                    <h4>Fasta file to process</h4>
			                                    <div class="form-input">
			                                        <div id="fastaFile" style="cursor:pointer;" class="btn btn-default btn-sm"><a style="cursor:pointer;" href="<%=MetaXplorController.TMP_OUTPUT_FOLDER%>/${param.exportHash}/<%= MetaXplorController.EXPORT_FILENAME_FA%>.zip"></a></div>
			                                    </div>
			                                </div>
			                            </div>
			                        </div>
			                    </div>
			                    </c:when>
								<c:otherwise>
									<div class="row" style="padding-bottom:15px;">
				                        <div class="col-md-1"></div>
			                   	        <div class="col-md-10">
											<div style='color:red; font-size:12px;'>
											Having accessed this functionality directly from the menu, you are expected to provide your own fasta file.
											<br/><br/>In order to run phylogenetic placement on sequences hosted in this metaXplor instance, you first need to select up to <div style='display:inline;' class="maxFastaSeqCount"></div> sequences by exploring a database, then export them into fasta format by checking the "Create URL on server" box.
											</div>
										</div>
										<div class="col-md-1"></div>
									</div>
								</c:otherwise>
			                </c:choose>
	                        <div class="">
		                        <div class="col-md-1"></div>
	                   	        <div class="col-md-10">
		                            <div class="row">
		                                <div class="form-group">
			                   	        	<h4>Pplacer reference package</h4>
			                   	        	<div id="defaultRefPkgs"></div>
											<div class="dz-default dz-message margin-top-sm" style="height:110px;">
								                <c:choose>
													<c:when test="${param.exportHash eq null || param.module eq null}">
														Please drop your fasta (and optionally your own ref-package file) here
														<br/><br/>supported extensions: .fasta, .fas, .fa, .fna, .refpkg.zip
														<br/>max refpkg size: <div style='display:inline;' id="maxRefPkgSize"></div> Mb,
														max sequences in fasta: <div style='display:inline;' class="maxFastaSeqCount"></div>
													</c:when>
													<c:otherwise>
														You may drop your own ref-package file here
														<br/><br/>supported extension: .refpkg.zip
														<br/>max file size: <div style='display:inline;' id="maxRefPkgSize"></div> Mb
													</c:otherwise>
			    								</c:choose> 
			 								</div>
			 							</div>
			 						</div>
	                            </div>
                            </div>
	                        <div class="row">
		                        <div class="col-md-1"></div>
	                            <div class="col-md-5" id="dropZonePreviews"></div>
	                            <div class="col-md-4">
	                            	<br/><span style='font-size:18px; font-weight:bold;'>MAFFT</span><a style='cursor:pointer;' href='https://doi.org/10.1093/molbev/mst010' target='_blank' title="Kazutaka Katoh, Daron M. Standley, MAFFT Multiple Sequence Alignment Software Version 7: Improvements in Performance and Usability, Molecular Biology and Evolution, Volume 30, Issue 4, April 2013, Pages 772–780, https://doi.org/10.1093/molbev/mst010"><img src='img/books.png'/></a> alignment option
	                            	<select class="selectpicker btn" data-width="155px" name=mafftOption>
	                            		<option>addfragments</option>
	                            		<option>addlong</option>
	                            		<option>add</option>
	                            	</select>
	                            </div>
	                            <div class="col-md-2 margin-top-sm text-left">
	                                <button type="button" class="margin-top-sm btn-sm btn btn-primary" onclick="submitForm();">Launch</button>
	                            </div>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
		<div style="display:none;" class='center' id="asyncProgressLink">
			<button class="btn btn-info btn-sm" onclick="window.open('ProgressWatch.jsp?processId=' + $('#processId').val() + '&successURL=' + escape('<c:url value='/pplacerResult.jsp' />?' + 'module=' + $('#module').val() + '&processId=' + $('#processId').val()));" title="This will open a separate page allowing to watch import progress at any time. Leaving the current page will not abort the import process.">Open async progress watch page</button>
		</div>
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript" src="js/spin.min.js"></script>
        <script type="text/javascript" src="js/jquery.ui.widget.min.js"></script>
        <script type="text/javascript" src="js/jquery-file-upload.min.js"></script>
        <script type="text/javascript" src="js/jquery.iframe-transport.min.js"></script>
        <script src="js/bootstrap.min.js"></script>
        <script src="js/bootstrap-select.min.js"></script>
        <script type="text/javascript">
        var uploadDropzone = new Dropzone("#uploadDropzone");
        var currentProgressCheckProcess;
        var resultWindowLaunched = false;
        var maxRefPkgSizeInMb, maxPhyloAssignFastaSeqCount = 0;
        
        $("#fastaFile a").html(window.location.origin + window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/") + 1) + $("#fastaFile a").attr("href"));
        
		$.ajax({
	      url: '<c:url value="<%=MetaXplorController.MAX_REFPKG_SIZE_PATH%>" />',
	      async: false,
	      type: "GET",
	      contentType: "application/json;charset=utf-8",
	      success: function(maxRefPkgSize) {
	    	maxRefPkgSizeInMb = parseFloat(maxRefPkgSize);
	      },
	      error: function(xhr, thrownError) {
	          handleError(xhr);
	      }
		});
		
		<c:if test="${param.exportHash eq null || param.module eq null}">
       	$.ajax({
           url: '<c:url value="<%=MetaXplorController.MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT%>" />',
           async: false,
           type: "GET",
           contentType: "application/json;charset=utf-8",
           success: function(maxFastaSeqCount) {
	          maxPhyloAssignFastaSeqCount = parseInt(maxFastaSeqCount);
           },
           error: function(xhr, thrownError) {
               handleError(xhr);
           }
        });
       	</c:if>

		$("div.maxFastaSeqCount").html(maxPhyloAssignFastaSeqCount);
		$("div#maxRefPkgSize").html(maxRefPkgSizeInMb);

        Dropzone.forElement("#uploadDropzone").options.autoProcessQueue = false;
        Dropzone.forElement("#uploadDropzone").options.uploadMultiple = true;
        Dropzone.forElement("#uploadDropzone").options.previewTemplate = "<div class=\"dz-preview dz-file-preview\">\n <div class=\"dz-details\">\n  <div class=\"dz-filename\"><span data-dz-name></span></div>\n  <div class=\"dz-size\"><span data-dz-size></span></div>\n  <a style=\"float:right;\" class=\"dz-remove\" href=\"javascript:undefined;\" data-dz-remove>Remove file</a>\n  </div>\n  <div class=\"dz-progress\"><span class=\"dz-upload\" data-dz-uploadprogress></span></div>\n  <div class=\"dz-error-message\"><span data-dz-errormessage></span></div>\n  <div class=\"dz-success-mark\">\n  <svg width=\"54px\" height=\"54px\" viewBox=\"0 0 54 54\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:sketch=\"http://www.bohemiancoding.com/sketch/ns\">\n   <title>Check</title>\n   <defs></defs>\n   <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\" sketch:type=\"MSPage\">\n    <path d=\"M23.5,31.8431458 L17.5852419,25.9283877 C16.0248253,24.3679711 13.4910294,24.366835 11.9289322,25.9289322 C10.3700136,27.4878508 10.3665912,30.0234455 11.9283877,31.5852419 L20.4147581,40.0716123 C20.5133999,40.1702541 20.6159315,40.2626649 20.7218615,40.3488435 C22.2835669,41.8725651 24.794234,41.8626202 26.3461564,40.3106978 L43.3106978,23.3461564 C44.8771021,21.7797521 44.8758057,19.2483887 43.3137085,17.6862915 C41.7547899,16.1273729 39.2176035,16.1255422 37.6538436,17.6893022 L23.5,31.8431458 Z M27,53 C41.3594035,53 53,41.3594035 53,27 C53,12.6405965 41.3594035,1 27,1 C12.6405965,1 1,12.6405965 1,27 C1,41.3594035 12.6405965,53 27,53 Z\" id=\"Oval-2\" stroke-opacity=\"0.198794158\" stroke=\"#747474\" fill-opacity=\"0.816519475\" fill=\"#FFFFFF\" sketch:type=\"MSShapeGroup\"></path>\n   </g>\n  </svg>\n  </div>\n  <div class=\"dz-error-mark\">\n  <svg width=\"54px\" height=\"54px\" viewBox=\"0 0 54 54\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:sketch=\"http://www.bohemiancoding.com/sketch/ns\">\n   <title>Error</title>\n   <defs></defs>\n   <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\" sketch:type=\"MSPage\">\n    <g id=\"Check-+-Oval-2\" sketch:type=\"MSLayerGroup\" stroke=\"#747474\" stroke-opacity=\"0.198794158\" fill=\"#ff9999\" fill-opacity=\"0.816519475\">\n     <path d=\"M32.6568542,29 L38.3106978,23.3461564 C39.8771021,21.7797521 39.8758057,19.2483887 38.3137085,17.6862915 C36.7547899,16.1273729 34.2176035,16.1255422 32.6538436,17.6893022 L27,23.3431458 L21.3461564,17.6893022 C19.7823965,16.1255422 17.2452101,16.1273729 15.6862915,17.6862915 C14.1241943,19.2483887 14.1228979,21.7797521 15.6893022,23.3461564 L21.3431458,29 L15.6893022,34.6538436 C14.1228979,36.2202479 14.1241943,38.7516113 15.6862915,40.3137085 C17.2452101,41.8726271 19.7823965,41.8744578 21.3461564,40.3106978 L27,34.6568542 L32.6538436,40.3106978 C34.2176035,41.8744578 36.7547899,41.8726271 38.3137085,40.3137085 C39.8758057,38.7516113 39.8771021,36.2202479 38.3106978,34.6538436 L32.6568542,29 Z M27,53 C41.3594035,53 53,41.3594035 53,27 C53,12.6405965 41.3594035,1 27,1 C12.6405965,1 1,12.6405965 1,27 C1,41.3594035 12.6405965,53 27,53 Z\" id=\"Oval-2\" sketch:type=\"MSShapeGroup\"></path>\n    </g>\n   </g>\n  </svg>\n </div>\n</div>";
        Dropzone.forElement("#uploadDropzone").options.previewsContainer = "#dropZonePreviews";
        Dropzone.forElement("#uploadDropzone").options.dictResponseError = 'Error uploading data';
        Dropzone.forElement("#uploadDropzone").options.acceptedFiles = "<c:if test="${param.exportHash eq null || param.module eq null}">.fasta,.fas,.fa,.fna,</c:if>.refpkg.zip";
        Dropzone.forElement("#uploadDropzone").options.maxFiles = <c:choose><c:when test="${param.exportHash eq null || param.module eq null}">2</c:when><c:otherwise>1</c:otherwise></c:choose>;

        function submitForm() {
            if (uploadDropzone.getRejectedFiles().length > 0) {
                alert("Please remove any rejected files before submitting!");
                return;
            }

            let selectedDefaultPackage = $("#refpkg").val() != "";
            let nRefPkgCount = (selectedDefaultPackage ? 1 : 0), nTotalFileCount = uploadDropzone.getQueuedFiles().length;
            
    		for (let index in uploadDropzone.getQueuedFiles())
    			if (uploadDropzone.getQueuedFiles()[index].name.endsWith(".refpkg.zip"))
    				nRefPkgCount++;

            if (nRefPkgCount != 1) {
            	alert("You must specify exactly one reference package file!");
            	return
            }
            <c:if test="${param.exportHash eq null || param.module eq null}">
            if (nTotalFileCount != (selectedDefaultPackage ? 0 : 1) + nRefPkgCount) {
            	alert("You must specify exactly one fasta file!");
            	return
            }
			</c:if>

        	document.getElementById('pageSpinner').style.display = 'flex';
            spinner = new Spinner({color: "#fff"}).spin(document.getElementById('pageSpinner'));
            
            $('#progress').data('error', false);
            $('#progress').modal({backdrop: 'static', keyboard: false, show: true});

           	$('#processId').val("phyloAssign_" + $("#module").val() + "_${pageContext.session.id}_" + new Date().getTime());
            
            if (uploadDropzone.getQueuedFiles().length > 0) {
        	  	document.getElementById('pageSpinnerText').innerHTML = "Uploading...";
				uploadDropzone.processQueue();
			}
			else
			{
				document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
				var blob = new Blob();
				blob.upload = { name:"nofiles" };
				uploadDropzone.uploadFile(blob);
			}

	        $('#asyncProgressLink').show();
            currentProgressCheckProcess = setInterval(checkProcessProgress, interval);
        }
		
		var interval = 1000;
        var spinner;
        var processId;
        var placementResultUrl;
        var defaultRefPkgDescs = new Array();

        $(document).ready(function () {
        	$("span#maxUploadSize").html(Dropzone.forElement("#uploadDropzone").options.maxFilesize);

	 		uploadDropzone.on("success", function (file, resultUrl) {
	 			placementResultUrl = resultUrl;
	 	    });
	 		uploadDropzone.on("error", function (file, resultUrl) {
				$('#asyncProgressLink').hide();
	 	    });
	 		uploadDropzone.on("addedfile", function(event) {
	            setTimeout(function() {
	            	if (!event.name.endsWith(".refpkg.zip"))
	            		return;
	            	$("#refpkg").val('');	            	
	            	$("#refpkg").change();
	            }, 1);            
	        });

	    	$.ajax({
 	    	    url: '<c:url value="<%=MetaXplorController.LIST_AVAILABLE_REF_PACKAGES_URL%>" />',
    	        async: false,
    	        success: function (jsonResult) {
    	        	var keys = Object.keys(jsonResult), selectHTML = "";
    	        	selectHTML += " <div class='row'><div class='col-md-5'><select name='refpkg' style='margin-bottom:100px;' id='refpkg' class='selectpicker' data-width='fit' data-size='10' onchange=\"refPkgChanged(this);\"><option value=''> --- Custom uploaded file --- </option>";
                    for (var i in keys) {
                    	defaultRefPkgDescs[keys[i]] = jsonResult[keys[i]];
                    	selectHTML += "<option>" + keys[i] + "</option>";
                    }
                    selectHTML += "</select>";
                    selectHTML += "<br/><button type='button' style='display:none; margin-top:10px; cursor:pointer; position:absolute;' class='btn-default btn-sm' id='kronaLink' onclick=\"window.open('<c:url value="<%=MetaXplorController.SHOW_REF_PACKAGE_KRONA_URL%>" />?refpkg=' + $('#refpkg option:selected').text());\">Click to view Krona pie <img src='private/img/magnifier.gif'></button>";
                    selectHTML += "</div><div style='height:120px;'><pre style='position:sticky; z-index:100; font-family:Menlo,Monaco,Consolas,monospace; width:65%; font-size:11px; display:none; border:1px solid grey; background-color:#eeeeff; margin-right:20px; height:135px; margin-top:-60px; overflow-y:auto; float:right;' id='regPkgDesc'></pre></div></div>";
                    $("#defaultRefPkgs").html(selectHTML);
                    $("#refpkg").selectpicker({
                        style: "btn-default btn-sm"
                    }).selectpicker('refresh');
                },
                error: function (xhr, ajaxOptions, thrownError) {
   	    	    	handleError(xhr);
                }
    	    });
        });
        
        function refPkgChanged(selectObj) {
        	if ( $(selectObj).val() != '') {
        		$('#regPkgDesc').html(defaultRefPkgDescs[$('#refpkg').val()].replace(/(?:\\r\\n|\\r|\\n)/g, '<br/>'));
        		$('#regPkgDesc').scrollTop(0);
        		for (let index in uploadDropzone.getQueuedFiles())
        			if (uploadDropzone.getQueuedFiles()[index].name.endsWith(".refpkg.zip"))
        				uploadDropzone.removeFile(uploadDropzone.getQueuedFiles()[index]);
        		$('#regPkgDesc').show();
        		$('#regPkgDesc').css('position', 'sticky');
        		$('#kronaLink').show();
        	}
        	else {
        		$('#regPkgDesc').hide();
        		$('#regPkgDesc').css('position', '');
        		$('#kronaLink').hide();
        	}
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
				        	$("div#pageSpinner").html("<button style='color:darkgreen;' onclick=\"window.open('pplacerResult.jsp?module=' + $('#module').val() + '&processId=' + $('#processId').val()); location.reload();\">Click to view result tree</button>");
					        $('#asyncProgressLink').hide();
		              	}
		              	else {
		                      if (jsonRes.error == null) {
		                      	if (jsonRes.progressDescription != null)
		                          	document.getElementById('pageSpinnerText').innerHTML = jsonRes.progressDescription;
		                      } else {
		                    	$('#asyncProgressLink').hide();
		                        document.getElementById('pageSpinner').style.display = 'none';
		                        spinner.stop();
		                        document.getElementById('pageSpinnerText').innerHTML = "Please wait...";
		                  		clearInterval(currentProgressCheckProcess);
		                  		currentProgressCheckProcess = null;
		                  		// re-add files to the queue if an error occured
		                        $.each(uploadDropzone.files, function(i, file) {
		                            file.status = Dropzone.QUEUED;
		                        });
		                        alert(jsonRes.error);
		                      }
		              	}
		              }
		          },
		          error: function (xhr, status, error) {
		            $('#asyncProgressLink').hide();
		      		clearInterval(currentProgressCheckProcess);
		      		currentProgressCheckProcess = null;
		      		handleError(xhr);
		          	document.getElementById('pageSpinner').style.display = 'none';
		          	spinner.stop();
		          }
	     	});
	 	 }

        function enable(doEnable) {
            $('#textArea').prop('disabled', doEnable);
            $('#package').prop('disabled', doEnable);
        }
        </script>
        <script type="text/javascript" src="js/commons.js"></script>
    </body>
</html>