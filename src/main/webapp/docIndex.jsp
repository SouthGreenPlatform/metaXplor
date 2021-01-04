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
<%@page language="java" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>
<fmt:setBundle basename="config" />
<!DOCTYPE html>
<html>
    <head>
        <title>Documentation</title>
        <link rel="shortcut icon" href="img/favicon.png" type="image/x-icon" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css">
        <link rel="stylesheet" type="text/css" href="css/main.css">
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript" src="js/bootstrap.min.js"></script>
    </head>
    <body style='padding:50px;'>
        <%@include file="navbar.jsp" %>
        <div id="readme" class="readme boxed-group clearfix announce instapaper_body md main-div margin-top-md">
            <div class="markdown-body main-div content">

                <h1 class="text-center">metaXplor v<%= appVersion %></h1>
                <h2>Index</h2>
                <ul>
                    <li><a href="#general-informations">General informations</a></li>
                    <li><a href="#import">Import</a></li>
                    <li><a href="#permission_rules">Permission rules</a></li>
                    <li><a href="#data_exploration">Data exploration</a></li>
                    <li><a href="#data_exports">Data exports</a></li>
                    <li><a href="#blasting_external_sequences">BLASTing external sequences against metaXplor contents</a></li>
                    <li><a href="#phylogenetic_assignment">Phylogenetic assignment</a></li>
                    <li><a href="#configuration_properties">Configuration properties</a></li>
                </ul>

                <h2 id="general-informations"> General informations</h2>

				<p><b>metaXplor</b> is a scalable, distributable, fully web-interfaced application for managing, sharing and exploring metagenomic data. Being based on a flexible NoSQL data model, it has very few constraints regarding dataset contents, and thus proves useful for handling outputs from both shot-gun and metabarcoding techniques. By supporting incremental data feeding and providing means to combine filters on all imported fields, it allows for exhaustive content browsing, as well as rapid narrowing to find very specific records. The application also features various interactive data visualization tools, ways to query contents by BLASTing external sequences, and an integrated pipeline to enrich assignments with phylogenetic placements.</p>
				<p><strong>Project homepage / source-code:</strong> <a href="https://github.com/SouthGreenPlatform/metaXplor" target="_blank"> https://github.com/SouthGreenPlatform/metaXplor</a></p>
                <p><strong>Current CIRAD instance:</strong> <a href="https://metaxplor.cirad.fr" target="_blank">https://metaxplor.cirad.fr</a></p>

                <h2 id="import"> Import</h2>

                <p> In order to import data into metaXplor, an account is <strong>required</strong>.
                    <fmt:message var="adminEmail" key="adminEmail" />
                    Data can be <strong>public</strong> (visible by anybody) or <strong>private</strong> (visible only by authorized users).
                    <c:if test='${!fn:startsWith(adminEmail, "??") && !empty adminEmail}'>
                        You can <b>request an account</b> from the <a href="login.jsp">login page</a>. 
                    </c:if>
                    Demonstration project data is <a href="data/mtx_sample_data.zip">available here</a>.
                </p>
                <h3>
                    <a id="user-content-import-archive-structure" class="anchor" href="#import-archive-structure" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>Import archive structure</h3>
                <p>The structure of the project archive should be as follows:</p>

				<pre>
					<code> 
					[optional directory]
					    |--[optional prefix]samples.tsv
					    |--[optional prefix]assignments.tsv
					    |--[optional prefix]sequences.tsv
					    |--[whatever].fasta </code>
				</pre>

				<p>You must upload project data as a .zip archive containing all 4 files.</p>


                <h3>
                    <a id="user-content-importtab-content" class="anchor" href="#importtab-content" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>Project meta-information</h3>

                <p>At the beginning of the upload procedure, a form describing the project has to be filled manually. Here are the available fields:</p>

                <table>
                    <thead>
                        <tr>
                            <th>Fields</th>
                            <th>Description</th>
                            <th>Required</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Project code</td>
                            <td>Project code. There can't be two projects with the same code</td>
                            <td><strong>yes</strong></td>
                        </tr>
                        <tr>
                            <td>Project name</td>
                            <td>Project name. There can't be two projects with the same name</td>
                            <td><strong>yes</strong></td>
                        </tr>
                        <tr>
                            <td>Project description</td>
                            <td>Short description of the data</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Contact information</td>
                            <td>How to contact data owner / manager (email / phone)</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Sequencing technology</td>
                            <td>(454, Illumina, PacBio, Sanger...)</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Author(s)</td>
                            <td>Name(s) of the person(s) who generated the data</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Max # accessions per assignment</td>
                            <td>How many accessions to take into account when several are provided for a single assignment</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Sequencing date</td>
                            <td>Date of sequencing</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Samples available</td>
                            <td>Are original samples still available (yes/no)</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Assembly method</td>
                            <td>(Abyss, CAP3, SPAdes, Velvet, ...)</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Publication reference(s)</td>
                            <td>References or links to publications associated with the data</td>
                            <td>no</td>
                        </tr>
                        <tr>
                            <td>Other informations</td>
                            <td>Additional information, comments ...</td>
                            <td>no</td>
                        </tr>
                    </tbody>
                </table>

                <h3>
                    <a id="user-content-samples-content" class="anchor" href="#samples-content" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>*samples.tsv content</h3>

                <p>files with the tsv extension are tab delimited flat text files with the fields described below (order of the fields is not important as long as field names are correct - the case does matter). Any non-required fields may contain an empty string or a dot “.”</p>
                <p>We <span style='color:red;'>strongly advise</span> to use, whenever possible, <span style='color:red;'>standard attribute names</span> such as defined in the <a href='https://www.ncbi.nlm.nih.gov/biosample/docs/attributes/' target='_blank'>BioSample database</a>.</p>

                <table>
                    <thead>
                        <tr>
                            <th>Fields</th>
                            <th>Description</th>
                            <th>Required</th>
                            <th>Example</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>sample_name</td>
                            <td>Code of the analysed sample</td>
                            <td><strong>yes</strong></td>
                            <td>C14_A88</td>
                        </tr>
                        <tr>
                            <td>lat_lon</td>
                            <td>Localisation of the host WGS 84 standard, formatted as <strong>latitude,longitude</strong>
                            </td>
                            <td><strong>Column must exist, cells may be empty</strong></td>
                            <td>43.5985,3.8794</td>
                        </tr>
                        <tr>
                            <td>collection_date</td>
                            <td>Collection date of the sample. Unix timestamp standard ISO 8601 as <strong>YYYY-mm-ddThh:mm:ss+00:00</strong>.  <strong>YYYY-mm-dd</strong> also accepted</td>
                            <td><strong>Column must exist, cells may be empty</strong></td>
                            <td>2016-12-07</td>
                        </tr>
                        <tr>
                            <td>Additional fields (unlimited) </td>
                            <td>Other informations</td>
                            <td>no</td>
                            <td>-</td>
                        </tr>
                    </tbody>
                </table>

                <h3>
                    <a id="user-content-sequence-content" class="anchor" href="#sequence-content" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>*sequences.tsv content</h3>

				<h5>all project sequences must appear here</h5>
                <p>files with the tsv extension are tab delimited flat text files with the fields described below (order of the fields is not important as long as field names are correct - the case does matter). Zero values may be omitted and replaced with an empty string (saves space!)”</p>

                <table>
                    <thead>
                        <tr>
                            <th>Fields</th>
                            <th>Description</th>
                            <th>Required</th>
                            <th>Example</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>qseqid</td>
                            <td>Sequence name. This field has to be unique</td>
                            <td><strong>yes</strong></td>
                            <td>Contig38_C1-4_A88_(24)</td>
                        </tr>
                        <tr>
                            <td><i>sample</i></td>
                            <td>Numeric value indicating how much each sample contributed to the sequence (may be a read depth for shotgun data, or a count of cluster sequences for metabarcoding)</td>
                            <td><strong>Column must exist for each sample, (not all) cells may be empty</strong></td>
                            <td>1</td>
                        </tr>
                </table>

                <h3>
                    <a id="user-content-sequence-content" class="anchor" href="#fasta-content" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>*.fasta content</h3>
				<h5>a standard fasta-format file is expected here, involving all sequences referred to in the *sequences.tsv file</h5>
				

                <h3>
                    <a id="user-content-assignment-content" class="anchor" href="#assignment-content" aria-hidden="true"><span aria-hidden="true" class="octicon octicon-link"></span></a>*assignments.tsv content</h3>

				<h5>only assigned sequences must appear here</h5>
                <p>files with the tsv extension are tab delimited flat text files with the fields described below (order of the fields is not important as long as field names are correct - the case does matter). Any non-required fields may contain an empty string or a dot “.”</p>
                <p>A bash script able to convert BLAST outputs to the expected format may be <a href="data/blastToMtx.sh">downloaded here</a>.</p>

                <table>
                    <thead>
                        <tr>
                            <th>Fields</th>
                            <th>Description</th>
                            <th>Required</th>
                            <th>Example</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>qseqid</td>
                            <td>Sequence name. This field may appear on multiple lines when working with several assignments per sequence</td>
                            <td><strong>yes</strong></td>
                            <td>Contig38_C1-4_A88_(24)</td>
                        </tr>
                        <tr>
                            <td>assignment_method</td>
                            <td>Method used for assigning sequence</td>
                            <td><strong>yes</strong></td>
                            <td>BlastX</td>
                        </tr>
                        <tr>
                            <td>sseqid</td>
                            <td>Subject accession(s), comma-separated if multiple (assignment taxon will be set to first common ancestor in this case)<br/><b><small><u>NB:</u> prefixing the accession number with "n:" (for nucleotide) or "p:" (for protein) is mandatory</small></b></td>
                            <td><strong>depends:</strong> required in the absence of taxonomy_id</td>
                            <td>p:AUW34315.1,p:WP_081775933.1</td>
                        </tr>
                        <tr>
                            <td>taxonomy_id</td>
                            <td>NCBI taxonomy ID<br/><b><small></td>
                            <td><strong>depends:</strong> required in the absence of sseqid</td>
                            <td>2045190</td>
                        </tr>
                        <tr>
                            <td>best_hit</td>
                            <td>Flag telling whether an assignment is considered the best hit for the current sequence AND assignment method. Any non-empty value other than a dot sets the flag to true.</td>
                            <td><strong>depends</strong>: not required if only one assignment is provided for the current sequence and method; required (and unique) otherwise</td>
                            <td>Y</td>
                        </tr>
                        <tr>
                            <td>Additional fields (unlimited)</td>
                            <td>Other informations</td>
                            <td>no</td>
                            <td>...</td>
                        </tr>
                    </tbody>
                </table>

                <p>Taxonomic information is retrieved from NCBI using the <a href="https://www.ncbi.nlm.nih.gov/books/NBK25501/">ENTREZ API</a> with values passed in the <strong>sseqid</strong> field.</p>
            
	          	<h2 id="permission_rules">Permission rules</h2>
	          	Read-access to projects is granted according to the following rules:
	          	<p>
				<table cellpadding="5">
					<tr style='background-color:#e8e8ff;'>
						<td></td>
						<td colspan='2' align="center"><b>Public database</b></td>
						<td colspan='2' align="center"><b>Private database</b></td>
					</tr>
	                <tbody>
					<tr>
						<td><b><br></b></td>
						<td><b>Public project</b></td>
						<td><b>Private project</b></td>
						<td><b>Public project</b></td>
						<td><b>Private project</b></td>
					</tr>
					<tr>
						<td><b>Anonymous user</b></td>
						<td>Y</td>
						<td>N</td>
						<td>N</td>
						<td>N</td>
					</tr>
					<tr>
						<td><b>Authenticated user without specific permissions</b></td>
						<td>Y</td>
						<td>N</td>
						<td>Y</td>
						<td>N</td>
					</tr>
					<tr>
						<td><b>Authenticated user with specific permissions</b></td>
						<td>Y</td>
						<td>Y</td>
						<td>Y</td>
						<td>Y</td>
					</tr>
					</tbody>
				</table>
				
                <h2 id="data_exploration">Data exploration</h2>
                
              	<p>All assigned sequences present in the system are searchable via the exploration interface, that allows to work simultaneously on any combination of projects from the selected database. Color codes are applied to sample-level, sequence-level and assignment-level fields for quick identification. This versatile interface provides means to combine filters on any of the fields added via project imports. Various kinds of advanced filtering widgets are thus proposed depending on the field’s data type.</p>
                <p>Search results can be browsed in four different ways. The default display is a sortable table with selectable fields supporting pagination, which can be configured to group results at the sample, sequence or assignment level. Table rows are clickable and lead to a dialog box with all the information related to the selected record. The other three displays, all interactive, allow browsing search results as a taxonomic tree, a Krona pie chart, and a zoomable geographic map showing sample collection locations.</p>
                <p>In the case when the user is working on projects containing multiple assignments per sequence along with multiple assignment methods, for consistency reasons, only one method is taken into account when building taxonomy trees or pies. Therefore a method needs to be selected to proceed, which is the reason for the assignment (and best-hit where applicable) filter(s) to be active by default in such cases. The user still has the ability to disable them but should expect result counts not to match between the table view and the taxonomy views.</p>
                
                <h2 id="data_exports">Data exports</h2>
                
              	<p>Once a dataset of interest has been selected, it may be downloaded in the same formats as supported for imports: a FASTA sequence file, and tab-delimited text files providing sample metadata, sequence composition or assignment information. Data may also be exported in the popular BIOM format, thus allowing easy manipulation of exported data in a variety of visualization or analysis tools such as Phinch or Calypso. Because this format enforces a precise and limited set of taxonomy ranks, sequence metadata are enriched with a full_taxonomy field that may include ranks beyond those defined in the BIOM format, e.g., several ranks associated with virus classification.</p>
                <p>Exports are automatically compressed into zip archives and may be either directed to the client computer for direct download, or temporarily materialized as physical files on the web server. In the latter case, a download URL is provided, making it easy to share with collaborators or feed into external systems. Indeed, next to the export button, a "sharing" icon provides means to configure "online output tools" that metaXplor will be able to push exported data to. As an example, this feature is compatible with Galaxy data sources and thus allows to transfer any exported file into a Galaxy history, by a simple button click. The metaXplor instance administrator may configure up to 5 default output tools, and each user may define his own custom output tool.</p>
                
                <h2 id="phylogenetic_assignment">Phylogenetic assignment</h2>

                <p>A "Phylogenetic assignment" link is available from the <span style='color:red;'>main menu</span>. Clicking it leads to a page offering the functionality to run this pipeline <span style='color:red;'>on user-provided sequences only</span>. <span style='text-decoration:underline;'>In order to run it on sequences identified in metaXplor, you must first export a FASTA file to the web server (see <a href="#data_exports">Data exports</a> section above)</span></p>
                <p>Having either provided his own FASTA file or created one by exporting metaXplor data to the web server, the user is then invited to select a reference package among those available with the system (de novo generated or obtained from paprica), or upload a custom reference package archive. A nucleotide sequence alignment is first applied using mafft v7.313 before pplacer v1.1.alpha19 proceeds with positioning exported sequences onto the existing reference tree. Then, guppy v1.1.alpha19 is used for sequence classification (classify option) and  to generate an XML version of the pplacer tree (fat option). Last, Archeopteryx.js is invoked to display an interactive solution for the end-user to investigate the results. After classification is performed, users with write permissions on any involved project have the facility to save newly found assignments to the database, thus enriching its contents for the benefit of all users.</p>

                <h2 id="blasting_external_sequences">BLASTing external sequences against metaXplor contents</h2>

                <p>Another section in the application provides means to search for similarities between an external set of sequences and those present in the system, the latter being used as a reference bank. Available algorithms are BLAST algorithm v2.6.0 and Diamond v2.0.4. Job results consist of a standard BLAST output file per selected target project, which may be investigated online in an interactive manner thanks to the BlasterJS library. Matching sequences may also be downloaded in FASTA format for further analyses (e.g. alignment, viral genome reconstruction).</p>
                <p>Several BLAST types are supported: BLASTx (comparison of a DNA query sequence, after having translated this DNA query sequence into the 6 possible frames, with a protein sequence database) with Diamond as a faster alternative, BLASTp (comparison of a protein query sequence with a protein sequence database) with Diamond as a faster alternative, BLASTn (comparison of a DNA query sequence to a DNA sequence database), tBLASTn (comparison of a protein query with a DNA database, in the 6 possible frames of the database), tBLASTx (comparison of the six-frame translations of a nucleotide query sequence with the six-frame translations of a nucleotide sequence database). This functionality was designed to provide means to quickly check whether newly obtained, locally held sequences share similarity with material already stored in previous projects.</p>
				
				<h2 id="configuration_properties">Configuration properties</h2>
	          	<p>
		          	WEB-INF/classes/config.properties file may be used to set values
		            for the following parameters:
	            </p>
		        <UL>
		            <LI class="margin-top-sm">
		                  <B>adminEmail</B> - You may specify via this property an email address
		                  for users to be able to contact the administrator, including for
		                  applying for account creation.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>sequenceLocation</B> - Place where web server stores fasta and fai files.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>blastDBLocation</B> - Place where cluster generates banks from sequences, also used by cluster for executing blasts (path is relative to the one defined in Opal scripts).
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>eutils_base_url</B> - Base URL of NCBI Entrez E-utilities
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>NCBI_api_key</B> - Providing a E-utilities api key allows querying web-service about 3x faster.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>NCBI_taxdump_zip_url</B> - URL of NCBI Taxonomy database dump zip.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>maxImportSize</B> - Defines the maximum allowed size (in megabytes) for project data file imports (capped by the maxUploadSize value set in applicationContext-MVC.xml). NB: Does not apply to administrators (administrators are only limited by maxUploadSize for uploads and are not limited when importing via webserver-local or http files).
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>maxRefPkgSize</B> - Defines the maximum allowed size (in megabytes) for zipped ref-package files.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>maxPhyloAssignFastaSeqCount</B> - Defines the default maximum number of sequences allowed for running phylogenetic assignment.
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>onlineOutputTool_N</B> - You may configure up to 5 external online output tools. The property value must consist in semi-colon-separated values. The first one is the label to display for this tool, the second one is the tool URL (in which any * character will be replaced at run time with the export file URL).
		            </LI>
		            <LI class="margin-top-sm">
		                  <B>enforcedWebapRootUrl</B> - In some situations the system needs to provide externally visible file URLs for remote applications to download. In most cases it is able to figure out which base URL to use, but it might also be impossible (for example when a proxy is used to add a https layer). This parameter may then be used to enforce a base-URL. Example values: https://my.secure.server.com/metaXplor or http://my.unsecure.metaXplor.server:8090
               		</LI>
		        </UL>
	          	<p>
		          	<b><span style='text-decoration:underline;'>NB:</span></b> webapp must be restarted for any change in config.properties to be taken into account
	            </p>
            </div>
        </div>
    	<script type="text/javascript" src="js/commons.js"></script>
    </body>
</html>
