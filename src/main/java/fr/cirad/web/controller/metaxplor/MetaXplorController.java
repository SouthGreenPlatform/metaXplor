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
package fr.cirad.web.controller.metaxplor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.biojava.bio.seq.DNATools;
import org.biojava.bio.symbol.SoftMaskedAlphabet;
import org.biojavax.bio.seq.RichSequence;
import org.biojavax.bio.seq.RichSequenceIterator;
import org.bson.Document;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import fr.cirad.metaxplor.importing.ImportArchiveChecker;
import fr.cirad.metaxplor.importing.MtxImport;
import fr.cirad.metaxplor.model.AssignedSequence;
import fr.cirad.metaxplor.model.Assignment;
import fr.cirad.metaxplor.model.AutoIncrementCounter;
import fr.cirad.metaxplor.model.Blast;
import fr.cirad.metaxplor.model.DBField;
import fr.cirad.metaxplor.model.MetagenomicsProject;
import fr.cirad.metaxplor.model.PhylogeneticAssignment;
import fr.cirad.metaxplor.model.Sample;
import fr.cirad.metaxplor.model.SampleReadCount;
import fr.cirad.metaxplor.model.Sequence;
import fr.cirad.metaxplor.model.Sequence.SequenceId;
import fr.cirad.metaxplor.model.Taxon;
import fr.cirad.metaxplor.model.TaxonomyNode;
import fr.cirad.security.ReloadableInMemoryDaoImpl;
import fr.cirad.security.base.IRoleDefinition;
import fr.cirad.tools.AppConfig;
import fr.cirad.tools.Constant;
import fr.cirad.tools.Helper;
import fr.cirad.tools.MetaXplorModuleManager;
import fr.cirad.tools.ProgressIndicator;
import fr.cirad.tools.mongo.DBConstant;
import fr.cirad.tools.mongo.MongoTemplateManager;
import fr.cirad.tools.opal.OpalServiceLauncher;
import fr.cirad.web.controller.BackOfficeController;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;

@Controller
public class MetaXplorController implements ApplicationContextAware {

    private static final Logger LOG = Logger.getLogger(MetaXplorController.class);
    
    @Autowired private AppConfig appConfig;

    @Autowired private MetaXplorModuleManager moduleManager;

    @Autowired private MtxImport mtxImport;
    
    @Autowired private ReloadableInMemoryDaoImpl userDao;
    
    @Autowired private CommonsMultipartResolver commonsMultipartResolver;

    @Autowired @Qualifier("authenticationManager")
    private AuthenticationManager authenticationManager;

	private int STATIC_LIST_FIELD_SIZE_LIMIT = 1000;
    
    static final private HashMap<String /*processId*/, URL/*import file URL*/> checkedImportFiles = new HashMap<>();

    /**
     * Unix path separator
     */
    public static final String PATH_SEPARATOR = "/";
    
    public static final String TMP_OUTPUT_FOLDER = "tmpOutput";
    public static final String REF_PKG_FOLDER = "refpkg";
    public static final String EXPORT_FILENAME_SP = "samples.tsv";
    public static final String EXPORT_FILENAME_FA = "sequences" + Sequence.FULL_FASTA_EXT;
    public static final String EXPORT_FILENAME_SQ = "sequences.tsv";
    public static final String EXPORT_FILENAME_AS = "assignments.tsv";
    public static final String EXPORT_FILENAME_BM = "export.biom";
    
    public static final String EXPORT_FORMAT_BIOM = "BM";

    public static final String DIAMOND_PREFIX = "diamond_";

    public static final String FRONTEND_URL = "metaXplor";
    public static final String TAXO_TREE_URL = PATH_SEPARATOR + FRONTEND_URL + "/taxoTree.json";
    public static final String MODULE_LIST_URL = PATH_SEPARATOR + FRONTEND_URL + "/moduleList.json";
    public static final String MODULE_PROJECT_LIST_URL = PATH_SEPARATOR + FRONTEND_URL + "/moduleProjects.json_";
    public static final String SEARCHABLE_FIELD_LIST_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchableFieldList.json";
    public static final String SEARCHABLE_FIELD_INFO_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchableFieldInfo.json";
    public static final String SEARCHABLE_LIST_FIELD_LOOKUP_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchableListFieldLookup.json";
    public static final String SEARCHABLE_TEXT_LOOKUP_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchableTextFieldLookup.json";
    public static final String CACHE_LOOKUP = PATH_SEPARATOR + FRONTEND_URL + "/cacheLookup.json";
    public static final String RECORD_COUNT_URL = PATH_SEPARATOR + FRONTEND_URL + "/countRecords.json";
    public static final String RECORD_SEARCH_URL = PATH_SEPARATOR + FRONTEND_URL + "/displaySearchResults.json";
    public static final String DISTINCT_SEQUENCES_URL = PATH_SEPARATOR + FRONTEND_URL + "/distinctSequences.json";
    public static final String GPS_POSITION_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchGpsPosition.json";
    public static final String SEQUENCE_DETAILS_URL = PATH_SEPARATOR + FRONTEND_URL + "/SequenceDetails.json";
    public static final String SAMPLE_DETAILS_URL = PATH_SEPARATOR + FRONTEND_URL + "/SampleDetails.json";
    public static final String SEARCH_INTERFACE_CLEANUP_URL = PATH_SEPARATOR + FRONTEND_URL + "/searchInterfaceCleanup.json";
    public static final String IMPORT_INTERFACE_CLEANUP_URL = PATH_SEPARATOR + FRONTEND_URL + "/importInterfaceCleanup.json";
    public static final String PROGRESS_INDICATOR_URL = PATH_SEPARATOR + FRONTEND_URL + "/progressIndicator.json";
    public static final String PROCESS_ABORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/processAbort.json";
    public static final String IMPORT_DATA_URL = PATH_SEPARATOR + FRONTEND_URL + "/import.do";
    public static final String SUBMIT_BLAST_URL = PATH_SEPARATOR + FRONTEND_URL + "/blast.do";
    public static final String BLAST_RESULT_BY_JOBID_URL = PATH_SEPARATOR + FRONTEND_URL + "/blastResults.json";
    public static final String BLASTED_PROJECTS_URL = PATH_SEPARATOR + FRONTEND_URL + "/blastedProjects.json_";
    public static final String IMPORT_CONTENT_CHECK_URL = PATH_SEPARATOR + FRONTEND_URL + "/checkImportContents.json";
    public static final String PHYLO_ASSIGN_URL = PATH_SEPARATOR + FRONTEND_URL + "/phyloAssignSubmit.do";
    public static final String GUPPY_FAT_RESULT_BY_JOBID_READER_URL = PATH_SEPARATOR + FRONTEND_URL + "/guppyFatResultByJobId.json";
    public static final String GUPPY_COUNT_UNSAVED_CLASSIFY_RESULT_BY_JOBID_READER_URL = PATH_SEPARATOR + FRONTEND_URL + "/guppyCountUnsavedClassifyResultByJobId.json";
    public static final String GUPPY_SAVE_CLASSIFY_RESULT_BY_JOBID_READER_URL = PATH_SEPARATOR + FRONTEND_URL + "/guppySaveClassifyResultByJobId.json";
    public static final String LIST_AVAILABLE_REF_PACKAGES_URL = PATH_SEPARATOR + FRONTEND_URL + "/listAvailRefPkg.json";
    public static final String SHOW_REF_PACKAGE_KRONA_URL = PATH_SEPARATOR + FRONTEND_URL + "/refPkgKrona.do";
    public static final String GET_STATS_URL = PATH_SEPARATOR + FRONTEND_URL + "/dbStats.json";
    public static final String SAMPLE_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportSamples.do";
    public static final String SEQUENCE_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportSequences.do";
    public static final String NUCL_SEQUENCE_URL = PATH_SEPARATOR + FRONTEND_URL + "/nucleotideSequence.do";
    public static final String PROJECT_FASTA_URL = PATH_SEPARATOR + FRONTEND_URL + "/projectFasta";
    public static final String BLAST_SUBJECT_SEQUENCE_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportBlastSubjectSequences.do";
    public static final String SEQ_COMPO_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportSequenceComposition.do";
    public static final String ASSIGNMENT_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportAssignments.do";
    public static final String BIOM_EXPORT_URL = PATH_SEPARATOR + FRONTEND_URL + "/exportBiom.do";
    public static final String REGISTER_NEW_USER_URL = PATH_SEPARATOR + FRONTEND_URL + "/register.do";
    public static final String CREATE_TEMP_VIEW_URL = PATH_SEPARATOR + FRONTEND_URL + "/doSearch.json";
    public static final String MAX_UPLOAD_SIZE_PATH = PATH_SEPARATOR + FRONTEND_URL + "/maxUploadSize.json";
    public static final String MAX_REFPKG_SIZE_PATH = PATH_SEPARATOR + FRONTEND_URL + "/maxRefPkgSize.json";
    public static final String MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT = PATH_SEPARATOR + FRONTEND_URL + "/maxPhyloAssignFastaSeqCount.json";
    public static final String ONLINE_OUTPUT_TOOLS_URL = PATH_SEPARATOR + FRONTEND_URL + "/onlineOutputTools.json";

    private static long lastInstanceStatsUpdate = 0;
    private static final Map<String, Integer> instanceStats = new LinkedHashMap<>();

    /**
     * Get number of databases, projects and sequences inserted in this instance
     *
     * @return
     * @throws InterruptedException 
     */
    @RequestMapping(GET_STATS_URL)
    @ResponseBody
    public Map<String, Integer> getInstanceStats() throws InterruptedException {
	    if (System.currentTimeMillis() - lastInstanceStatsUpdate > 1000*60*60 /*expires after an hour*/) {
	    	instanceStats.clear();
            ArrayList<Thread> threadsToWaitFor = new ArrayList<>();
            AtomicLong nDbCount = new AtomicLong(0), nTotalProjCount = new AtomicLong(0), nTotalAssignedSeqCount = new AtomicLong(0), nTotalUnAssignedSeqCount = new AtomicLong(0);
	        for (final String module : MongoTemplateManager.getAvailableModules()) {
	        	final MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
	        	Thread t = new Thread() {
	        		public void run() {
	    	        	int nDbProjCount = 0;
	    	        	nDbProjCount += mongoTemplate.count(new Query(), MetagenomicsProject.class);
	    	        	nTotalAssignedSeqCount.addAndGet(mongoTemplate.count(new Query(), AssignedSequence.class));
	    	
	    	    		if (nDbProjCount > 0) {	// only take into account databases that have non-empty projects
	    	    			nTotalProjCount.addAndGet(nDbProjCount);
	    	    			nDbCount.incrementAndGet();
	    	    		}

	    	    		nTotalUnAssignedSeqCount.addAndGet(mongoTemplate.count(new Query(), Sequence.class));		
	        		}
	        	};
	            threadsToWaitFor.add(t);
	        	t.start();
	        }
	        
	        for (Thread t : threadsToWaitFor) // wait for all threads before moving to next phase
	        	t.join();
           
			instanceStats.put("databases", nDbCount.intValue());
			instanceStats.put("projects", nTotalProjCount.intValue());
			instanceStats.put("assigned sequences", nTotalAssignedSeqCount.intValue());
			instanceStats.put("unassigned sequences", nTotalUnAssignedSeqCount.intValue());
			
			lastInstanceStatsUpdate = System.currentTimeMillis();
			LOG.debug("Instance stats updated");
	    }

		return instanceStats;
    }

    /**
     * List files in a folder. If names are correctly formatted, check file
     * content. Returns correct file along with "ok" if the file is correctly
     * formatter, or the error message
     *
     * @param
     * @return
     * @throws ClassNotFoundException 
     * @throws InterruptedException 
     * @throws IOException 
     */
    @RequestMapping(IMPORT_CONTENT_CHECK_URL)
    @ResponseBody
    public Map<String, Map<String, Object>> checkImportContents(HttpServletRequest request, HttpServletResponse resp,
    		@RequestParam(value = "processId") String processId,
            @RequestParam(value = "module") String module,
            @RequestParam(value = "code") String code,
            @RequestParam(value = "name") String name,
            @RequestParam(value = "description") String description,
            @RequestParam(value = "authors") String authors,
            @RequestParam(value = "adress") String adress,
            @RequestParam(value = "seqDate") String seqDate,
            @RequestParam(value = "seqTech") String seqTech,
            @RequestParam(value = "assemblTech") String assemblTech,
            @RequestParam(value = "maxAcc", defaultValue="1") int maxAcc,
            @RequestParam(value = "pub", required = false) String pub,
            @RequestParam(value = "avail", required = false) Boolean avail,
            @RequestParam(value = "extra", required = false) String extraInfo,
            @RequestParam(value = "access") boolean access,
            @RequestParam(value = "dataPath") String dataPath,
            @RequestParam(value = "file[0]", required = false) MultipartFile mpf,
            @RequestParam(value = "dontCheckAllEntries", required = false)  Boolean dontCheckAllEntries) throws ClassNotFoundException, InterruptedException, IOException {

        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Please wait"});
        ProgressIndicator.registerProgressIndicator(progress);
        String shortProcessId = processId.substring(1 + processId.indexOf('_'));

        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> checkedFiles = new HashMap<>(), sampleFields = new HashMap<>(), assignmentFields = new HashMap<>();
        Collection<String> sampleCodes = new ArrayList<>();
        result.put("files", checkedFiles);
        result.put("sample_fields", sampleFields);
        result.put("assignment_fields", assignmentFields);
        URL importArchiveURL = null;
    	boolean fGotInvalidFiles = false;
    	
		Long maxUploadSize = maxUploadSize(request, true), maxImportSize = maxUploadSize(request, false);
		try
		{
			if (mpf != null && !mpf.isEmpty()) {
				String fileExtension = FilenameUtils.getExtension(mpf.getOriginalFilename()).toLowerCase();
				File file;
				if (CommonsMultipartFile.class.isAssignableFrom(mpf.getClass()) && DiskFileItem.class.isAssignableFrom(((CommonsMultipartFile) mpf).getFileItem().getClass())) {
					// make sure we transfer it to a file in the same location so it is a move rather than a copy!
					File uploadedFile = ((DiskFileItem) ((CommonsMultipartFile) mpf).getFileItem()).getStoreLocation();
					file = new File(uploadedFile.getAbsolutePath() + "." + fileExtension);
				} else {
					file = File.createTempFile(null, "_" + mpf.getOriginalFilename());
					LOG.debug("Had to transfer MultipartFile to tmp directory for " + mpf.getOriginalFilename());
				}
				if (file.length() > maxUploadSize*1024*1024)
					progress.setError("Uploaded data is larger than your allowed maximum (" + maxUploadSize + " Mb)");
				else {
					mpf.transferTo(file);
					importArchiveURL = file.toURI().toURL();
				}
			}
			else {
				String sTrimmedDataPath = dataPath.trim();
				boolean fIsFtp = sTrimmedDataPath.startsWith("ftp://");
				boolean fIsRemote = fIsFtp || sTrimmedDataPath.startsWith("http://") || sTrimmedDataPath.startsWith("https://");						

				URL url = fIsRemote ? new URL(sTrimmedDataPath) : new File(sTrimmedDataPath).toURI().toURL();
				if (fIsRemote && !fIsFtp)   
				{
					HttpURLConnection httpConn = ((HttpURLConnection) url.openConnection());
					int respCode = httpConn.getResponseCode();
					if (HttpURLConnection.HTTP_OK != respCode) {
			    		progress.setError("Response code " + respCode + " on " + sTrimmedDataPath);
						build500Response(resp, progress.getError());
						return null;
					}
					
					Authentication auth = SecurityContextHolder.getContext().getAuthentication();
					boolean fAdminImporter = auth != null && auth.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
					if (!fAdminImporter)
					{
						Integer fileSize = null;
						try
						{
							fileSize = Integer.parseInt(httpConn.getHeaderField("Content-Length"));
						}
						catch (Exception ignored)
						{}
						if (fileSize == null)
							progress.setError("Only administrators may upload files with unspecified Content-Length");
						else if (fileSize > maxImportSize*1024*1024)
							progress.setError("Provided import data is larger than your allowed maximum (" + maxImportSize + " Mb)");
					}
				}
				importArchiveURL = url;
			}

			if (progress.getError() != null)
				return null;

			int nSeqCount = -1;
	    	Integer numberOfEntriesToCheck = Boolean.TRUE.equals(dontCheckAllEntries) ? 10000 : null;
	    	ZipEntry ze;

	    	ZipInputStream zis = new ZipInputStream(importArchiveURL.openStream());
	    	while ((ze = zis.getNextEntry()) != null)
	    	{
	    	   String fileName = ze.getName();    
               if (fileName.endsWith(Sequence.FULL_FASTA_EXT)) {
            	   try {
            		   nSeqCount = ImportArchiveChecker.testFastaFile(zis, numberOfEntriesToCheck, progress);
	                   checkedFiles.put(fileName, nSeqCount > 0 ? "ok" : "no sequence found in fasta");
	            	   if (nSeqCount <= 0)
	            		   fGotInvalidFiles = true;
            	   }
	           		catch (Exception e) {
	        			LOG.error("Invalid fasta file uploaded", e);
	        			checkedFiles.put(fileName, e.getMessage());
	        		}
            	   break; // handle fasta via a dedicated stream because biojava3 closes the stream after processing
               }
	    	}

	    	MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
	    	zis = new ZipInputStream(importArchiveURL.openStream());
	    	while ((ze = zis.getNextEntry()) != null)
	    	{
	    	   String fileName = ze.getName();    

               if (fileName.endsWith("samples.tsv")) {
            	   progress.addStep("Checking sample file");
            	   progress.moveToNextStep();
            	   String checkResultString = ImportArchiveChecker.testSampleFile(mongoTemplate, zis, sampleFields, sampleCodes);
                   checkedFiles.put(fileName, checkResultString);
            	   if (!"ok".equals(checkResultString))
            		   fGotInvalidFiles = true;
               }
               else if (fileName.endsWith("sequences.tsv")) {
            	   String checkResultString = ImportArchiveChecker.testSequenceFile(zis, numberOfEntriesToCheck, sampleCodes, progress, nSeqCount);
                   checkedFiles.put(fileName, checkResultString);
            	   if (!"ok".equals(checkResultString))
            		   fGotInvalidFiles = true;
               }
               else if (fileName.endsWith("assignments.tsv")) {
            	   String checkResultString = ImportArchiveChecker.testAssignmentFile(mongoTemplate, zis, numberOfEntriesToCheck, assignmentFields, progress);
                   checkedFiles.put(fileName, checkResultString);
            	   if (!"ok".equals(checkResultString))
            		   fGotInvalidFiles = true;
               }
	    	}
	    	zis.close();

	    	if (checkedFiles.size() != 4) {
	    		progress.setError("Wrong file contents. Expecting 4 files in archive: fasta file, .tsv sample file, .tsv sequence composition file, .tsv assignment file");
	    		//build500Response(resp, progress.getError());
	    		return null;
	    	}

	    	progress.markAsComplete();
	    	if (!fGotInvalidFiles)
	    		checkedImportFiles.put(shortProcessId, importArchiveURL);

	    	return result;
		}
		catch (Exception e)
		{
			progress.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
			throw e;
		}
		finally
		{
			if (!checkedImportFiles.containsKey(shortProcessId) && importArchiveURL != null && importArchiveURL.toString().startsWith("file:"))
				new File(importArchiveURL.getFile()).delete();	// checking somehow failed: let's not keep the file
		}
    }

    /**
     * Import data into metaXplor.
     *
     * @param request
     * @param processId
     * @param module
     * @param filePath
     * @param code
     * @param name
     * @param description
     * @param authors
     * @param adress
     * @param seqDate
     * @param seqTech
     * @param assemblTech
     * @param pub
     * @param avail
     * @param extraInfo
     * @param access
     * @return Map<String, String> containing import stats, and the error (errorMsg | null )
     * @throws IOException 
     */
    @RequestMapping(value = IMPORT_DATA_URL, method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> importData(HttpServletRequest request, HttpServletResponse resp,
            @RequestParam(value = "processId") String processId,
            @RequestParam(value = "module") String module,
            @RequestParam(value = "code") String code,
            @RequestParam(value = "name") String name,
            @RequestParam(value = "description") String description,
            @RequestParam(value = "authors") String authors,
            @RequestParam(value = "adress") String adress,
            @RequestParam(value = "seqDate") String seqDate,
            @RequestParam(value = "seqTech") String seqTech,
            @RequestParam(value = "assemblTech") String assemblTech,
            @RequestParam(value = "maxAcc", defaultValue="1") int maxAcc,
            @RequestParam(value = "pub", required = false) String pub,
            @RequestParam(value = "avail") boolean avail,
            @RequestParam(value = "extra", required = false) String extraInfo,
            @RequestParam(value = "access") boolean access) throws IOException {
    	
    	SecurityContext securityContext = SecurityContextHolder.getContext();
    	Authentication auth = securityContext.getAuthentication();
    	if (!moduleManager.canUserCreateProjectInDB(auth, module)) {
    		build401Response(resp);
    		return null;
    	}

    	final MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
    	
        URL importZipURL = checkedImportFiles.get(processId.substring(1 + processId.indexOf('_')));
        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Please wait"});
        ProgressIndicator.registerProgressIndicator(progress);

        int nProjectId = AutoIncrementCounter.getNextSequence(mongoTemplate, MongoTemplateManager.getMongoCollectionName(MetagenomicsProject.class));
    	try {
	        
	        if (code.trim().isEmpty())
	        	throw new Exception("No project code provided!");

	        Map<String, String> result = mtxImport.doImport(
	        		new OpalServiceLauncher(request),
	                module,
	                nProjectId,
	                code,
	                name,
	                description,
	                authors,
	                adress,
	                seqDate,
	                seqTech,
	                assemblTech,
	                avail,
	                maxAcc,
	                pub,
	                extraInfo,
	                importZipURL,
	                progress,
	                access);

	        // set the user as owner of this project ( -> allow him to give read/write permission to other users ) 
	        MetagenomicsProject savedProject = mongoTemplate.findOne(new Query(Criteria.where(MetagenomicsProject.FIELDNAME_ACRONYM).is(code)), MetagenomicsProject.class);
	        if (savedProject != null) {
	            String ownerName = request.getUserPrincipal().getName();
	            userDao.allowManagingEntity(module, "project", savedProject.getId(), ownerName);
	            userDao.reloadProperties();
	            securityContext.setAuthentication(authenticationManager.authenticate(auth));
	        }
	
	        return result;
    	}
    	catch (Exception ex) {
        	progress.setError(ex.getMessage());
            LOG.error("Import procedure failed", ex);
            try {
            	moduleManager.removeManagedEntity(module, MetaXplorModuleManager.ENTITY_PROJECT, nProjectId);
            }
            catch (Exception ignored) {}
        	return null;
        }
    	finally {
    		checkedImportFiles.remove(processId.substring(1 + processId.indexOf('§')));
    		
    		new Thread() {
    			public void run() {
    				moduleManager.cleanupOrphanSequences(module);
    			}
    		}.start();

			if (importZipURL.toString().startsWith("file:"))
				new File(importZipURL.getFile()).delete();	// checking somehow failed: let's not keep the file
    	}
    }

    /**
     * submit a pplacer job via opal and store the result in mongoDB
     *
     * @param request
     * @param sModule
     * @param processId job processId
     * @param fastaFileFolderName
     * @param refpkg refpkg file name if selected among provided
     * @param mafftOption
     * @param file[0] first uploaded file (fasta or own refpkg)
     * @param file[1] second uploaded file (fasta or own refpkg)
     * @throws Exception 
     */
    @RequestMapping(value = PHYLO_ASSIGN_URL)
    @ResponseBody
    public String phylogeneticAssignment(HttpServletRequest request, @RequestParam(value="module", required=false /* may not be provided if called on an external fasta*/) String sModule, @RequestParam String processId, @RequestParam(value="exportHash", required=false) String fastaFileFolderName, @RequestParam("refpkg") String refpkg, @RequestParam("mafftOption") String mafftOption, @RequestParam(value = "file[0]", required = false) MultipartFile mpf1, @RequestParam(value = "file[1]", required = false) MultipartFile mpf2) throws Exception {
        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Please wait"});
        ProgressIndicator.registerProgressIndicator(progress);

        String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
        if (fastaFileFolderName == null || fastaFileFolderName.isEmpty()) // we will need to create a tmp directory because the fasta has been uploaded by the user rather then exported from metaXplor
        	fastaFileFolderName = Helper.convertToMD5(processId);
        File tempWorkingFolder = new File(webappRootPath + File.separator + TMP_OUTPUT_FOLDER + File.separator + fastaFileFolderName);
        if (!tempWorkingFolder.exists() && !tempWorkingFolder.mkdirs())
            LOG.error("Could not find nor create folder '" + tempWorkingFolder + "'");
        
        File receivedRefPkgFile = null;
        File destinationRefPkgFile = new File(tempWorkingFolder + File.separator + "1.refpkg.zip");	// does not exist for now
        if (refpkg != null && !refpkg.isEmpty()) {
        	receivedRefPkgFile = new File(request.getRealPath(MetaXplorController.REF_PKG_FOLDER) + File.separator + refpkg);
        	FileUtils.copyFile(receivedRefPkgFile, destinationRefPkgFile);
        }
        else {
        	if (mpf2 != null && !mpf2.isEmpty() && mpf2.getOriginalFilename().endsWith(".refpkg.zip"))
        		mpf2.transferTo(destinationRefPkgFile);
        	else if (mpf1 != null && !mpf1.isEmpty() && mpf1.getOriginalFilename().endsWith(".refpkg.zip"))
        		mpf1.transferTo(destinationRefPkgFile);
        }
        boolean fGotRefPkg = destinationRefPkgFile.exists() && destinationRefPkgFile.length() > 0;

        ZipEntry ze;
		File zippedFastaFile = new File(webappRootPath + File.separator + TMP_OUTPUT_FOLDER + File.separator + fastaFileFolderName + File.separator + EXPORT_FILENAME_FA + ".zip");
		File destinationFastaFile = new File(webappRootPath + File.separator + TMP_OUTPUT_FOLDER + File.separator + fastaFileFolderName + File.separator + EXPORT_FILENAME_FA);	// does not exist for now
        if (zippedFastaFile.exists() & zippedFastaFile.length() > 0) {
            FileOutputStream fastaFileOS = null;
    		ZipInputStream zis = new ZipInputStream(new FileInputStream(zippedFastaFile));
    		ze = zis.getNextEntry();

        	while (ze!=null)
        	{
        		String fileName = ze.getName();
        		if (fileName.endsWith(Sequence.FULL_FASTA_EXT))
        		{
        			byte[] buffer = new byte[1024];
        			fastaFileOS = new FileOutputStream(destinationFastaFile);
                	int len;
    	            while ((len = zis.read(buffer)) > 0)
    	            	fastaFileOS.write(buffer, 0, len);
    	            fastaFileOS.close();
        		}
                ze = zis.getNextEntry();
         	}
        	if (fastaFileOS == null) {
    	    	progress.setError("No file found with extension '" + Sequence.FULL_FASTA_EXT + "' in " + zippedFastaFile.getName());
    	    	return null;
        	}
        	zis.closeEntry();
         	zis.close();
        }
        else {
        	if (mpf2 != null && !mpf2.isEmpty() && !mpf2.getOriginalFilename().endsWith(".refpkg.zip"))
        		mpf2.transferTo(destinationFastaFile);
        	else if (mpf1 != null && !mpf1.isEmpty() && !mpf1.getOriginalFilename().endsWith(".refpkg.zip"))
        		mpf1.transferTo(destinationFastaFile);
        }
        boolean fGotFasta = destinationFastaFile.exists() && destinationFastaFile.length() > 0;
   
        if (!fGotRefPkg || !fGotFasta) {
        	progress.setError("Some required files are missing or empty:" + (!fGotRefPkg ? " refpkg" : "") + (!fGotFasta ? " fasta" : ""));
        	return null;
        }

        progress.addStep("Counting sequences in fasta");
        progress.moveToNextStep();
        Long maxPhyloAssignFastaSeqCount = maxPhyloAssignFastaSeqCount(request);
		RichSequenceIterator iterator = RichSequence.IOTools.readFasta(new BufferedReader(new FileReader(destinationFastaFile)), SoftMaskedAlphabet.getInstance(DNATools.getDNA()).getTokenization("token"), null);
		int nSeqCount = 0;
		while (iterator.hasNext()) {
			iterator.nextSequence();
			nSeqCount++;
		}
        if (nSeqCount > maxPhyloAssignFastaSeqCount) {
        	progress.setError("Fasta contains too many sequences is too large (" + nSeqCount + "), maximum allowed is " + maxPhyloAssignFastaSeqCount);
        	return null;
        }

        Long maxRefPkgSize = maxRefPkgSize(request);
        if (destinationRefPkgFile.length() > (maxRefPkgSize * 1024 * 1024)) {
        	progress.setError("Refpkg file is too large, it may not exceed " + maxRefPkgSize + " Mb");
        	return null;
        }

    	String refFastaFileName = null;
    	File refFasta = null;
    	ZipInputStream zis = new ZipInputStream(new FileInputStream(destinationRefPkgFile));

    	// first pass: read CONTENTS.json
		while ((ze = zis.getNextEntry()) != null)
    	{
    	   String fileName = ze.getName();

           if (fileName.equals("CONTENTS.json") || fileName.endsWith(File.separator + "CONTENTS.json")) {
        	   JsonNode jsonNode = new ObjectMapper().readTree(zis).get("files");
        	   if (jsonNode != null)
        		   jsonNode = jsonNode.get("aln_fasta");
        	   if (jsonNode != null)
        		   refFastaFileName = jsonNode.textValue();
               break;
           }
    	}
    	zis.close();
    	if (refFastaFileName == null) {
    		progress.setError("Unable to find files.aln_fasta entry in refpkg's CONTENTS.json: cannot launch mafft");
    		LOG.error(progress.getError());
    		return null;
    	}
    	
    	// second pass: extract reference fasta
    	zis = new ZipInputStream(new FileInputStream(destinationRefPkgFile));
		while ((ze = zis.getNextEntry()) != null)
    	{
    	   String fileName = ze.getName();

           if (fileName.equals(refFastaFileName) || fileName.endsWith(File.separator + refFastaFileName)) {
        	   refFasta = new File(tempWorkingFolder + File.separator + "ref" + Sequence.FULL_FASTA_EXT);
        	   byte[] buffer = new byte[1024];
        	   FileOutputStream fos = new FileOutputStream(refFasta);
               int len;
               while ((len = zis.read(buffer)) > 0)
                   fos.write(buffer, 0, len);
               fos.close();
               break;
           }
    	}
    	zis.close();    	
    	if (refFasta == null) {
    		progress.setError("Unable to find fasta file '" + refFastaFileName + "' in refpkg: cannot launch mafft");
    		LOG.error(progress.getError());
    		return null;
    	}

        String assignmentQueryHash = Helper.convertToMD5(DigestUtils.md5Hex(new FileInputStream(destinationRefPkgFile)) + "\n" + mafftOption + "\n" + FileUtils.readFileToString(destinationFastaFile));	// this hash is an ID representing the job to run
        File phyloAssignFolder = new File(tempWorkingFolder.getAbsolutePath().replace(fastaFileFolderName, assignmentQueryHash));
        if (phyloAssignFolder.exists()) {
            LOG.debug("deleting " + phyloAssignFolder);
            FileUtils.deleteDirectory(phyloAssignFolder);
        }
        FileUtils.copyDirectory(tempWorkingFolder, phyloAssignFolder);
        
        refFasta.delete();
        
        String guppyFileUrl = new OpalServiceLauncher(request).phyloAssign(sModule, assignmentQueryHash, mafftOption, progress);

        LOG.debug("deleting " + phyloAssignFolder);
        FileUtils.deleteDirectory(phyloAssignFolder);

        return guppyFileUrl;
    }

    /**
     * count entries in guppy classify TSV result file
     *
     * @param module
     * @param jobId id of a job that lead to the wanted results
     * @return the number of assigned sequences
     * @throws IOException 
     */
    @RequestMapping(GUPPY_COUNT_UNSAVED_CLASSIFY_RESULT_BY_JOBID_READER_URL)
    @ResponseBody
    public int countUnsavedGuppyClassifyResultsByJobId(HttpServletResponse resp, @RequestParam(value = "module") String sModule, @RequestParam(value = "processId") String jobId) throws IOException {
    	MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);

    	PhylogeneticAssignment phyloAssign = mongoTemplate.findOne(new Query(Criteria.where(PhylogeneticAssignment.FIELDNAME_JOB_IDS).is(jobId)), PhylogeneticAssignment.class);
    	if (phyloAssign == null) {
    		build404Response(resp);
    		return 0;
    	}

    	if (phyloAssign.getPersistedAssignmentCount() > 0)
    		return 0;	// already saved

    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    	
    	HashMap<String, Boolean> projectWritePermissions = new HashMap<>();
    	
    	int nResult = 0;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(new URL(phyloAssign.getOutputUrl() + "/classification.tsv").openStream()))) {
            String line;
            while ((line = input.readLine()) != null) {
            	String[] splitLine = line.split("\t");
            	try {
            		Integer.parseInt(splitLine[1]);
            		int nLastPipePos = splitLine[0].lastIndexOf("|");
            		if (nLastPipePos == -1)
            			throw new Exception("Could not find project acronym suffix in sequence ID " + splitLine[0]);
            		String projId = splitLine[0].substring(nLastPipePos + 1);
            		Boolean fGotWritePermissionOnProject = projectWritePermissions.get(projId);
            		if (fGotWritePermissionOnProject == null) {
            			MetagenomicsProject project = mongoTemplate.findOne(new Query(Criteria.where(MetagenomicsProject.FIELDNAME_ACRONYM).is(projId)), MetagenomicsProject.class);
            			fGotWritePermissionOnProject = project == null ? false : moduleManager.canUserWriteToProject(auth, sModule, project.getId());
           				projectWritePermissions.put(projId, fGotWritePermissionOnProject);
            		}
            		if (fGotWritePermissionOnProject)
            			nResult++;
            	}
            	catch (NumberFormatException nfe) {
            		LOG.debug("Invalid taxid in guppy classify output: " + splitLine[1]);
            	}
            }
            input.close();
        } catch (Exception ex) {
            LOG.debug("failed: ", ex);
        }
        return nResult;
    }

    /**
     * save guppy classify results into database
     *
     * @param module
     * @param jobId id of a job that lead to the wanted results
     * @return the number of results
     * @throws Exception 
     */
    @RequestMapping(GUPPY_SAVE_CLASSIFY_RESULT_BY_JOBID_READER_URL)
    @ResponseBody
    public int saveGuppyClassifyResultsByJobId(HttpServletResponse resp, @RequestParam(value = "module") String sModule, @RequestParam(value = "processId") String jobId) throws Exception {
    	PhylogeneticAssignment phyloAssign = MongoTemplateManager.get(sModule).findOne(new Query(Criteria.where(PhylogeneticAssignment.FIELDNAME_JOB_IDS).is(jobId)), PhylogeneticAssignment.class);
    	if (phyloAssign == null) {
    		build404Response(resp);
    		return 0;
    	}

    	MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
    	Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    	
    	HashMap<String, Integer> projectAcronymToIdMap = new HashMap<>();
    	HashMap<Integer, Boolean> projectWritePermissions = new HashMap<>();
    	
    	int nResult = 0;
    	DBField assignmentMethodField = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + Assignment.FIELDNAME_ASSIGN_METHOD + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
    	if (assignmentMethodField == null)
    		throw new Exception("Unable to find assignment field: " + Assignment.FIELDNAME_ASSIGN_METHOD);

    	DBField likelihoodField = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + Assignment.FIELDNAME_LIKELIHOOD + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
    	if (likelihoodField == null) {
    		likelihoodField = new DBField(AutoIncrementCounter.getNextSequence(mongoTemplate, MongoTemplateManager.getMongoCollectionName(DBField.class)), AssignedSequence.FIELDNAME_ASSIGNMENT, Assignment.FIELDNAME_LIKELIHOOD, DBConstant.DOUBLE_TYPE);
    		mongoTemplate.save(likelihoodField);
    		LOG.info("Added assignment field: " + Assignment.FIELDNAME_LIKELIHOOD + " with ID " + likelihoodField.getId());
    	}
    	BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AssignedSequence.class);
        String line;
        
        try (BufferedReader input = new BufferedReader(new InputStreamReader(new URL(phyloAssign.getOutputUrl() + "/classification.tsv").openStream()))) {
            while ((line = input.readLine()) != null) {
            	String[] splitLine = line.split("\t");
            	try {
            		double taxid = Double.parseDouble(splitLine[1]);
            		int nLastPipePos = splitLine[0].lastIndexOf("|");
            		if (nLastPipePos == -1)
            			throw new Exception("Could not find project acronym suffix in sequence ID " + splitLine[0]);
        			String seq = splitLine[0].substring(0, nLastPipePos), pjAcronym = splitLine[0].substring(nLastPipePos + 1);
            		Boolean fGotWritePermissionOnProject = projectWritePermissions.get(pjAcronym);
            		if (fGotWritePermissionOnProject == null) {
            			MetagenomicsProject project = mongoTemplate.findOne(new Query(Criteria.where(MetagenomicsProject.FIELDNAME_ACRONYM).is(pjAcronym)), MetagenomicsProject.class);
            			fGotWritePermissionOnProject = project == null ? false : moduleManager.canUserWriteToProject(auth, sModule, project.getId());
            			projectAcronymToIdMap.put(pjAcronym, project.getId()); 
           				projectWritePermissions.put(project.getId(), fGotWritePermissionOnProject);
            		}
            		if (fGotWritePermissionOnProject) {
            			Assignment assignment = new Assignment();
        	        	if (assignmentMethodField != null)
        	        		assignment.addStringField(assignmentMethodField.getId(), "pplacer / guppy");   
        	        	assignment.putDoubleField(DBField.taxonFieldId,  taxid);
        	        	if (likelihoodField != null)
        	        		assignment.putDoubleField(likelihoodField.getId(), Double.parseDouble(splitLine[2]));
        	        	Update update = new Update().addToSet(AssignedSequence.FIELDNAME_ASSIGNMENT, assignment);
                    	bulkOperations.updateOne(new Query(new Criteria().andOperator(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(projectAcronymToIdMap.get(pjAcronym)), Criteria.where("_id." + Sequence.FIELDNAME_QSEQID).is(seq))), update);
                		nResult++;
            		}
            	}
            	catch (NumberFormatException nfe) {
            		LOG.debug("Invalid taxid in guppy classify output: " + splitLine[1]);
            	}
            }
            input.close();
            if (nResult > 0) {
            	nResult = bulkOperations.execute().getModifiedCount();
            	if (nResult > 0)	// add assignments
            	{
	            	// compute likelihood and assignment_method cache for all projects to which we were allowed to add assignments
	            	for (int projId : projectWritePermissions.keySet())
	            		if (projectWritePermissions.get(projId)) {
	                		MtxImport.computeFieldCache(mongoTemplate, projId, assignmentMethodField);
	                		MtxImport.computeFieldCache(mongoTemplate, projId, likelihoodField);
	                		likelihoodField.addProject(projId);	// also specify in which projects this field is being used
	            		}

	            	// save changes
	            	mongoTemplate.save(likelihoodField);
	            	phyloAssign.setPersistedAssignmentCount(nResult);
	            	mongoTemplate.save(phyloAssign);
            	}
            }
        } catch (Exception ex) {
            LOG.debug("failed: ", ex);
        }
        return nResult;
    }

    /**
     * get the guppy fat XML result file
     *
     * @param module
     * @param jobId id of a job that lead to the wanted results
     * @return the file as String (application/xml)
     * @throws IOException 
     */
    @RequestMapping(value = GUPPY_FAT_RESULT_BY_JOBID_READER_URL)
    @ResponseBody
    public String getGuppyFatResultsByJobId(HttpServletResponse resp, @RequestParam(value = "module") String sModule, @RequestParam(value = "processId") String jobId) throws IOException {
    	MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
    	if (mongoTemplate == null)
    		mongoTemplate = MongoTemplateManager.getCommonsTemplate();

    	PhylogeneticAssignment clusterPplacer = mongoTemplate.findOne(new Query(Criteria.where(PhylogeneticAssignment.FIELDNAME_JOB_IDS).is(jobId)), PhylogeneticAssignment.class);
    	if (clusterPplacer == null) {
    		build404Response(resp);
    		return null;
    	}
    	
        String result = null;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(new URL(clusterPplacer.getOutputUrl() + "/" + clusterPplacer.getId() + ".xml").openStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = input.readLine()) != null) {
                sb.append(line).append("\n");
            }
            input.close();
            result = sb.toString();
        } catch (Exception ex) {
            LOG.debug("failed: ", ex);
        }
        return result;
    }

    @RequestMapping(LIST_AVAILABLE_REF_PACKAGES_URL)
    @ResponseBody
    public Map<String, String> listAvailableRefPackages() {
    	Map<String, String> result = new LinkedHashMap<String, String>();
    	Query q = new Query();
    	q.fields().exclude(PhylogeneticAssignment.REF_PKG_KRONA_FIELD_NAME).exclude("_class");
    	q.with(Sort.by(Arrays.asList(new Order(Sort.Direction.ASC, "_id"))));
    	for (HashMap<String, String> aRefPkg : MongoTemplateManager.getCommonsTemplate().find(q, HashMap.class, PhylogeneticAssignment.REF_PKG_COLL_NAME))
    		result.put(aRefPkg.get("_id"), aRefPkg.get(PhylogeneticAssignment.REF_PKG_DESC_FIELD_NAME));
        return result;
    }

    @RequestMapping(SHOW_REF_PACKAGE_KRONA_URL)
    @ResponseBody
    public void showRefPackageKrona(HttpServletResponse response, @RequestParam("refpkg") String refpkg) throws IOException {
    	response.setContentType("text/html");
    	Map<String, String> result = new HashMap<String, String>();
    	Query q = new Query(Criteria.where("_id").is(refpkg));
    	q.fields().include(PhylogeneticAssignment.REF_PKG_KRONA_FIELD_NAME);
    	response.getWriter().write((String) MongoTemplateManager.getCommonsTemplate().findOne(q, HashMap.class, PhylogeneticAssignment.REF_PKG_COLL_NAME).get(PhylogeneticAssignment.REF_PKG_KRONA_FIELD_NAME));
    }

    /**
     * submit a blast on the cluster. Return the query hashes if successful
     *
     * @param request
     * @param sModule
     * @param processId
     * @param sequence
     * @param banks
     * @param program
     * @param expect
     * @param align
     * @return query hash = md5 checksum of parameters
     */
    @RequestMapping(value = SUBMIT_BLAST_URL, method = RequestMethod.POST)
    @ResponseBody
    public Collection<String> submitBlast(HttpServletRequest request, @RequestParam("module") String sModule, @RequestParam(value = "processId") String processId, @RequestParam(value = "banks") String banks, @RequestParam(value = "sequence") String sequence, @RequestParam(value = "program") String program, @RequestParam(value = "expect") String expect, @RequestParam(value = "align") String align) {
        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Submitting job(s)"});
        ProgressIndicator.registerProgressIndicator(progress);
        
        if (banks.isEmpty()){
			progress.setError("No project specified for blasting!");
			return null;
		}

        try {
	        OpalServiceLauncher osl = new OpalServiceLauncher(request);
        	return program.startsWith(DIAMOND_PREFIX) ? osl.diamond(sModule, banks, program.replaceFirst(DIAMOND_PREFIX, ""), sequence, expect, align, progress) : osl.blast(sModule, banks, program, sequence, expect, align, progress);
        } catch (IOException | NumberFormatException e) {
            progress.setError("Job submission failed: " + e.getMessage());
            LOG.debug("job failed: ", e);
        }
        return null;
    }

    /**
     * @param paramOnly if true, return only blast param to resubmit blast
     * @param module
     * @param project project ID
     * @param processId job ID
     * @return Map<String, Object>
     * @throws IOException 
     */
    @RequestMapping(value = BLAST_RESULT_BY_JOBID_URL, produces = "application/json")
    @ResponseBody
    public Map<String, Object> getBlastResult (
    		HttpServletResponse resp, 
            @RequestParam("paramOnly") boolean paramOnly,
            @RequestParam("module") String module,
            @RequestParam(value="project", required=false) Integer projId,
    		@RequestParam("processId") String jobId) throws IOException {
    	
    	long before = System.currentTimeMillis();
    	
        String result = null;
        Map<String, List<String>> seqToSampleMap = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        
        Criteria crit = new Criteria().andOperator(Criteria.where(PhylogeneticAssignment.FIELDNAME_JOB_IDS).is(jobId));
        if (projId != null)
        	crit.and(DBConstant.FIELDNAME_PROJECT).is(projId);
        Blast blast = mongoTemplate.findOne(new Query(crit), Blast.class);
    	if (blast == null) {
    		build404Response(resp);
    		return null;
    	}

    	URL resultUrl = new URL(blast.getResultUrl() + "/blast.out");
        if (!paramOnly) {
        	InputStream inputStream = resultUrl.openStream();
            boolean isProt = "blastx".equals(blast.getType()) || "blastp".equals(blast.getType()) || (DIAMOND_PREFIX + "blastx").equals(blast.getType()) || (DIAMOND_PREFIX + "blastp").equals(blast.getType());
            StringBuilder sb;
            ArrayList<String> seqIdList;
            try (BufferedReader input = new BufferedReader(new InputStreamReader(inputStream))) {
                sb = new StringBuilder();
                String line;
                seqIdList = new ArrayList<>();
                while ((line = input.readLine()) != null) {
                    sb.append(line).append("\n");
                    String id = null;
                    if (line.startsWith(">"))
                        id = line.substring(1, line.length() - 1).trim();
                    else if (line.contains("<Hit_id>"))
                        id = line.replaceAll("<[^>]+>", "").trim();
                    else
                    	continue;

                    if (!isProt) {
                        seqIdList.add(id);
                    } else {
                        seqIdList.add(id.substring(0, id.lastIndexOf('_')));
                    }
                }
            }
            result = sb.toString();
            if (!seqIdList.isEmpty()) {
                MongoCursor<Document> cursor = mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(AssignedSequence.class)).find(new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, new BasicDBObject("$in", seqIdList))).projection(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)).iterator();
                while (cursor.hasNext()) {
                	Document aggResult = cursor.next();
                    List<String> samples = new ArrayList<>();
                    for (Object sc : (List) aggResult.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION))
                    	samples.add((String) ((Document)sc).get(SampleReadCount.FIELDNAME_SAMPLE_CODE));
                    seqToSampleMap.put(((String) Helper.readPossiblyNestedField(aggResult, "_id." + Sequence.FIELDNAME_QSEQID, null)), samples);
                }
                if (seqToSampleMap.size() < seqIdList.size()) { // some sequences must be unassigned
                	cursor = mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(Sequence.class)).find(new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, new BasicDBObject("$in", seqIdList))).projection(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)).iterator();
                    while (cursor.hasNext()) {
                    	Document aggResult = cursor.next();
                        List<String> samples = new ArrayList<>();
                        for (Object sc : (List) aggResult.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION))
                        	samples.add((String) ((Document)sc).get(SampleReadCount.FIELDNAME_SAMPLE_CODE));
                        seqToSampleMap.put(((String) Helper.readPossiblyNestedField(aggResult, "_id." + Sequence.FIELDNAME_QSEQID, null)), samples);
                    }                    	
                }
            }
        }

        response.put("samples", seqToSampleMap);
        response.put("blastOutput", result);
        response.put("parameters", blast);
                
        LOG.debug("getBlastResult took " + (System.currentTimeMillis() - before) + "ms");
        
        return response;
    }
    
    /**
     * Returns project IDs and acronyms involved in a blast job
     *
     * @param module
     * @param processId job ID
     * @return
     */
    @RequestMapping(BLASTED_PROJECTS_URL)
    @ResponseBody
    public Map<Integer, String> getBlastedProjects(@RequestParam("module") String module, @RequestParam("processId") String jobId) {
    	Map<Integer, String> result = new TreeMap<>();
    	MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        List<Integer> projIDs = mongoTemplate.findDistinct(new Query(new Criteria().andOperator(Criteria.where(PhylogeneticAssignment.FIELDNAME_JOB_IDS).is(jobId))), DBConstant.FIELDNAME_PROJECT, Blast.class, Integer.class);
        Query q = new Query(Criteria.where("_id").in(projIDs)).with(Sort.by(Sort.Direction.ASC, "_id"));
        q.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
        for (MetagenomicsProject project :  MongoTemplateManager.get(module).find(q, MetagenomicsProject.class))
        	result.put(project.getId(), project.getAcronym());
        return result;
    }


    /**
     * Returns the list of available projects with their metaInfo
     *
     * @param module
     * @param detailLevel. 1 => name + acronym only, 2 => full details
     * @param projects id of projects to retrieve, joined with ';'
     * @return
     */
    @RequestMapping(MODULE_PROJECT_LIST_URL)
    @ResponseBody
    public List<Document> getModuleProjects(@RequestParam("module") String module, @RequestParam("detailLevel") int detailLevel, @RequestParam(value = "projects", required = false) String projects) {
    	if (module.isEmpty())
    		return new ArrayList<>();

        // include all info fields, but exclude pre-computed filter available values
    	BasicDBObject projection = new BasicDBObject(MetagenomicsProject.FIELDNAME_NAME, 1).append(MetagenomicsProject.FIELDNAME_ACRONYM, 1);
        if (detailLevel == 2) {
            projection.put(MetagenomicsProject.FIELDNAME_DESCRIPTION, 1);
            projection.put(MetagenomicsProject.FIELDNAME_META_INFO, 1);
            projection.put(MetagenomicsProject.FIELDNAME_AUTHORS, 1);
            projection.put(MetagenomicsProject.FIELDNAME_CONTACT_INFO, 1);
            projection.put(MetagenomicsProject.FIELDNAME_SEQUENCING_DATE, 1);
            projection.put(MetagenomicsProject.FIELDNAME_ASSEMBLY_METHOD, 1);
            projection.put(MetagenomicsProject.FIELDNAME_DATA_AVAIL, 1);
            projection.put(MetagenomicsProject.FIELDNAME_SEQUENCING_TECHNOLOGY, 1);
            projection.put(MetagenomicsProject.FIELDNAME_PUBLICATION, 1);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        List<Document> allProjects = mongoTemplate.getCollection(mongoTemplate.getCollectionName(MetagenomicsProject.class)).find().projection(projection).into(new ArrayList<Document>());
		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		if (fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN)))
			return allProjects;
		
        List<Document> allowedProjects = new ArrayList<>();
        for (Document proj : allProjects)
        	if (moduleManager.canUserReadProject(authentication, module, (int) proj.get("_id")))
        		allowedProjects.add(proj);
        return allowedProjects;
    }

    /**
     * get the list aof available modules
     *
     * @return Set< String >
     */
    @RequestMapping(MODULE_LIST_URL)
    @ResponseBody
    public Collection<String> getModuleList(HttpServletRequest request, @RequestParam(value="module", required=false) String selectedModule /* may have been passed via URL */) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	if ("true".equals(request.getHeader("Writable"))) 
    		return moduleManager.listWritableDBs(authentication);
    	else
    		return moduleManager.listReadableDBs(authentication, selectedModule);
    }

    /**
     * Return list of individuals from temp collection / full individuals
     * collection to display their GPS position
     *
     * @param module
     * @param processId
     * @return
     */
    @RequestMapping(GPS_POSITION_URL)
    @ResponseBody
    public List<Document> samplesPositions(@RequestParam("module") String module, @RequestParam(value="processId", required=false) String processId, @RequestParam(value="projects", required=false) String projectIds) {

        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        BasicDBObject match;

        if (processId != null) {
	        MongoCollection<Document> tmpView = mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));
	        
	        // get distinct samples from temp collection 
	        Collection<Object> sampleList = tmpView.aggregate(Arrays.asList(new BasicDBObject("$group", new BasicDBObject("_id", null).append(SampleReadCount.FIELDNAME_SAMPLE_CODE, new BasicDBObject("$addToSet", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE))))).allowDiskUse(true).batchSize(1000).first().getList(SampleReadCount.FIELDNAME_SAMPLE_CODE, Object.class); // we can't simply call distinct because it doesn't support allowDiskUse

	        // retrieve those samples but filter out individuals with null position
	        Set<String> samples = new HashSet<>(); 
	        sampleList.stream().forEach((sublist) -> {
	        	if (List.class.isAssignableFrom(sublist.getClass()))	// depending on MongoDB version we may get Strings or lists of Strings
	        		samples.addAll(((List) sublist));
	        	else
	        		samples.add(sublist.toString());
	        });
	
	        match = new BasicDBObject("_id", new BasicDBObject("$in", samples));
        }
        else {
        	String[] splitProjIDs = projectIds.split(";");
        	if (projectIds == null || "".equals(projectIds) || splitProjIDs.length == mongoTemplate.count(new Query(), MetagenomicsProject.class))
        		match = new BasicDBObject();	// no-filter
        	else
        		match = new BasicDBObject(DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", Arrays.asList(splitProjIDs).stream().mapToInt(Integer::parseInt).toArray()));
        }
        
        List<BasicDBObject> pipeline = Arrays.asList(new BasicDBObject("$match", match/*.append(DBConstant.GPS_TYPE + "." + DBField.gpsPosFieldId, new BasicDBObject("$exists", true))*/), new BasicDBObject("$project", new BasicDBObject(Sample.FIELDNAME_COLLECT_GPS, "$" + DBConstant.GPS_TYPE + "." + DBField.gpsPosFieldId)));
        List<Document> result = new ArrayList<>(); 
        MongoCursor<Document> cursor =  mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(Sample.class)).aggregate(pipeline).allowDiskUse(true).iterator();
        cursor.forEachRemaining((doc) -> {
            result.add(doc); 
        });
        return result; 
    }

    /**
     * get list of available filters
     *
     * @param module
     * @param projectIds project IDs joined with ";". This field may not be empty
     * @return Collection of Comparable arrays describing fields
     */
    @RequestMapping(SEARCHABLE_FIELD_LIST_URL)
    @ResponseBody
    public Collection<Comparable[]> searchableFieldList(@RequestParam("module") String module, @RequestParam("projects") String projectIds) {
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        Collection<Comparable[]> fieldList = new ArrayList<>();
        fieldList.add(new Comparable[] {DBField.qseqIdFieldId, AssignedSequence.TYPE_ALIAS, DBConstant.STRING_TYPE, Sequence.FIELDNAME_QSEQID});	// hack it into the list
        
        if (projectIds != null && !"".equals(projectIds)) {
	        int[] listProjectId = Arrays.asList(projectIds.split(";")).stream().mapToInt(Integer::parseInt).toArray();
	        BasicDBObject query = new BasicDBObject("$or", Arrays.asList(
        		new BasicDBObject("_id", new BasicDBObject("$in", DBField.getFieldsNotNeedingProjectReference().stream().map(dbf -> dbf.getId()).collect(Collectors.toList()))), // we don't bother creating a project list for fields with static IDs (they are always present)
        		new BasicDBObject(DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", listProjectId))));

	        MongoCursor<Document> cursor = mongoTemplate.getCollection(mongoTemplate.getCollectionName(DBField.class)).find(query).iterator();
	        cursor.forEachRemaining((doc) -> {
	            fieldList.add(new Comparable[] {(int) doc.get("_id"), (String) doc.get(DBField.FIELDNAME_ENTITY_TYPEALIAS), (String) doc.get(DBField.FIELDNAME_TYPE), (String) doc.get(DBField.FIELDNAME_NAME)});
	        });
        }
        return fieldList;
    }

    /**
     * Get bounds / available values for a field according to selected projects
     *
     * @param module
     * @param projectIds id's of projects joined with ";"
     * @param fieldName
     * @param type
     * @return
     * @throws Exception 
     */
    @RequestMapping(SEARCHABLE_FIELD_INFO_URL)
    @ResponseBody
    public List searchableFieldInfo(
            @RequestParam("module") String module,
            @RequestParam("projects") String projectIds,
            @RequestParam("field") int fieldId,
            @RequestParam("type") String type) throws Exception {
        List<Comparable> values = new ArrayList<>();
        if (projectIds == null || "".equals(projectIds) || type == null) {
            return values;
        }

        int[] listProjectId = Arrays.asList(projectIds.split(";"))
            .stream()
            .mapToInt(Integer::parseInt)
            .toArray();
        BasicDBObject query = new BasicDBObject((DBField.qseqIdFieldId == fieldId ? "_id." : "") + DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", listProjectId));
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        MongoCollection<Document> cacheCollection = mongoTemplate.getCollection(DBField.qseqIdFieldId == fieldId ? mongoTemplate.getCollectionName(AssignedSequence.class) : (DBConstant.CACHE_PREFIX + fieldId));

        switch (type) {
	        case DBConstant.GPS_TYPE:
	            throw new Exception("nyi");
            case DBConstant.STRING_TYPE:
            case DBConstant.STRING_ARRAY_TYPE:
            	if (cacheCollection.countDocuments(query) <= STATIC_LIST_FIELD_SIZE_LIMIT )
            		values = cacheCollection.distinct("_id" + (DBField.qseqIdFieldId == fieldId ? ("." + Sequence.FIELDNAME_QSEQID) : ""), query, String.class).into(new ArrayList<>());
                break;
            case DBConstant.DOUBLE_TYPE:
            case DBConstant.DATE_TYPE:
                Comparable min = Helper.getBound(cacheCollection, DBConstant.FIELDNAME_MIN, DBConstant.LOWER_BOUND, "_id", listProjectId);
                Comparable max = Helper.getBound(cacheCollection, DBConstant.FIELDNAME_MAX, DBConstant.UPPER_BOUND, "_id", listProjectId);
                values = Arrays.asList(min, max);
                break;
        }
        return values;
    }
    
    /**
     * Get bounds / available values for a field according to selected projects
     *
     * @param module
     * @param projectIds projectIds _id's of projects joined with ";". This
     * field has to be specified and must not be empty
     * @param fieldName
     * @param type
     * @return
     * @throws Exception 
     */
    @RequestMapping(SEARCHABLE_LIST_FIELD_LOOKUP_URL)
    @ResponseBody
    public List<Comparable> searchableListFieldLookup(
            @RequestParam("module") String module,
            @RequestParam("projects") String projectIds,
            @RequestParam("field") int fieldId,
            @RequestParam("q") String lookupText) throws Exception {
        List<Comparable> values = new ArrayList<>();
        if (projectIds == null || "".equals(projectIds) || lookupText == null) {
            return values;
        }

        int[] listProjectId = Arrays.asList(projectIds.split(";"))
            .stream()
            .mapToInt(Integer::parseInt)
            .toArray();
        BasicDBObject query = new BasicDBObject((DBField.qseqIdFieldId == fieldId ? "_id." : "") + DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", listProjectId));
        query.put("_id" + (DBField.qseqIdFieldId == fieldId ? ("." + Sequence.FIELDNAME_QSEQID) : ""), Pattern.compile(".*\\Q" + lookupText + "\\E.*", Pattern.CASE_INSENSITIVE));

        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        MongoCollection<Document> cacheCollection = mongoTemplate.getCollection(DBField.qseqIdFieldId == fieldId ? mongoTemplate.getCollectionName(AssignedSequence.class) : (DBConstant.CACHE_PREFIX + fieldId));
        values = cacheCollection.distinct("_id" + (DBField.qseqIdFieldId == fieldId ? ("." + Sequence.FIELDNAME_QSEQID) : ""), query, String.class).into(new ArrayList<>());
    	if (values.size() > STATIC_LIST_FIELD_SIZE_LIMIT)
    		return Arrays.asList("Too many results (" + values.size() + ") , please refine search!");

        return values;
    }

    /**
     * @param module
     * @param sProjects
     * @param processId
     * @return
     * @throws Exception 
     */
    @RequestMapping(TAXO_TREE_URL)
    @ResponseBody
    public TaxonomyNode taxonomyTree(@RequestParam("module") String module, @RequestParam(value = "projects", required = false) String sProjects, @RequestParam(required = false) String assignMethod, @RequestParam(value = "processId", required = false) String processId) throws Exception {
    	HashMap<Integer, Collection<SequenceId>> assignedSeqsByTaxon;
        Map<Integer, Integer> assignedSeqCountsByTaxon = new HashMap<>();

        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        boolean fFoundInCache = false;

        String sCacheId = sProjects + (assignMethod == null || assignMethod.trim().isEmpty() ? "" : ("_" + assignMethod));
        if ("".equals(assignMethod))
        	assignMethod = null;

		if (processId == null /* means we're showing the main taxo tree, not one based on search results */ && mongoTemplate.count(new Query(Criteria.where("_id").is(sCacheId)), Constant.TAXO_TREE_CACHE_COLLNAME) > 0)
        {	// try and get it from the cache
        	HashMap<Comparable, Object> cacheObject = mongoTemplate.findById(sCacheId, HashMap.class, Constant.TAXO_TREE_CACHE_COLLNAME);
        	cacheObject.remove("_id");

        	for (Comparable taxid : cacheObject.keySet())
        		assignedSeqCountsByTaxon.put(Integer.valueOf(taxid.toString()), (int) cacheObject.get(taxid));

        	Integer unidentifiedSeqCount = assignedSeqCountsByTaxon.get(Taxon.UNIDENTIFIED_ORGANISM_TAXID);
        	if (unidentifiedSeqCount != null && unidentifiedSeqCount > 0)
        		LOG.warn("In projects " + sProjects + ", " + unidentifiedSeqCount + " sequences have no tax id: did NCBI service calls fail at import time?");

        	fFoundInCache = true;
        }
        else
        {	// build it
	        assignedSeqsByTaxon = new HashMap<>();
	        List<BasicDBObject> pipeline = new ArrayList<>(), firstMatchAndList = new ArrayList<>(), secondMatchOrList = new ArrayList<>();
	        
	        if (assignMethod == null) {	// no need to distinguish by assignment method (simplest case)
	        	if (sProjects != null)
		        	pipeline.add(new BasicDBObject("$match", new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", Arrays.asList(sProjects.split(";")).stream().mapToInt(Integer::parseInt).toArray()))));
	        }
	        else {
		        // flatten records to have one per assignment
		        pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
	
		        if (assignMethod != null) {
		        	DBField assignmentMethodField = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + Assignment.FIELDNAME_ASSIGN_METHOD + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
		        	if (assignmentMethodField == null)
		        		throw new Exception("Unable to find assignment field: " + Assignment.FIELDNAME_ASSIGN_METHOD);
	
		        	firstMatchAndList.add(new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.STRING_TYPE + "." + assignmentMethodField.getId(), assignMethod));
		        }
		        
		        if (sProjects != null) {
		            // filter on projects to build selection tree
		            firstMatchAndList.add(new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", Arrays.asList(sProjects.split(";")).stream().mapToInt(Integer::parseInt).toArray())));
		        }
		        if (!firstMatchAndList.isEmpty())
		        	pipeline.add(new BasicDBObject("$match", new BasicDBObject("$and", firstMatchAndList)));
		        
		        // group again to keep only assignments with the wanted method, and compute for each sequence the number of assignments with this method
		        DBObject firstgroupFields = new BasicDBObject("_id", "$_id");
		        firstgroupFields.put(AssignedSequence.FIELDNAME_ASSIGNMENT, new BasicDBObject("$addToSet", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
		        firstgroupFields.put("n", new BasicDBObject("$sum", 1));
		        pipeline.add(new BasicDBObject("$group", firstgroupFields));
		        
		        // final filter will need to accept sequences with only one remaining assignment even if no best hit was specified
		        secondMatchOrList.add(new BasicDBObject("n", 1));
	        }

	        // flatten records to have one per assignment
	        pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));

	    	DBField bestHit = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + DBField.bestHitFieldName + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
	    	if (bestHit != null)	// final filter will need to accept best hits when several assignments exist for the same sequence (and possibly assignment method)
	    		secondMatchOrList.add(new BasicDBObject("$expr", new BasicDBObject("$ne", Arrays.asList(new BasicDBObject("$ifNull", Arrays.asList("$AS." + DBConstant.STRING_TYPE + "." + bestHit.getId(), "")), ""))));
	    	
	    	if (!secondMatchOrList.isEmpty())	// apply final filter to retain only relevant assignments
	    		pipeline.add(new BasicDBObject("$match", new BasicDBObject("$or", secondMatchOrList)));

	    	// group records by taxon
	        DBObject lastgroupFields = new BasicDBObject("_id", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId);
	        lastgroupFields.put("sq", new BasicDBObject("$addToSet", "$_id"));
	        pipeline.add(new BasicDBObject("$group", lastgroupFields));

	        MongoCollection<Document> collection = mongoTemplate.getCollection(processId == null ? MongoTemplateManager.getMongoCollectionName(AssignedSequence.class) : (MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId)));
	        MongoCursor<Document> cursor = collection.aggregate(pipeline).allowDiskUse(true).iterator();
	        Collection<String> accsMissingFromCache = new TreeSet<String>();
	        while (cursor.hasNext()) {
	        	Document record = cursor.next();
	            if (record != null) {
	            	Collection<SequenceId> seqs = ((Collection<SequenceId>) record.get("sq"));
	            	Object taxId = record.get("_id");
	            	if (taxId == null && sProjects != null /* checking if sProjects is null is a hack to determine whether we're being called from the interface or from another method such as findTaxaChildren */) {
	            		LOG.info("In project(s) [" + sProjects + "], trying to find missing tax id from cache for " + seqs.size() + " sequences...");
	            		int nSuccessfullyUpdated = 0;
	            		List<AssignedSequence> seqsToUpdate = mongoTemplate.find(new Query(Criteria.where("_id").in(seqs)), AssignedSequence.class);
	            		for (AssignedSequence as : seqsToUpdate) {
	            			boolean fSeqUpdated = false;
	            			for (Assignment assignment : as.getAssignments()) {
	            				Map<Integer, String[]> stringArrayFields = assignment.getStringArrayFields();
	            				Map<Integer, Double> doubleFields = assignment.getDoubleFields();
	            				boolean fHasTaxId = doubleFields != null && !doubleFields.isEmpty() && doubleFields.get(DBField.taxonFieldId) != null;
	            				if (!fHasTaxId && stringArrayFields != null && !stringArrayFields.isEmpty()) {
	            					List<String> notFound = mtxImport.addAccessionInfoToAssignment(Arrays.asList(assignment.getStringArrayFields().get(DBField.sseqIdFieldId)), assignment);
	            					if (notFound.isEmpty())
		            					fSeqUpdated = true;
	            					else
	            						accsMissingFromCache.addAll(notFound);
	            				}
	            			}
	            			if (fSeqUpdated) {
	            				nSuccessfullyUpdated++;
	            				mongoTemplate.save(as);
	            			}
	            		}
	            		LOG.info("In project(s) [" + sProjects + "], " + nSuccessfullyUpdated + " out of " + seqs.size() + " sequences could be updated from NCBI accession cache");
	            		if (nSuccessfullyUpdated > 0 && !Thread.getAllStackTraces().get(Thread.currentThread())[3].getMethodName().equals(new Object(){}.getClass().getEnclosingMethod().getName()))
	            			return taxonomyTree(module, sProjects, assignMethod, processId); // some taxonomy could be updated so let's re-run this very method (unless we're already re-running it because we want to avoid infinite loops!)
	            	}
	            	assignedSeqsByTaxon.put(taxId == null ? null : ((Number) taxId).intValue(), seqs);
	            }
	        }
            if (!accsMissingFromCache.isEmpty()) {
            	LOG.warn("No accession cache found for " + StringUtils.join(accsMissingFromCache, ", "));
            	accsMissingFromCache.clear();
            }

	        for (Integer taxId : assignedSeqsByTaxon.keySet())
	        	assignedSeqCountsByTaxon.put(taxId, assignedSeqsByTaxon.get(taxId).size());

	        if (processId == null && !fFoundInCache)
	        	// add it to the cache
		        try {
		        	HashMap<Comparable, Object> cacheObject = new HashMap<>(assignedSeqCountsByTaxon);
		        	cacheObject.put("_id", sCacheId);
		        	mongoTemplate.save(cacheObject, Constant.TAXO_TREE_CACHE_COLLNAME);
		        }
		        catch (Exception e) {
		        	LOG.warn(e);	// will fail if cacheObject size exceeds 16Mb
		        }
        }
        return buildTaxonomyTree(assignedSeqCountsByTaxon);
    }

    /**
     * Builds a taxonomic tree, appending number of sequences to each taxon
     * 
     * @param assignedSeqCountsByTaxon
     * @return
     */
    private TaxonomyNode buildTaxonomyTree(Map<Integer, Integer> assignedSeqCountsByTaxon) {
    	/* This might be made faster by using the $graphLookup operator */
//    	long before = System.currentTimeMillis();
        HashMap<Integer, TaxonomyNode> createdJsonTaxa = new HashMap<>();
        HashMap<Integer, Taxon> loadedDbTaxa = new HashMap<>();
        Set<Taxon> parentToLoad = new HashSet<>();
        // get the list of taxa from the temp collection, going from the bottom of the tree to the root
        Query q = new Query().addCriteria(Criteria.where("_id").in(assignedSeqCountsByTaxon.keySet()));
        q.fields().exclude("_class").exclude(Taxon.FIELDNAME_RANK).slice(Taxon.FIELDNAME_NAMES, 1);
        List<Taxon> dbTaxa = MongoTemplateManager.getCommonsTemplate().find(q, Taxon.class);
        // while total number of sequence and result count don't match 
        while (!dbTaxa.isEmpty()) {
        	// get all parent taxa from db
            q = new Query().addCriteria(Criteria.where("_id").in(dbTaxa.stream().map(taxon -> taxon.getParentId()).collect(Collectors.toList())));
            q.fields().exclude("_class").exclude(Taxon.FIELDNAME_RANK).slice(Taxon.FIELDNAME_NAMES, 1);
        	for (Taxon parentTaxon : MongoTemplateManager.getCommonsTemplate().find(q, Taxon.class))
        		loadedDbTaxa.put(parentTaxon.getId(), parentTaxon);
        	
            for (Taxon taxon : dbTaxa) {
                // get the sequence count for this taxon 
            	Integer seqsAssignedToTaxon = assignedSeqCountsByTaxon.get(taxon.getId()) == null ? 0 : assignedSeqCountsByTaxon.get(taxon.getId());
                // check if the parent node already exists
                TaxonomyNode parentNode = createdJsonTaxa.get(taxon.getParentId());
                Taxon parentTaxon = loadedDbTaxa.get(taxon.getParentId());
                if (parentTaxon.getId() != 1) {
                    parentToLoad.add(parentTaxon);
                }
                if (parentNode == null) {
                    // if not, create a new one from the parent taxon 
                	Integer seqsAssignedToParent = assignedSeqCountsByTaxon.get(taxon.getParentId());
                    parentNode = new TaxonomyNode(taxon.getParentId(), parentTaxon.getNames().get(0), seqsAssignedToParent != null ? seqsAssignedToParent : 0);
                    createdJsonTaxa.put(parentTaxon.getId(), parentNode);
                }
                // add the current node to parent children 
                TaxonomyNode currentNode = createdJsonTaxa.get(taxon.getId());
                if (currentNode == null) {
                    currentNode = new TaxonomyNode(taxon.getId(), taxon.getNames().get(0), seqsAssignedToTaxon);
                    createdJsonTaxa.put(taxon.getId(), currentNode);
                }
                parentNode.addChildren(currentNode);
            }
            dbTaxa.clear();
            for (Taxon taxon : parentToLoad) {
                dbTaxa.add(taxon);
            }
            parentToLoad.clear();
        }
//        LOG.debug("buildTaxonomyTree took " + (System.currentTimeMillis() - before) + "ms");
        return createdJsonTaxa.get(1);
    }

    public void parseTaxonFilter(String module, int[] projects, String sKey, int[] selectedTaxa, List<DBObject> filterList) throws Exception {
		Collection<Integer> selectedTaxaAndChildren = findTaxaChildren(module, projects, selectedTaxa);
    	boolean fAllowMissingData = !sKey.startsWith("*");
    	int key = Integer.parseInt(!fAllowMissingData ? sKey.substring(1) : sKey);
    	BasicDBObject inFilter = selectedTaxaAndChildren.size() == 0 ? null : new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + key, new BasicDBObject("$in", selectedTaxaAndChildren));
	    if (!fAllowMissingData)
	    	filterList.add(inFilter != null ? inFilter : new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + key, new BasicDBObject("$exists", true)));
	    else if (inFilter != null)
	    	filterList.add(new BasicDBObject("$or", Arrays.asList(new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + key, new BasicDBObject("$exists", false)), inFilter)));
    }

    private Collection<Integer> findTaxaChildren(String module, int[] projectIDs, int[] taxa) throws Exception {
//    	long before = System.currentTimeMillis();
    	Collection<Integer> result = new HashSet<>();
    	TaxonomyNode treeRootNode = taxonomyTree(module, null, null, null);
    	List<Integer> selectedTaxa = new ArrayList<>();
    	for (int taxon : taxa)
    		selectedTaxa.add(taxon);

		Set<TaxonomyNode> nodesToExploreAtNextIteration;

		// find selected nodes in the tree
    	Set<TaxonomyNode> currentlyExploredNodes = new HashSet<>(), selectedNodes = new HashSet<>();
    	currentlyExploredNodes.add(treeRootNode);
    	while (selectedNodes.size() < taxa.length) {
    		nodesToExploreAtNextIteration = new HashSet<>();
    		for (TaxonomyNode currentlyExploredNode : currentlyExploredNodes)
    			if (selectedTaxa.contains(currentlyExploredNode.getId()))
    				selectedNodes.add(currentlyExploredNode);
    			else
    				nodesToExploreAtNextIteration.addAll(currentlyExploredNode.getChildren());
    		currentlyExploredNodes = nodesToExploreAtNextIteration;
    	}

    	// get all children for selected nodes
    	currentlyExploredNodes = selectedNodes;
		nodesToExploreAtNextIteration = null;
    	while (nodesToExploreAtNextIteration == null || currentlyExploredNodes.size() > 0) {
    		nodesToExploreAtNextIteration = new HashSet<>();
    		for (TaxonomyNode currentlyExploredNode : currentlyExploredNodes) {
    			result.add(currentlyExploredNode.getId());
    			nodesToExploreAtNextIteration.addAll(currentlyExploredNode.getChildren());
    		}
    		currentlyExploredNodes = nodesToExploreAtNextIteration;
    	}

//    	LOG.debug("findTaxaChildren took " + (System.currentTimeMillis() - before) + "ms for " + taxa.length + " taxa");
		return result;
	}

    /**
     *
     * @param key
     * @param splitFilterVal
     * @param filterList
     * @throws ParseException 
     */
    public void parseFilter(String sKey, String[] splitFilterVal, List<DBObject> filterList) throws ParseException {
    	boolean fAllowMissingData = !sKey.startsWith("*");
    	int key = Integer.parseInt(!fAllowMissingData ? sKey.substring(1) : sKey);
    	String fieldPath = key == DBField.qseqIdFieldId ? "_id" : (key == DBField.sampleFieldId ? Sequence.FIELDNAME_SAMPLE_COMPOSITION : ((splitFilterVal[0].equals(AssignedSequence.FIELDNAME_ASSIGNMENT) ? AssignedSequence.FIELDNAME_ASSIGNMENT + "." : "") + splitFilterVal[1]));
        String[] values = splitFilterVal.length < 3 ? null : splitFilterVal[2].split("¤");	// values should be separated by '¤'
        if (values == null && fAllowMissingData) {
        	LOG.error("Invalid empty filter for field " + sKey + " while allowing missing data!");
        	return;
        }
        BasicDBObject mainFilter = null;        
    	if (values != null)
	        switch (splitFilterVal[1]) {
	            case DBConstant.STRING_TYPE:
	            case DBConstant.STRING_ARRAY_TYPE:
            		mainFilter = new BasicDBObject(fieldPath + "." + (key == DBField.qseqIdFieldId ? Sequence.FIELDNAME_QSEQID : (key == DBField.sampleFieldId ? SampleReadCount.FIELDNAME_SAMPLE_CODE : key)), new BasicDBObject("$in", values));
	                break;
	            case DBConstant.DOUBLE_TYPE:
	            case DBConstant.DATE_TYPE:
	                // assume following format: min|max
	                if (values.length > 2)
	                	LOG.error("Invalid filter value count for field " + sKey + ": " + splitFilterVal[2]);
	                else {
	                	String start = null, end = null;
	                	if (values.length == 2) {
	                		start = values[0];
	                		end = values[1];
	                	} else {
	                		if (splitFilterVal[2].startsWith("¤"))
	                			end = values[0];
	                		else
	                			start = values[0]; 
	                	}
	                	List<DBObject> rangeFilterList = new ArrayList<>();
	                	if (start != null)
	                		rangeFilterList.add(new BasicDBObject(fieldPath + "." + key, new BasicDBObject("$gte", splitFilterVal[1].equals(DBConstant.DOUBLE_TYPE) ? Double.parseDouble(start) : start)));
	                	if (end != null)
	                		rangeFilterList.add(new BasicDBObject(fieldPath + "." + key, new BasicDBObject("$lte", splitFilterVal[1].equals(DBConstant.DOUBLE_TYPE) ? Double.parseDouble(end) : end)));
	                	mainFilter = new BasicDBObject("$and", rangeFilterList);
	                }
	                break;
	            case DBConstant.GPS_TYPE:
	                BasicDBList bottomLeft = new BasicDBList();
	                bottomLeft.add(Double.parseDouble(values[0]));
	                bottomLeft.add(Double.parseDouble(values[1]));
	                BasicDBList topRight = new BasicDBList();
	                topRight.add(Double.parseDouble(values[2]));
	                topRight.add(Double.parseDouble(values[3]));
	                BasicDBList box = new BasicDBList();
	                box.add(bottomLeft);
	                box.add(topRight);	                
	                mainFilter = new BasicDBObject(fieldPath + "." + key, new BasicDBObject("$geoWithin", new BasicDBObject("$box", box)));
	                break;
	        }
        
        if (mainFilter != null) {
            if (fAllowMissingData)
            	filterList.add(new BasicDBObject("$or", Arrays.asList(new BasicDBObject(fieldPath + "." + (key == DBField.qseqIdFieldId ? Sequence.FIELDNAME_QSEQID : (key == DBField.sampleFieldId ? SampleReadCount.FIELDNAME_SAMPLE_CODE : key)), new BasicDBObject("$exists", false)), mainFilter)));	// apply main filter, allowing missing values
            else
            	filterList.add(mainFilter);	// apply main filter, excluding missing values
        }
        else if (!fAllowMissingData)
        	filterList.add(new BasicDBObject(fieldPath + "." + (key == DBField.qseqIdFieldId ? Sequence.FIELDNAME_QSEQID : key), new BasicDBObject("$exists", true))); // no main filter, only exclude missing values
        else
        	LOG.error("Unable to understand filter to apply for field " + sKey);	// no main filter, missing values allowed: there should be no filter here!
    }

	/**
     * create a view on sequence collection to display sequences matching selected filters
     *
     * @param request
     * @param module
     * @param projects
     * @param processId
     * @param sortBy
     * @return boolean indicating success
	 * @throws Exception 
     */
    @RequestMapping(CREATE_TEMP_VIEW_URL)
    @ResponseBody
    public boolean createTmpView(HttpServletRequest request,
            @RequestParam("module") String module,
            @RequestParam(value="projects", required=false) String projects,
            @RequestParam(value="sortBy", required=false) String sortBy,
            @RequestParam("processId") String processId) throws Exception {
    	
        long before = System.currentTimeMillis();
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        
        int[] projIDs = projects == null ? new int[0] : Arrays.asList(projects.split(";"))
            .stream()
            .mapToInt(Integer::parseInt)
            .toArray();

        List<DBObject> sampleFilters = new ArrayList<>();
        List<DBObject> sequenceFilters = new ArrayList<>();
        List<DBObject> assigmentFilters = new ArrayList<>();

        Enumeration<String> listParam = request.getParameterNames();

        while (listParam.hasMoreElements()) {
            String key = listParam.nextElement();
            String value = request.getParameter(key);

            // format for value has to be collectionTypeAlias:type:value
            String[] splitFilterVal = value.split("§");
            if (splitFilterVal.length < 2)
                continue;

            if (Integer.parseInt(key.replaceFirst("\\*", "")) == DBField.taxonFieldId)
            	parseTaxonFilter(module, projIDs, key, splitFilterVal.length < 3 ? new int[0] : Arrays.asList(splitFilterVal[2].split("¤")).stream().mapToInt(Integer::parseInt).toArray(), assigmentFilters);
            else
	            switch (splitFilterVal[0]) {
	                case Sample.TYPE_ALIAS:
	                    parseFilter(key, splitFilterVal, sampleFilters);
	                    break;
	                case AssignedSequence.TYPE_ALIAS:
	                    parseFilter(key, splitFilterVal, sequenceFilters);
	                    break;
	                case AssignedSequence.FIELDNAME_ASSIGNMENT:
	                    parseFilter(key, splitFilterVal, assigmentFilters);
	                    break;
	            }
        }

        List<BasicDBObject> pipeline = new ArrayList<>();

        List<String> listSampleCode = new ArrayList<>();
        BasicDBList sampleQueries = new BasicDBList();
        if (projIDs.length > 0)
        	sampleQueries.add(new BasicDBObject(DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", projIDs)));
        if (!sampleFilters.isEmpty() || !sampleQueries.isEmpty()) {
        	for (DBObject filter : sampleFilters)
        		sampleQueries.add(filter);
        	BasicDBObject mainSampleQuery = new BasicDBObject("$and", sampleQueries);
            listSampleCode.addAll(mongoTemplate.getCollection(mongoTemplate.getCollectionName(Sample.class)).distinct("_id", mainSampleQuery, String.class).into(new ArrayList<>()));
            LOG.debug("sampleQueries (" + listSampleCode.size() + " results): " + mainSampleQuery);
        }

        // search sequences from those samples 
        BasicDBList sequenceAndAssignmentQueries = new BasicDBList();
        if ((projIDs.length == 1 && projIDs[0] == -1) /*case where no project is visible for this user*/ || (projIDs.length > 0 && mongoTemplate.count(new Query(), MetagenomicsProject.class) > 1))
        	sequenceAndAssignmentQueries.add(new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$in", projIDs)));
        if (!sequenceFilters.isEmpty() || !sampleFilters.isEmpty()) {
            if (!sampleFilters.isEmpty())
            	sequenceAndAssignmentQueries.add(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE, new BasicDBObject("$in", listSampleCode)));

            if (!sequenceFilters.isEmpty())
            	for (DBObject filter : sequenceFilters)
            		sequenceAndAssignmentQueries.add(filter);
        }

        // finally, search matching assignments
        if (!assigmentFilters.isEmpty())
        	sequenceAndAssignmentQueries.addAll(assigmentFilters);

        if (!sequenceAndAssignmentQueries.isEmpty()) {
        	pipeline.add(new BasicDBObject("$match", new BasicDBObject("$and", sequenceAndAssignmentQueries))); // could be removed when !assigmentFilters.isEmpty() but makes execution much faster because applies an indexed filter at sequence level before the $unwind
        	
        	if (!assigmentFilters.isEmpty()) {	// using $unwind and $group and re-applying the filter in between ensures that all assignment-level filters are applied to each assignment individually
        		pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
        	
        		pipeline.add(new BasicDBObject("$match", new BasicDBObject("$and", assigmentFilters)));
        	
    	        DBObject groupFields = new BasicDBObject("_id", "$_id");
    	        groupFields.put(AssignedSequence.FIELDNAME_ASSIGNMENT, new BasicDBObject("$addToSet", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
    	        groupFields.put(DBConstant.DOUBLE_TYPE, new BasicDBObject("$first", "$" + DBConstant.DOUBLE_TYPE));
    	        groupFields.put(Sequence.FIELDNAME_SAMPLE_COMPOSITION, new BasicDBObject("$first", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION));
    	        pipeline.add(new BasicDBObject("$group", groupFields));        		
        	}
        }
        
        // one processId per interface instance => one temp view per interface instance. 
        // views can't be overwritten, so we need to explicitly delete them before creating a new one with the same name. 
        String viewName = MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId);
        mongoTemplate.getCollection(viewName).drop();
        
        mongoTemplate.getDb().createView(viewName, mongoTemplate.getCollectionName(AssignedSequence.class), pipeline);
        
        Thread t = new Thread() {
        	public void run() {
        		createSampleSortCache(mongoTemplate, processId);
        	}
        };
        if (sortBy.startsWith(Sample.TYPE_ALIAS + "."))
        	t.run();	// current display needs to be sorted by a sample field: cache is going to be needed immediately
        else
        	t.start();
        
        LOG.debug("createTmpView took " + (System.currentTimeMillis() - before) + "ms for pipeline " + pipeline);
        return true;
    }

    /**
     * creates a temporary collection containing cache for faster sorting by sample fields
     *
     * @param mongoTemplate
     * @param processId
     */
    private void createSampleSortCache(MongoTemplate mongoTemplate, String processId) {
        long before = System.currentTimeMillis();
        mongoTemplate.getCollection(MongoTemplateManager.TMP_SAMPLE_SORT_CACHE_COLL + Helper.convertToMD5(processId)).drop();
        List<BasicDBObject> pipeline = new ArrayList<>();
        pipeline.add(new BasicDBObject("$unwind", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION));
        pipeline.add(new BasicDBObject("$addFields", new BasicDBObject("_id." + AssignedSequence.FIELDNAME_ASSIGNMENT, new BasicDBObject("$map", new BasicDBObject("input", new BasicDBObject("$range", Arrays.asList(0, new BasicDBObject("$size", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT)))).append("in", "$$this")))));
        pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE).append(AssignedSequence.TYPE_ALIAS, new BasicDBObject("$push", "$_id"))));
        pipeline.add(new BasicDBObject("$lookup",
                new BasicDBObject("from", "samples")
                .append("foreignField", "_id")
                .append("localField", "_id")
                .append("as", Sample.TYPE_ALIAS)));
        pipeline.add(new BasicDBObject("$out", MongoTemplateManager.TMP_SAMPLE_SORT_CACHE_COLL + Helper.convertToMD5(processId)));

        try {
	        mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId)).aggregate(pipeline).allowDiskUse(true).toCollection();	/* invoking toCollection() is necessary for $out to take effect */
	        LOG.debug("createSampleSortCache took " + (System.currentTimeMillis() - before) + "ms for pipeline " + pipeline);
        }
        catch (MongoCommandException mce) {
        	LOG.debug("Unable to generate sample-sort-cache: " + mce.getMessage());
        }
    }

    /**
     * Count the number of records in the temp collection associated to processId
     *
     * @param module
     * @param processId
     * @throws Exception 
     */
    @RequestMapping(RECORD_COUNT_URL)
    @ResponseBody
    public int countRecords(@RequestParam("module") String module, @RequestParam("processId") String processId, @RequestParam String entityLevel) throws Exception {
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        MongoCollection<Document> view = mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));

        List<BasicDBObject> pipeline = new ArrayList<>();

        if (Sample.TYPE_ALIAS.equals(entityLevel)) {
	        pipeline.add(new BasicDBObject("$unwind", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION));
	        pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE).append(Sample.TYPE_ALIAS, new BasicDBObject("$first", "$" +  Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE))));
        }
        else if (AssignedSequence.FIELDNAME_ASSIGNMENT.equals(entityLevel))
            pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));

        pipeline.add(new BasicDBObject("$count", "count"));

        MongoCursor<Document> cursor = view.aggregate(pipeline).allowDiskUse(true).iterator();
        return !cursor.hasNext() ? 0 : (int) cursor.next().get("count");
    }

    /**
     * This returns data related to records that match the passed query.
     *
     * @param module
     * @param sortBy
     * @param sortDir
     * @param page
     * @param size
     * @param processId
     * @return list< DBObject >
     * @throws Exception 
     */
    @RequestMapping(RECORD_SEARCH_URL)
    @ResponseBody
    public List<Document> findRecords(
            @RequestParam("module") String module,
            @RequestParam(value="sortBy", required=false) String sortBy,
            @RequestParam("sortDir") int sortDir,
            @RequestParam("page") int page,
            @RequestParam("size") Integer size,
            @RequestParam("processId") String processId,
            @RequestParam String entityLevel) throws Exception {

        long before = System.currentTimeMillis();
        boolean fUseSampleSortOptimization = sortBy != null && sortBy.startsWith(Sample.TYPE_ALIAS + ".") && !Sample.TYPE_ALIAS.equals(entityLevel) && MongoTemplateManager.get(module).getCollection(MongoTemplateManager.TMP_SAMPLE_SORT_CACHE_COLL + Helper.convertToMD5(processId)).countDocuments() > 0;
    	List<BasicDBObject> pipeline = getDisplayPipeline(sortBy, sortDir, page, size, entityLevel, fUseSampleSortOptimization ? processId : null);
    	MongoCursor<Document> cursor = getCursorForPipeline(module, processId, pipeline, fUseSampleSortOptimization, size);

        HashSet<Integer> subjectTaxa = new HashSet<>();
        List<Document> result = new ArrayList<>();
        while (cursor.hasNext()) {
        	Document dbo = cursor.next();
        	if (!Sample.TYPE_ALIAS.equals(entityLevel)) 
	            for (Object assignment : (List) dbo.get("AS")) {
	            	Number taxId = (Number) ((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).get("" + DBField.taxonFieldId);
	            	if (taxId != null)
	            		subjectTaxa.add(taxId.intValue());
            }
        	result.add(dbo);
        }

        if (!Sample.TYPE_ALIAS.equals(entityLevel)) {
	        HashMap<Integer, String> taxaAncestry = TaxonomyNode.getTaxaAncestry(subjectTaxa, true, false, "; ");
	        for (Document dbo : result) {
		        for (Object assignment : (List) dbo.get("AS")) {
		        	Number taxId = (Number) ((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).get("" + DBField.taxonFieldId);
		        	if (taxId != null) {
		        		int taxIdAsInt = taxId.intValue();
		        		String taxonAncestry = taxaAncestry.get(taxIdAsInt);
		        		((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).put("" + DBField.taxonFieldId, "[" + taxIdAsInt + "]" + (taxonAncestry != null ? " " + taxonAncestry : ""));
		        	}
		        }
	        }
        }
        
        LOG.debug("findRecords took " + (System.currentTimeMillis() - before) + "ms for pipeline " + pipeline);
        return result;
    }
    
    private MongoCursor<Document> getCursorForPipeline(String module, String processId, List<BasicDBObject> pipeline, boolean fUseSampleSortOptimization, Integer batchSize) {
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        MongoCollection<Document> mainCollOrView = fUseSampleSortOptimization ? mongoTemplate.getCollection(MongoTemplateManager.TMP_SAMPLE_SORT_CACHE_COLL + Helper.convertToMD5(processId)) : mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));
        return mainCollOrView.aggregate(pipeline).batchSize(100).allowDiskUse(true).iterator();
    }

    private List<BasicDBObject> getDisplayPipeline(String sortBy, int sortDir, int page, Integer size, String entityLevel, String processIdForSampleSortOptimization) throws Exception {        
        List<BasicDBObject> pipeline = new ArrayList<>();
        
        if (processIdForSampleSortOptimization != null) {	// use cache for faster sorting
        	boolean fDisplayingByAssignment = AssignedSequence.FIELDNAME_ASSIGNMENT.equals(entityLevel);
    		pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.TYPE_ALIAS));
    		
    		if (fDisplayingByAssignment)
    			pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.TYPE_ALIAS + "." + AssignedSequence.FIELDNAME_ASSIGNMENT));	// unwind twice because AS records are in an array or arrays
    		else
    			pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", new BasicDBObject(DBConstant.FIELDNAME_PROJECT, "$" + AssignedSequence.TYPE_ALIAS + "." + DBConstant.FIELDNAME_PROJECT).append(Sequence.FIELDNAME_QSEQID, "$" + AssignedSequence.TYPE_ALIAS + "." + Sequence.FIELDNAME_QSEQID)).append(Sample.TYPE_ALIAS, new BasicDBObject("$addToSet", new BasicDBObject("$arrayElemAt", Arrays.asList("$" + Sample.TYPE_ALIAS, 0))))));
    		
    		pipeline.add(new BasicDBObject("$sort", new BasicDBObject(sortBy, sortDir)));
		    if (size != null) {
		        if (page > 0)
		        	pipeline.add(new BasicDBObject("$skip", page * size));
		    	pipeline.add(new BasicDBObject("$limit", size));
		    }

		    if (fDisplayingByAssignment) {
        		pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.TYPE_ALIAS));
		        pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", new BasicDBObject(DBConstant.FIELDNAME_PROJECT, "$" + AssignedSequence.TYPE_ALIAS + "." + DBConstant.FIELDNAME_PROJECT).append(Sequence.FIELDNAME_QSEQID, "$" + AssignedSequence.TYPE_ALIAS + "." + Sequence.FIELDNAME_QSEQID)).append("ASn", new BasicDBObject("$push", "$" + AssignedSequence.TYPE_ALIAS + "." + AssignedSequence.FIELDNAME_ASSIGNMENT)).append(Sample.TYPE_ALIAS, new BasicDBObject("$addToSet", new BasicDBObject("$arrayElemAt", Arrays.asList("$" + Sample.TYPE_ALIAS, 0))))));		        
		        pipeline.add(new BasicDBObject("$sort", new BasicDBObject(sortBy, sortDir)));	// sort again the chunk we've got in hands because the $group operation shuffled its contents
		    }

			pipeline.add(new BasicDBObject("$lookup",
			        new BasicDBObject("from", MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processIdForSampleSortOptimization))
			        .append("let", new BasicDBObject(DBConstant.FIELDNAME_PROJECT, "$_id." + DBConstant.FIELDNAME_PROJECT).append(Sequence.FIELDNAME_QSEQID, "$_id." + Sequence.FIELDNAME_QSEQID))
			        .append("pipeline", Arrays.asList(new BasicDBObject("$match", new BasicDBObject("$expr", new BasicDBObject("$and", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$_id." + DBConstant.FIELDNAME_PROJECT, "$$" + DBConstant.FIELDNAME_PROJECT)), new BasicDBObject("$eq", Arrays.asList("$_id." + Sequence.FIELDNAME_QSEQID, "$$" + Sequence.FIELDNAME_QSEQID))))))))
			        .append("as", AssignedSequence.TYPE_ALIAS)));

			if (!fDisplayingByAssignment) {
				BasicDBObject secondGroupObj = new BasicDBObject("$group", new BasicDBObject("_id", "$_id")
			  			.append(Sample.TYPE_ALIAS, new BasicDBObject("$addToSet", new BasicDBObject("$arrayElemAt", Arrays.asList("$" + Sample.TYPE_ALIAS, 0))))
			  			.append(AssignedSequence.TYPE_ALIAS, new BasicDBObject("$addToSet", new BasicDBObject("$arrayElemAt", Arrays.asList("$" + AssignedSequence.TYPE_ALIAS, 0)))));
			        pipeline.add(secondGroupObj);
			        
				   	pipeline.add(new BasicDBObject("$sort", new BasicDBObject(sortBy, sortDir)));	// sort again the chunk we've got in hands because the $group operation shuffled its contents
			}
			
		    BasicDBObject projectObj = new BasicDBObject(Sample.TYPE_ALIAS, 1)
	  			.append(DBConstant.DOUBLE_TYPE, new BasicDBObject("$arrayElemAt", Arrays.asList("$" + AssignedSequence.TYPE_ALIAS + "." + DBConstant.DOUBLE_TYPE, 0)))
	  			.append(Sequence.FIELDNAME_SAMPLE_COMPOSITION, new BasicDBObject("$arrayElemAt", Arrays.asList("$" + AssignedSequence.TYPE_ALIAS + "." + Sequence.FIELDNAME_SAMPLE_COMPOSITION, 0)));
		    if (fDisplayingByAssignment)
		    	// only keep assignments that were matched during the search query
		    	projectObj.append(AssignedSequence.FIELDNAME_ASSIGNMENT, new BasicDBObject("$map", new BasicDBObject("input", "$ASn").append("in", new BasicDBObject("$arrayElemAt", Arrays.asList(new BasicDBObject("$arrayElemAt", Arrays.asList("$" + AssignedSequence.TYPE_ALIAS + "." + AssignedSequence.FIELDNAME_ASSIGNMENT, 0)), "$$this")))));
		    else
		    	projectObj.append(AssignedSequence.FIELDNAME_ASSIGNMENT, new BasicDBObject("$arrayElemAt", Arrays.asList("$" + AssignedSequence.TYPE_ALIAS + "." + AssignedSequence.FIELDNAME_ASSIGNMENT, 0)));
		    pipeline.add(new BasicDBObject("$project", projectObj));

	        if (fDisplayingByAssignment) {
	            pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
	            pipeline.add(new BasicDBObject("$addFields", new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT, new String[] {"$" + AssignedSequence.FIELDNAME_ASSIGNMENT})));	// so we keep the same format as when working at the sequence level
	        }
		}
		else {
	        if (Sample.TYPE_ALIAS.equals(entityLevel)) {
		        pipeline.add(new BasicDBObject("$unwind", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION));
		        pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE).append(Sample.TYPE_ALIAS, new BasicDBObject("$first", "$" +  Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE))));
	        }
	        else if (AssignedSequence.FIELDNAME_ASSIGNMENT.equals(entityLevel)) {
	            pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
	            pipeline.add(new BasicDBObject("$addFields", new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT, new String[] {"$" + AssignedSequence.FIELDNAME_ASSIGNMENT})));	// so we keep the same format as when working at the sequence level
	        }

	        boolean fSortingOnSampleField = sortBy != null && sortBy.startsWith(Sample.TYPE_ALIAS + ".");
	        if (!fSortingOnSampleField) {
	            if (sortBy != null)
	            	pipeline.add(new BasicDBObject("$sort", new BasicDBObject(sortBy, sortDir)));
	            if (size != null) {
	                if (page > 0)
	                	pipeline.add(new BasicDBObject("$skip", page * size));
	            	pipeline.add(new BasicDBObject("$limit", size));
	            }
	        }
	        
		    pipeline.add(new BasicDBObject("$lookup",
	          new BasicDBObject("from", MongoTemplateManager.getMongoCollectionName(Sample.class))
	          .append("localField", Sample.TYPE_ALIAS.equals(entityLevel) ? Sample.TYPE_ALIAS : Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE)
	          .append("foreignField", "_id")
	          .append("as", Sample.TYPE_ALIAS)));
	   	
	       if (fSortingOnSampleField) {
	          	pipeline.add(new BasicDBObject("$sort", new BasicDBObject(sortBy, sortDir)));
	           if (size != null) {
	               if (page > 0)
	               	pipeline.add(new BasicDBObject("$skip", page * size));
	           	pipeline.add(new BasicDBObject("$limit", size));
	           }
	       }
	
	       BasicDBObject projectToRemoveUnwantedFields = new BasicDBObject("$project", new BasicDBObject(Sample.TYPE_ALIAS + "._class", 0)
					.append(AssignedSequence.FIELDNAME_ASSIGNMENT + "._class", 0).append("_class", 0)
					.append("_class", 0)
					.append(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.STRING_TYPE + "." + Sequence.FIELDNAME_QSEQID, 0));
	       pipeline.add(projectToRemoveUnwantedFields);
		}

        return pipeline;
    }

    /**
    *
    * Export blast subject sequences as fasta file
    *
    * @param module
    * @param project
    * @param processId
    * @param seqIDs
    * @throws java.io.IOException
    */
   @RequestMapping(value=BLAST_SUBJECT_SEQUENCE_EXPORT_URL)
   public void exportBlastSubjectSequences(HttpServletResponse resp,
           @RequestParam("module") String module,
           @RequestParam("project") int project,
           @RequestParam("processId") String processId,
           @RequestParam("seqIDs") String seqIDs) throws IOException {

       final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Reading project sequence index"});
       ProgressIndicator.registerProgressIndicator(progress);

       ConcurrentSkipListSet<String> seqIdList = new ConcurrentSkipListSet(Arrays.asList(seqIDs.split("\\s*;\\s*")));
       int nTotalSeqCount = seqIdList.size();
       
       File sequenceLocation = new File(appConfig.sequenceLocation());
       boolean fWriteSequences = sequenceLocation.exists() || sequenceLocation.mkdirs();
       if (!fWriteSequences) {
           LOG.error("Could not find nor create folder '" + sequenceLocation.getAbsolutePath() + "'");
       }
       
       MongoTemplate mongoTemplate = MongoTemplateManager.get(module);

       int nCount = 0;
       IndexedFastaSequenceFile projectSequences = new IndexedFastaSequenceFile(new File(sequenceLocation.getAbsolutePath() + File.separator + module + File.separator + project + Sequence.NUCL_FASTA_EXT));

	   progress.addStep("Writing sequences to output fasta");
	   progress.moveToNextStep();
	   
	   ZipOutputStream os = new ZipOutputStream(resp.getOutputStream());
	   resp.setContentType("application/zip; charset=UTF-8");
	   resp.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_FA + ".zip");
	   
	   Query projQuery = new Query(Criteria.where("_id").is(project));
	   projQuery.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
	   String pjAcronym = mongoTemplate.findOne(projQuery, MetagenomicsProject.class).getAcronym().replace(' ', '_');
	
	   os.putNextEntry(new ZipEntry("blast_subjects.fasta"));
	   boolean fLookingAtAssignedSeqs = true;
	   MongoCursor<Document> cursor = mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(AssignedSequence.class)).find(new BasicDBObject("$and", Arrays.asList(new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, project), new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, new BasicDBObject("$in", seqIdList))))).batchSize(1000).projection(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)).iterator();
	   if (!cursor.hasNext()) { // all sequences must be unassigned
			cursor.close();
			cursor = mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(Sequence.class)).find(new BasicDBObject("$and", Arrays.asList(new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, project), new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, new BasicDBObject("$in", seqIdList))))).batchSize(1000).projection(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)).iterator();
			fLookingAtAssignedSeqs = false;
	   }
       while (cursor.hasNext()) {
    	  Document aggResult = cursor.next();
	      StringBuffer samples = new StringBuffer();
	      String qseqid = ((String) Helper.readPossiblyNestedField(aggResult, "_id." + Sequence.FIELDNAME_QSEQID, null));
	      for (Object sc : (List) aggResult.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION))
	      	samples.append((samples.length() == 0 ? "" : ",") + ((String) ((Document)sc).get(SampleReadCount.FIELDNAME_SAMPLE_CODE)));
	       
	      os.write((">" + qseqid + "|" + pjAcronym + " " + samples.toString() + "\n").getBytes());
	      
		  try { // split the nucleotide sequence into several lines of 80 char max
		    String[] result = new String(projectSequences.getSequence(qseqid).getBases()).split("(?<=\\G.{80})");
		    for (String line : result) {
		        os.write(line.getBytes());
		        os.write("\n".getBytes());
		    }
		  }
		  catch (SAMException se) {
		  	LOG.error("Sequence " + qseqid + " not found in file " + projectSequences.toString());
		  	os.write("\n".getBytes());
		  }
		  seqIdList.remove(qseqid);
		  if (++nCount % 100 == 0 || seqIdList.size() > 0) {
		      progress.setCurrentStepProgress(nCount * 100 / nTotalSeqCount);
		  }
		  if (!cursor.hasNext() && seqIdList.size() > 0 && fLookingAtAssignedSeqs) { // some sequences must be unassigned
			  cursor.close();
			  cursor = mongoTemplate.getCollection(MongoTemplateManager.getMongoCollectionName(Sequence.class)).find(new BasicDBObject("$and", Arrays.asList(new BasicDBObject("_id." + DBConstant.FIELDNAME_PROJECT, project), new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, new BasicDBObject("$in", seqIdList))))).batchSize(1000).projection(new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)).iterator();
			  fLookingAtAssignedSeqs = false;
		  }
       }
       projectSequences.close();
       
       os.closeEntry();
       addReadmeFileToExportZip(os, module, Arrays.asList(project));
       os.finish();
       os.close();
       progress.markAsComplete();
   }
   
   private void addReadmeFileToExportZip(ZipOutputStream zos, String sModule, Collection<Integer> projIDs) throws IOException {
	   MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
	   Collection<MetagenomicsProject> projects = mongoTemplate.find(new Query(Criteria.where("_id").in(projIDs)), MetagenomicsProject.class);
       zos.putNextEntry(new ZipEntry("README.txt"));
       zos.write(("Exported sequences came from database '" + sModule + "' and are part of the following project(s): ").getBytes());
       for (MetagenomicsProject pj : projects){
    	   zos.write(("\n\n- " + pj.getAcronym() + ": " + pj.getName()).getBytes());
    	   if (!pj.getDescription().trim().isEmpty())
    		   zos.write(("\n  " + pj.getDescription()).getBytes());
       }
       zos.closeEntry();
   }
   
   @RequestMapping(value=NUCL_SEQUENCE_URL)
   @ResponseBody
   public String getNucleotideSequence(HttpServletRequest request, HttpServletResponse response, @RequestParam String module, @RequestParam int project, @RequestParam String qseqid) throws Exception {
       File sequenceLocation = new File(appConfig.sequenceLocation());
       File fastaFilePath = new File(sequenceLocation.getAbsolutePath() + File.separator + module + File.separator + project + Sequence.NUCL_FASTA_EXT);
       File faiFile = new File(fastaFilePath.getParent() + "/_" + fastaFilePath.getName() + Sequence.NUCL_FAI_EXT);	// first try and find the "light" version of the index, that contains only assigned sequences
       if (!faiFile.exists() || faiFile.length() == 0)
    	   faiFile = new File(fastaFilePath.getParent() + "/" + fastaFilePath.getName() + Sequence.NUCL_FAI_EXT); // load the full index file
       
       MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
       try (IndexedFastaSequenceFile indexedFastaSequenceFile = new IndexedFastaSequenceFile(fastaFilePath, new FastaSequenceIndex(faiFile.toPath()))) {
    	   Query projQuery = new Query(Criteria.where("_id").is(project));
    	   projQuery.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
    	   
    	   Query seqQuery = new Query(new Criteria().andOperator(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(project), Criteria.where("_id." + Sequence.FIELDNAME_QSEQID).is(qseqid)));
    	   seqQuery.fields().include(Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE);

    	   return ">" + qseqid + "|" + mongoTemplate.findOne(projQuery, MetagenomicsProject.class).getAcronym().replace(' ', '_') + " "
       		   	+ StringUtils.join(mongoTemplate.findOne(seqQuery, AssignedSequence.class).getSampleComposition().stream().map(src -> ((SampleReadCount) src).getSp()).collect(Collectors.toList()), ',')
       		   	+ "\n" + StringUtils.join(indexedFastaSequenceFile.getSequence(qseqid).getBaseString().split("(?<=\\G.{80})"), '\n');
       }
   }

   /**
    *
    * Export searched sequences as fasta file
    *
    * @param processId
	* @throws Exception 
    */
   @RequestMapping(value=SEQUENCE_EXPORT_URL)
   public void exportSearchedSequences(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="exportToServer", required=false) Boolean fExportToServer, @RequestParam("projects") String projectIds, @RequestParam("processId") String processId) throws Exception {
	   long before = System.currentTimeMillis();
       String module = processId.split("¤")[1].split("¤")[0];

       final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Please wait"});
       ProgressIndicator.registerProgressIndicator(progress);

       MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
       String tempViewName = MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId);

       File sequenceLocation = new File(appConfig.sequenceLocation());
       boolean fWriteSequences = sequenceLocation.exists() || sequenceLocation.mkdirs();
       if (!fWriteSequences) {
           LOG.error("Could not find nor create folder '" + sequenceLocation.getAbsolutePath() + "'");
       }

       int nCount = 0, nTotalCount = countRecords(module, processId, AssignedSequence.TYPE_ALIAS);
       HashMap<Integer, IndexedFastaSequenceFile> fastaFilesByProject = new HashMap<>();
       ArrayList<Thread> threadsToWaitFor = new ArrayList<>();

	   progress.addStep("Reading sequence index");
	   progress.moveToNextStep();

       for (int projectId : Arrays.asList(projectIds.split(";")).stream().mapToInt(Integer::parseInt).toArray()) {
           Thread t = new Thread() {
               @Override
               public void run() {
                   File fastaFilePath = new File(sequenceLocation.getAbsolutePath() + File.separator + module + File.separator + projectId + Sequence.NUCL_FASTA_EXT);
                   File faiFile = new File(fastaFilePath.getParent() + "/_" + fastaFilePath.getName() + Sequence.NUCL_FAI_EXT);	// first try and find the "light" version of the index, that contains only assigned sequences
                   if (!faiFile.exists() || faiFile.length() == 0)
                	   faiFile = new File(fastaFilePath.getParent() + "/" + fastaFilePath.getName() + Sequence.NUCL_FAI_EXT); // load the full index file
	               try {
	            	   fastaFilesByProject.put(projectId, new IndexedFastaSequenceFile(fastaFilePath, new FastaSequenceIndex(faiFile.toPath())));
	               }
	               catch (Throwable t) {
	            	   progress.setError(t.getMessage());
	               }
//	               LOG.debug("loaded in " + (System.currentTimeMillis() - before) + "ms");
               }
           };
           
           threadsToWaitFor.add(t);
           t.start();
       }

       ZipOutputStream os = null;
       try {
           for (Thread t : threadsToWaitFor) // wait for all threads before moving to next phase
    			if (t != null)
    				t.join();

           if (progress.getError() != null)
        	   return;
           
	       if (Boolean.TRUE.equals(fExportToServer)) {
	    	   	String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
	    	   	String exportHash = Helper.convertToMD5(processId);
	            String relativeOutputFolder = TMP_OUTPUT_FOLDER + File.separator + exportHash + File.separator;
	            File outputLocation = new File(webappRootPath + File.separator + relativeOutputFolder);
	            if (!outputLocation.exists() && !outputLocation.mkdirs())
	                throw new Exception("Unable to create folder: " + outputLocation);

	            os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputLocation.getAbsolutePath() + File.separator + EXPORT_FILENAME_FA + ".zip"))));
	            response.setContentType("text/plain");
	            LOG.debug("On-server export file for export " + processId + ": " + request.getContextPath() + "/" + relativeOutputFolder.replace(File.separator, "/") + EXPORT_FILENAME_FA + ".zip");
	            response.getWriter().write(exportHash);
	            response.flushBuffer();
	       } else {
	    	   	os = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()));
				response.setContentType("application/zip; charset=UTF-8");
				response.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_FA + ".zip");
	       }
	       
	       os.putNextEntry(new ZipEntry(EXPORT_FILENAME_FA));
	       HashMap<Integer, String> projectAcronyms = new HashMap<>();
	
		   progress.addStep("Writing sequences to output fasta");
		   progress.moveToNextStep();
	       Query q = new Query();
	       q.fields().include("_id");
	       MongoCursor<Document> cursor = mongoTemplate.getCollection(tempViewName).aggregate(Arrays.asList(new BasicDBObject("$project", new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)))).allowDiskUse(true).iterator();
	       while (cursor.hasNext()) {
	    	   Document doc = cursor.next();
	    	   Document docId = (Document) doc.get("_id");
	           int projectId = (Integer) docId.get(DBConstant.FIELDNAME_PROJECT);
	           String pjAcronym = projectAcronyms.get(projectId);
	           if (pjAcronym == null) {
	        	   Query query = new Query(Criteria.where("_id").is(projectId));
	        	   query.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
	        	   pjAcronym = mongoTemplate.findOne(query, MetagenomicsProject.class).getAcronym().replace(' ', '_');
	        	   projectAcronyms.put(projectId, pjAcronym);
	           }
	
	           String qseqid = (String) docId.get(Sequence.FIELDNAME_QSEQID);
	           os.write((">" + qseqid + "|" + pjAcronym + " "
	        		   	+ StringUtils.join(((List<Document>) doc.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION)).stream().map(dbo -> ((Document) dbo).get(SampleReadCount.FIELDNAME_SAMPLE_CODE)).collect(Collectors.toList()), ',')
	        		   	+ "\n").getBytes());
	
	           IndexedFastaSequenceFile projectSequences = fastaFilesByProject.get(projectId);
	
	           try { // split the nucleotide sequence into several lines of 80 char max
		            String[] result = new String(projectSequences.getSequence(qseqid).getBases()).split("(?<=\\G.{80})");
		            for (String line : result) {
		            	os.write((line + "\n").getBytes());
		                os.flush();
		            }
	           }
	           catch (SAMException se) {
	           	LOG.error("Sequence " + qseqid + " not found in file " + projectSequences.toString());
	           	os.write("\n".getBytes());
	           }
	           nCount++;
	           if (nCount % 100 == 0 || nCount == nTotalCount) {
	               progress.setCurrentStepProgress(nCount * 100 / nTotalCount);
	           }
	       }
	       for (IndexedFastaSequenceFile ifsf : fastaFilesByProject.values())
	    	   ifsf.close();
	       
	       os.closeEntry();
	       addReadmeFileToExportZip(os, module, fastaFilesByProject.keySet());
	       os.finish();
	       os.close();
	       progress.markAsComplete();
	       LOG.debug("exportSearchedSequences took " + (System.currentTimeMillis() - before) + "ms for " + nCount + " taxa");
		}
		catch (Exception e) {
			LOG.error("Error exporting fasta file", e);
			progress.setError(e.getMessage());
		}
	   finally {
	   	if (os != null)
			try {
				os.close();
			} catch (IOException ignored) {}
	   }
   }

    /**
     * @param processId
     * @return
     * @throws Exception 
     */
    @RequestMapping(value=SAMPLE_EXPORT_URL, produces = "text/csv;charset=UTF-8")
    @ResponseBody
    public void exportSamples(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="exportToServer", required=false) Boolean fExportToServer, @RequestParam("processId") String processId) {
        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Creating sample file"});
        ProgressIndicator.registerProgressIndicator(progress);
    	
        ZipOutputStream os = null;
        
        try {
        	String module = processId.split("¤")[1].split("¤")[0];
	        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
	        MongoCollection<Document> view = mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));
	        Collection<Integer> projIDs = view.aggregate(Arrays.asList(new BasicDBObject("$group", new BasicDBObject("_id", null).append(DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$addToSet", "$_id." + DBConstant.FIELDNAME_PROJECT))))).allowDiskUse(true).batchSize(1000).first().getList(DBConstant.FIELDNAME_PROJECT, Integer.class); // we can't simply call distinct because it doesn't support allowDiskUse
	        List<DBField> fieldsToExport = mongoTemplate.find(new Query(new Criteria().andOperator(new Criteria().orOperator(Criteria.where(DBConstant.FIELDNAME_PROJECT).size(0), Criteria.where(DBConstant.FIELDNAME_PROJECT).in(projIDs)), Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(Sample.TYPE_ALIAS))), DBField.class);
	        
	        if (Boolean.TRUE.equals(fExportToServer)) {
	     	   	String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
	     	   	String exportHash = Helper.convertToMD5(processId);
	             String relativeOutputFolder = TMP_OUTPUT_FOLDER + File.separator + exportHash + File.separator;
	             File outputLocation = new File(webappRootPath + File.separator + relativeOutputFolder);
	             if (!outputLocation.exists() && !outputLocation.mkdirs())
	                 throw new Exception("Unable to create folder: " + outputLocation);

	             os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputLocation.getAbsolutePath() + File.separator + EXPORT_FILENAME_SP + ".zip"))));
	             response.setContentType("text/plain");
	             LOG.debug("On-server export file for export " + processId + ": " + request.getContextPath() + "/" + relativeOutputFolder.replace(File.separator, "/") + EXPORT_FILENAME_SP);
	             response.getWriter().write(exportHash);
	             response.flushBuffer();
	        } else {
	             os = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()));
	             response.setContentType("application/zip");
	             response.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_SP + ".zip");
	        }
	        
	        os.putNextEntry(new ZipEntry(EXPORT_FILENAME_SP));
	        os.write(Sample.FIELDNAME_SAMPLE_CODE.getBytes("UTF-8"));
	        for (DBField dbFIeld : fieldsToExport)
	        	os.write(("\t" + dbFIeld.getFieldName()).getBytes("UTF-8"));
	        
	        int totalCount = countRecords(module, processId, Sample.TYPE_ALIAS), i = 0;
	        List<BasicDBObject> pipeline = getDisplayPipeline("_id", 1, 0, null, Sample.TYPE_ALIAS, null);
	        MongoCursor<Document> cursor = view.aggregate(pipeline).allowDiskUse(true).iterator();
	        while (cursor.hasNext()) {
	        	Document sampleObj = (Document) ((List) cursor.next().get(Sample.TYPE_ALIAS)).get(0);
	        	os.write(("\n" + sampleObj.get("_id")).getBytes("UTF-8"));
	        	for (DBField dbField : fieldsToExport)
	        		os.write(("\t" + Helper.readPossiblyNestedField(sampleObj, dbField.getType() + "." + dbField.getId(), ",")).getBytes("UTF-8"));
	        	progress.setCurrentStepProgress(++i * 100 / totalCount);
	        }
	        
	        os.closeEntry();        
	        addReadmeFileToExportZip(os, module, projIDs);
	        os.finish();
	        os.close();        
	        progress.markAsComplete();
    	}
    	catch (Exception e) {
    		LOG.error("Error exporting sample file", e);
    		progress.setError(e.getMessage());
    	}
        finally {
        	if (os != null)
    			try {
    				os.close();
    			} catch (IOException ignored) {}
        }
    }
    
    /**
     * @param processId
     * @return
     * @throws Exception 
     */
    @RequestMapping(value=SEQ_COMPO_EXPORT_URL, produces = "text/csv")
    @ResponseBody
    public void exportSequenceComposition(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="exportToServer", required=false) Boolean fExportToServer, @RequestParam("processId") String processId) {
        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Determining list of involved samples", "Creating sequence composition file"});
        ProgressIndicator.registerProgressIndicator(progress);
    	
        ZipOutputStream os = null;
        long before = System.currentTimeMillis();
        
        try {
	        String module = processId.split("¤")[1].split("¤")[0];
	        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
	
	        List<BasicDBObject> pipeline = getDisplayPipeline("_id", 1, 0, null, AssignedSequence.TYPE_ALIAS, null);
	        MongoCollection<Document> view = mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));
	        long i = 0, totalCount = view.aggregate(Arrays.asList(new BasicDBObject("$count", "count"))).allowDiskUse(true).first().getInteger("count"); // we can't simply call countDocuments because it doesn't support allowDiskUse
	        ArrayList<String> samples = getSamplesInvolvedInResultset(pipeline, view);

	        if (Boolean.TRUE.equals(fExportToServer)) {
	     	   	String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
	     	   	String exportHash = Helper.convertToMD5(processId);
	             String relativeOutputFolder = TMP_OUTPUT_FOLDER + File.separator + exportHash + File.separator;
	             File outputLocation = new File(webappRootPath + File.separator + relativeOutputFolder);
	             if (!outputLocation.exists() && !outputLocation.mkdirs())
	                 throw new Exception("Unable to create folder: " + outputLocation);

	             os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputLocation.getAbsolutePath() + File.separator + EXPORT_FILENAME_SQ + ".zip"))));
	             response.setContentType("text/plain");
	             LOG.debug("On-server export file for export " + processId + ": " + request.getContextPath() + "/" + relativeOutputFolder.replace(File.separator, "/") + EXPORT_FILENAME_SQ);
	             response.getWriter().write(exportHash);
	             response.flushBuffer();
	        } else {
	             os = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()));
	             response.setContentType("application/zip");
	             response.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_SQ + ".zip");
	        }
	
	        os.putNextEntry(new ZipEntry(EXPORT_FILENAME_SQ));
	        os.write(Sequence.FIELDNAME_QSEQID.getBytes());
	        for (String sample : samples)
	        	os.write(("\t" + sample).getBytes());
	
	    	progress.moveToNextStep();
	    	
	        HashMap<Integer, String> projectAcronyms = new HashMap<>();
	        MongoCursor<Document> cursor = view.aggregate(Arrays.asList(new BasicDBObject("$project", new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)))).allowDiskUse(true).batchSize(1000).iterator(); // we can't simply call find because it doesn't support allowDiskUse
//	        long beforeHasNext = System.currentTimeMillis();
	        while (cursor.hasNext()) {
//	        	long afterHasNext = System.currentTimeMillis();
	        	Document mainObj = cursor.next();
//	        	if (afterHasNext - beforeHasNext > 1)
//	        		System.err.println(i + " : " + (afterHasNext - beforeHasNext));
	        	
	        	Document id = (Document) mainObj.get("_id");
	        	int projId = (int) id.get(DBConstant.FIELDNAME_PROJECT);
	        	String pjAcronym = projectAcronyms.get(projId);
	        	if (pjAcronym == null) {
	        		Query query = new Query(Criteria.where("_id").is(projId));
	        		query.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
	        		pjAcronym = mongoTemplate.findOne(query, MetagenomicsProject.class).getAcronym().replace(' ', '_');
	        		projectAcronyms.put(projId, pjAcronym);
	        	}
	        	
	        	os.write(("\n" + id.get(Sequence.FIELDNAME_QSEQID) + "|" + pjAcronym).getBytes());
	        	HashMap<String, Integer> sampleReadCounts = new HashMap<>();
	        	for (Object sampleAndCount : (List) mainObj.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION))
	        		sampleReadCounts.put((String) ((Document) sampleAndCount).get(SampleReadCount.FIELDNAME_SAMPLE_CODE), (int) ((Document) sampleAndCount).get(SampleReadCount.FIELDNAME_SAMPLE_COUNT));
	            for (String sample : samples) {
	            	Integer count = sampleReadCounts.get(sample);
	            	os.write(("\t" + (count == null ? 0 : count)).getBytes());
	            }
	        	progress.setCurrentStepProgress((int) (++i * 100 / totalCount));
//	        	beforeHasNext = System.currentTimeMillis();
	        }
	        
	        os.closeEntry();        
	        addReadmeFileToExportZip(os, module, projectAcronyms.keySet());
	        os.finish();
	        os.close();        
	        progress.markAsComplete();
	        LOG.debug("exportSequenceComposition took " + (System.currentTimeMillis() - before) + "ms for " + totalCount + " records");
    	}
    	catch (Exception e) {
    		LOG.error("Error exporting sequence composition file", e);
    		progress.setError(e.getMessage());
    	}
        finally {
        	if (os != null)
    			try {
    				os.close();
    			} catch (IOException ignored) {}
        }
    }
    
    private ArrayList<String> getSamplesInvolvedInResultset(List<BasicDBObject> pipeline, MongoCollection<Document> coll) {	// determine list of samples involved in a resultset
    	ArrayList<String> samples = new ArrayList<>();
    	List<BasicDBObject> pipelineClone = new ArrayList<>();
        pipelineClone.addAll(pipeline);
        pipelineClone.add(new BasicDBObject("$unwind", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION));
        pipelineClone.add(new BasicDBObject("$group", new BasicDBObject("_id", "$" + Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE)));
        MongoCursor<Document> sampleCursor = coll.aggregate(pipelineClone).allowDiskUse(true).iterator();
        while (sampleCursor.hasNext())
        	samples.add((String) sampleCursor.next().get("_id"));
        return samples;
    }

    /**
     * @param processId
     * @return
     * @throws Exception 
     */
    @RequestMapping(value=ASSIGNMENT_EXPORT_URL, produces = "text/csv")
    @ResponseBody
    public void exportAssignments(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="exportToServer", required=false) Boolean fExportToServer, @RequestParam("processId") String processId) {
        String module = processId.split("¤")[1].split("¤")[0];
        long before = System.currentTimeMillis();

        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Creating assignment file"});
        ProgressIndicator.registerProgressIndicator(progress);
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        String viewName = MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId);
        MongoCollection<Document> view = mongoTemplate.getCollection(viewName);
        Collection<Integer> projIDs = view.aggregate(Arrays.asList(new BasicDBObject("$group", new BasicDBObject("_id", null).append(DBConstant.FIELDNAME_PROJECT, new BasicDBObject("$addToSet", "$_id." + DBConstant.FIELDNAME_PROJECT))))).allowDiskUse(true).batchSize(1000).first().getList(DBConstant.FIELDNAME_PROJECT, Integer.class); // we can't simply call distinct because it doesn't support allowDiskUse
        List<DBField> fieldsToExport = mongoTemplate.find(new Query(new Criteria().andOperator(new Criteria().orOperator(Criteria.where(DBConstant.FIELDNAME_PROJECT).size(0), Criteria.where(DBConstant.FIELDNAME_PROJECT).in(projIDs)), Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT))), DBField.class);
      	
        boolean fRemoveSseqIdCol = fieldsToExport.stream().map(dbf -> dbf.getId()).collect(Collectors.toList()).contains(DBField.sseqIdFieldId) && mongoTemplate.count(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).in(projIDs)), "cache_" + DBField.sseqIdFieldId) == 0;
        if (fRemoveSseqIdCol)
        	fieldsToExport = fieldsToExport.stream().filter(dbf -> dbf.getId() != DBField.sseqIdFieldId).collect(Collectors.toList());	// sseqid field is not used in exported data
        else {	// see if we need to export the taxid field
        	Aggregation tAgg = Aggregation.newAggregation(Arrays.asList(new MatchOperation(Criteria.where(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.STRING_ARRAY_TYPE + "." + DBField.sseqIdFieldId).exists(false)), new LimitOperation(1)))
        									.withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        	if (mongoTemplate.aggregate(tAgg, viewName, AssignedSequence.class).getUniqueMappedResult() == null)	// see if we find at least one assigned sequence without sseqid in the selection to export
        		fieldsToExport = fieldsToExport.stream().filter(dbf -> dbf.getId() != DBField.taxonFieldId).collect(Collectors.toList());	// sseqid field is used in each assignment of exported data: we don't need to re-export the corresponding taxIDs since they were probably found by metaXplor from their sseqid
        }

        ZipOutputStream os = null;
        try {
            int totalCount = countRecords(module, processId, AssignedSequence.FIELDNAME_ASSIGNMENT), i = 0;
	        if (Boolean.TRUE.equals(fExportToServer)) {
	     	   	String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
	     	   	String exportHash = Helper.convertToMD5(processId);
	             String relativeOutputFolder = TMP_OUTPUT_FOLDER + File.separator + exportHash + File.separator;
	             File outputLocation = new File(webappRootPath + File.separator + relativeOutputFolder);
	             if (!outputLocation.exists() && !outputLocation.mkdirs())
	                 throw new Exception("Unable to create folder: " + outputLocation);

	             os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputLocation.getAbsolutePath() + File.separator + EXPORT_FILENAME_AS + ".zip"))));
	             response.setContentType("text/plain");
	             LOG.debug("On-server export file for export " + processId + ": " + request.getContextPath() + "/" + relativeOutputFolder.replace(File.separator, "/") + EXPORT_FILENAME_AS);
	             response.getWriter().write(exportHash);
	             response.flushBuffer();
	        } else {
	             os = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()));
	             response.setContentType("application/zip");
	             response.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_AS + ".zip");
	        }

	        os.putNextEntry(new ZipEntry(EXPORT_FILENAME_AS));

	        // write header columns
	        os.write(Sequence.FIELDNAME_QSEQID.getBytes());
	        for (DBField dbField : fieldsToExport)
	        	if (!DBConstant.FIELDNAME_HIT_DEFINITION.contains(dbField.getFieldName()))
	        		os.write(("\t" + dbField.getFieldName()).getBytes());

	        HashMap<Integer, String> projectAcronyms = new HashMap<>();
	        List<BasicDBObject> pipeline = Arrays.asList(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT), new BasicDBObject("$sort", new BasicDBObject("_id", 1))/*, new BasicDBObject("$project", new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT, 1))*/);
	        MongoCursor<Document> cursor = view.aggregate(pipeline).batchSize(1000).allowDiskUse(true).iterator();
	        while (cursor.hasNext()) {
	        	Document mainObj = cursor.next();
	        	Document id = (Document) mainObj.get("_id");
	        	int projId = (int) id.get(DBConstant.FIELDNAME_PROJECT);
	        	String pjAcronym = projectAcronyms.get(projId);
	        	if (pjAcronym == null) {
	        		Query query = new Query(Criteria.where("_id").is(projId));
	        		query.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
	        		pjAcronym = mongoTemplate.findOne(query, MetagenomicsProject.class).getAcronym();
	        		projectAcronyms.put(projId, pjAcronym);
	        	}

	        	Document assignmentObj = (Document) mainObj.get(AssignedSequence.FIELDNAME_ASSIGNMENT);
	        	os.write(("\n" + id.get(Sequence.FIELDNAME_QSEQID) + "|" + pjAcronym).getBytes());
	        	for (DBField dbField : fieldsToExport)
	        		if (!DBConstant.FIELDNAME_HIT_DEFINITION.contains(dbField.getFieldName())) {
	        			Object val = Helper.readPossiblyNestedField(assignmentObj, dbField.getType() + "." + dbField.getId(), ",");
	        			if (val instanceof Double && (((Double) val).intValue() == ((Double) val).doubleValue()))
	        				val = ((Double) val).intValue();
	        			os.write(("\t" + val).getBytes());
	        		}
	
	        	progress.setCurrentStepProgress(++i * 100 / totalCount);
	        }
	
	        os.closeEntry();        
	        addReadmeFileToExportZip(os, module, projectAcronyms.keySet());
	        os.finish();
	        os.close();        
	        progress.markAsComplete();
	        LOG.debug("exportAssignments took " + (System.currentTimeMillis() - before) + "ms for " + totalCount + " records");
		}
		catch (Exception e) {
			LOG.error("Error exporting assignment file", e);
			progress.setError(e.getMessage());
		}
	   finally {
	   	if (os != null)
			try {
				os.close();
			} catch (IOException ignored) {}
	   }
    }

    @RequestMapping(value=BIOM_EXPORT_URL, produces = "application/json")
    @ResponseBody
    public void exportBiom(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="exportToServer", required=false) Boolean fExportToServer, @RequestParam(value="assignMethod", required=false) String assignMethod, @RequestParam("processId") String processId) {
    	long before = System.currentTimeMillis();
        String module = processId.split("¤")[1].split("¤")[0];

        final ProgressIndicator progress = new ProgressIndicator(processId, new String[]{"Getting sample information"});
        ProgressIndicator.registerProgressIndicator(progress);
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        MongoCollection<Document> view = mongoTemplate.getCollection(MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId));
        
        ZipOutputStream os = null;
        
        try {
	        if (Boolean.TRUE.equals(fExportToServer)) {
	     	   	String webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
	     	   	String exportHash = Helper.convertToMD5(processId);
	             String relativeOutputFolder = TMP_OUTPUT_FOLDER + File.separator + exportHash + File.separator;
	             File outputLocation = new File(webappRootPath + File.separator + relativeOutputFolder);
	             if (!outputLocation.exists() && !outputLocation.mkdirs())
	                 throw new Exception("Unable to create folder: " + outputLocation);
	             
	             os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputLocation.getAbsolutePath() + File.separator + EXPORT_FILENAME_BM + ".zip"))));
	             response.setContentType("text/plain");
	             LOG.debug("On-server export file for export " + processId + ": " + request.getContextPath() + "/" + relativeOutputFolder.replace(File.separator, "/") + EXPORT_FILENAME_SQ);
	             response.getWriter().write(exportHash);
	             response.flushBuffer();
	        } else {
	             os = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()));
	             response.setContentType("application/zip");
	             response.setHeader("Content-disposition", "inline; filename=" + EXPORT_FILENAME_BM + ".zip");
	        }
	        
	        os.putNextEntry(new ZipEntry(EXPORT_FILENAME_BM));
	
			// build a column for each sample
	        Collection<Integer> projIDs = new HashSet<>();
			List<HashMap<String, Object>> columns = new ArrayList<>();
			HashMap<String, Integer> sampleIndexMap = new LinkedHashMap<>();
			List<DBField> fieldsToExport = mongoTemplate.find(new Query(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(Sample.TYPE_ALIAS)), DBField.class);
			Set<String> fieldsWithData = new HashSet<String>();	// we will remove fields with no data within this result-set
			List<BasicDBObject> pipeline = getDisplayPipeline("_id", 1, 0, null, Sample.TYPE_ALIAS, null);
			MongoCursor<Document> cursor = view.aggregate(pipeline).allowDiskUse(true).iterator();
	        while (cursor.hasNext()) {
	        	Document dbo = cursor.next();
	        	List spList = ((List) dbo.get(Sample.TYPE_ALIAS));
	        	if (spList.isEmpty()) {
	        		LOG.warn("Unable to find sample to export: " + dbo.get("_id"));
	        		continue;
	        	}
	        	Document sampleObj = (Document) spList.get(0);
	        	HashMap<String, Object> sampleInfo = new LinkedHashMap<>(), sampleMetadata = new LinkedHashMap<>();
	        	String spId = (String) sampleObj.get("_id");
	        	sampleInfo.put("id", spId);
	        	for (DBField dbField : fieldsToExport) {
	        		Object val = Helper.readPossiblyNestedField(sampleObj, dbField.getType() + "." + dbField.getId(), "; ");
	        		if (!"".equals(val))
	        			fieldsWithData.add(dbField.getFieldName());
	        		sampleMetadata.put(dbField.getFieldName(), val.toString());
	        	}
	        	sampleInfo.put("metadata", sampleMetadata);
	        	sampleIndexMap.put(spId, columns.size());
	        	columns.add(sampleInfo);        	
	        	for (Object pjId : (List) sampleObj.get(DBConstant.FIELDNAME_PROJECT))
	        		projIDs.add((int) pjId);
	        }
	        for (Object unusedField : CollectionUtils.disjunction(fieldsToExport.stream().map(f -> f.getFieldName()).collect(Collectors.toList()), fieldsWithData))
		        for (HashMap<String, Object> col : columns)
		        	((HashMap<String, Object>) col.get("metadata")).remove(unusedField);
	
	        progress.addStep("Counting sequence records");
	        progress.moveToNextStep();        
	        int nCurrentSeq = 0;
	        pipeline = Arrays.asList(new BasicDBObject("$count", "count"));
	        cursor = view.aggregate(pipeline).allowDiskUse(true).iterator();
	        int totalCount = !cursor.hasNext() ? 0 : (int) cursor.next().get("count");
	
	        progress.addStep("Processing sequence records");
	        progress.moveToNextStep();
	        
			// build a row for each sequence and a data object for each cell 
			List<HashMap<String, Object>> rows = new ArrayList<>();
			List<Integer[]> data = new ArrayList<>();
			
			DBField assignmentMethodField = null;
			if (assignMethod != null && !assignMethod.isEmpty()) {
		    	assignmentMethodField = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + Assignment.FIELDNAME_ASSIGN_METHOD + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
		    	if (assignmentMethodField == null)
		    		throw new Exception("Unable to find assignment field: " + Assignment.FIELDNAME_ASSIGN_METHOD);
			}
	
	    	DBField bestHit = mongoTemplate.findOne(new Query(new Criteria().andOperator(Criteria.where(DBField.FIELDNAME_ENTITY_TYPEALIAS).is(AssignedSequence.FIELDNAME_ASSIGNMENT), Criteria.where(DBField.FIELDNAME_NAME).regex(Pattern.compile("^" + DBField.bestHitFieldName + "$", Pattern.CASE_INSENSITIVE)))), DBField.class);
	    	if (bestHit == null) {
	    		pipeline = new ArrayList<>();
				pipeline.add(new BasicDBObject("$unwind", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
				if (assignMethod != null && !assignMethod.isEmpty())
					pipeline.add(new BasicDBObject("$match", new BasicDBObject(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.STRING_TYPE + "." + assignmentMethodField.getId(), assignMethod)));	// limit to passed assignment method
	    		pipeline.add(new BasicDBObject("$sort", new BasicDBObject("_id", 1)));
	    		pipeline.add(new BasicDBObject("$project", new BasicDBObject("tx", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId).append(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1)));
	    	}
	    	else
	    	{	// pipeline is more complex because it handles multiple assignments per sequence (takes the best hit in this case)
				BasicDBObject bestHitFilter = new BasicDBObject("input", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT);
				BasicDBObject filterCond = new BasicDBObject("$ne", Arrays.asList(new BasicDBObject("$ifNull", Arrays.asList("$$a." + DBConstant.STRING_TYPE + "." + bestHit.getId(), "")), ""));
				if (assignMethod != null && !assignMethod.isEmpty())
					filterCond = new BasicDBObject("$and", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$$a." + DBConstant.STRING_TYPE + "." + assignmentMethodField.getId(), assignMethod)), filterCond));	// limit to passed assignment method
				bestHitFilter.append("as", "a").append("cond", filterCond);
				BasicDBObject filteredAssignments = new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$gt", Arrays.asList(new BasicDBObject("$size", "$" + AssignedSequence.FIELDNAME_ASSIGNMENT), 1)), new BasicDBObject("$filter", bestHitFilter), "$" + AssignedSequence.FIELDNAME_ASSIGNMENT));
				BasicDBObject project = new BasicDBObject(Sequence.FIELDNAME_SAMPLE_COMPOSITION, 1);
				BasicDBObject taxon = new BasicDBObject("vars", new BasicDBObject("as", filteredAssignments));
				taxon.append("in", new BasicDBObject("$arrayElemAt", Arrays.asList("$$as" + "." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId, 0)));
				project.append("tx", new BasicDBObject("$let", taxon));
				pipeline = Arrays.asList(new BasicDBObject("$sort", new BasicDBObject("_id", 1)), new BasicDBObject("$project", project));
	    	}
	
	        cursor = view.aggregate(pipeline).allowDiskUse(true).batchSize(1000).iterator();
	        HashMap<Integer, String> projectAcronyms = new LinkedHashMap<>();
	        List<Integer> taxIDs = new ArrayList<>();	// will be used to call TaxonomyNode.getTaxaAncestry in batch
	        String previousQseqid = null;
	        while (cursor.hasNext()) {
	        	Document doc = (Document) cursor.next();
	        	Document docId = (Document) doc.get("_id");
	            int projectId = (Integer) docId.get(DBConstant.FIELDNAME_PROJECT);
	            String pjAcronym = projectAcronyms.get(projectId);
	            if (pjAcronym == null) {
	         	   Query query = new Query(Criteria.where("_id").is(projectId));
	         	   query.fields().include(MetagenomicsProject.FIELDNAME_ACRONYM);
	         	   pjAcronym = mongoTemplate.findOne(query, MetagenomicsProject.class).getAcronym();
	         	   projectAcronyms.put(projectId, pjAcronym);
	            }
	
	        	HashMap<String, Object> seqInfo = new LinkedHashMap<>();
	        	String qseqid = (String) docId.get(Sequence.FIELDNAME_QSEQID);
	        	if (qseqid.equals(previousQseqid)) {
	        		progress.setError("Several taxa linked to sequence " + qseqid);
	        		return;
	        	}
	        	previousQseqid = qseqid;
	        	
	        	seqInfo.put("id", qseqid + "|" + pjAcronym);
	        	Object taxIdObj = doc.get("tx");	// happens to be null when provided accession could not be found using the NCBI web-service
	        	Integer taxId = doc.get("tx") == null ? Taxon.UNIDENTIFIED_ORGANISM_TAXID : (int) (double) taxIdObj;
	            taxIDs.add(taxId);
	        	seqInfo.put("metadata", taxId);	// use this temporarily until it gets replaced with the taxonomy list
	        	
	        	List composition = (List) doc.get(Sequence.FIELDNAME_SAMPLE_COMPOSITION);
	        	for (Object c : composition){
	        		Document sampleContribution = (Document) c;
	        		data.add(new Integer[] {rows.size(), sampleIndexMap.get(sampleContribution.get(SampleReadCount.FIELDNAME_SAMPLE_CODE)), (Integer) sampleContribution.get(SampleReadCount.FIELDNAME_SAMPLE_COUNT)});
	        	}
	        	        	
	        	rows.add(seqInfo);
	        	if (++nCurrentSeq % 100 == 0)
	        		progress.setCurrentStepProgress(nCurrentSeq * 100 / totalCount);
	        }
	        
	        progress.addStep("Building taxonomy arrays");
	        progress.moveToNextStep();
	        HashSet<Integer> unknownTaxa = new HashSet<>();
	        HashMap<Integer, String> taxonomyMap = TaxonomyNode.getTaxaAncestry(taxIDs, true, true, "|");
	        for (HashMap<String, Object> seqInfo : rows) {
	        	Integer taxId = (Integer) seqInfo.get("metadata");
	        	String taxo = taxonomyMap.get(taxId);
	        	Object biomTaxArray;
	        	String[] fullTaxArray;
	        	if (taxo == null) {
	        		unknownTaxa.add(taxId);
	        		fullTaxArray = new String[0];
	        		biomTaxArray = fullTaxArray;
	        	}        	
	        	else {
	        		fullTaxArray = taxo.split("\\|");
	        		biomTaxArray = new ArrayList<>(TaxonomyNode.rankPrefixes.values());
	        		boolean fGotKingdom = false;
	        		String kingdomPrefix = TaxonomyNode.rankPrefixes.get("kingdom");
	        		for (String tax : fullTaxArray) {
	        			if (tax.length() > 3 && tax.charAt(1) == '_' && tax.charAt(2) == '_') {
	        				int prefixIndex = ((ArrayList<String>) biomTaxArray).indexOf(tax.subSequence(0, 3));
	        				((ArrayList<String>) biomTaxArray).set(prefixIndex, tax);
	        				if (tax.startsWith(kingdomPrefix))
	        					fGotKingdom = true;
	        			}
	        		}
	        		if (!fGotKingdom && !(fullTaxArray[0].charAt(1) == '_' && fullTaxArray[0].charAt(2) == '_'))
	        			((ArrayList<String>) biomTaxArray).set(0, kingdomPrefix + fullTaxArray[0]);
	        	}
	        	seqInfo.put("metadata", new HashMap<String, Object>() {{ put("taxonomy", biomTaxArray); put("full_taxonomy", fullTaxArray); }});
	        }
	        if (unknownTaxa.size() > 0)
	        	LOG.warn("Taxa not found: " + unknownTaxa);
	
	        HashMap<String, Object> result = new LinkedHashMap<>();
			result.put("id", "");
			result.put("format", "Biological Observation Matrix 1.0.0");
			result.put("format_url", "http://biom-format.org");
			result.put("matrix_type", "sparse");
			Properties prop = new Properties();
			prop.load(request.getServletContext().getResourceAsStream("/META-INF/MANIFEST.MF"));
			String appVersion = prop.getProperty("Implementation-version");
			result.put("generated_by", "metaXplor" + (appVersion == null ? "" : (" v" + appVersion)));
			result.put("date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
			result.put("type", "Taxon table");
			result.put("matrix_element_type", "int");
			result.put("shape", new int[] {rows.size(), columns.size()});
	        
			result.put("rows", rows);
	        result.put("columns", columns);
	        result.put("data", data);
			
	        String s = new ObjectMapper().writeValueAsString(result);
	        os.write(s.getBytes("UTF-8"));
	
	        os.closeEntry();        
	        addReadmeFileToExportZip(os, module, projIDs);
	        os.finish();
	        os.close();        
	        progress.markAsComplete();
	        
	        LOG.debug("exportBiom took " + (System.currentTimeMillis() - before) + "ms for matrix size " + rows.size() + " * " + columns.size());
		}
		catch (Exception e) {
			LOG.error("Error exporting BIOM file", e);
			progress.setError(e.getMessage());
		}
	   finally {
	   	if (os != null)
			try {
				os.close();
			} catch (IOException ignored) {}
	   }
    }

    /**
     * @param module
     * @param sample
     * @return The requested sample
     */
    @RequestMapping(value = SAMPLE_DETAILS_URL, method = RequestMethod.GET)
    @ResponseBody
    public Document sampleDetails(@RequestParam("module") String module, @RequestParam String sample)
    {
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);

        // match sequence, lookup on individuals and assignments 
        Document matchStep = new Document("_id", sample);
        
        BasicDBObject projectStep = new BasicDBObject("_class", 0)
                .append(Sample.TYPE_ALIAS + "._class", 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.FIELDNAME_PROJECT, 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "._id", 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "._class", 0);
        
        List<BasicDBObject> pipeline = Arrays.asList(new BasicDBObject("$match", matchStep),  new BasicDBObject("$project", projectStep));

        MongoCursor<Document> cursor = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Sample.class)).aggregate(pipeline).allowDiskUse(true).iterator();
        // return the first item of the cursor. It contains only one element anyway
        try {
        	return cursor.next();
        }
        catch (NoSuchElementException nsee) {
        	return matchStep;
        }
    }

    /**
     * @param module
     * @param qseqid
     * @return The requested sequence and its relaed assignments
     */
    @RequestMapping(value = SEQUENCE_DETAILS_URL, method = RequestMethod.GET)
    @ResponseBody
    public Document sequenceDetails(@RequestParam("module") String module, @RequestParam("pj") Integer projId, @RequestParam(Sequence.FIELDNAME_QSEQID) String qseqid)
    {
        MongoTemplate mongoTemplate = MongoTemplateManager.get(module);

        // match sequence, lookup on individuals and assignments 
        BasicDBObject matchStep = new BasicDBObject("_id." + Sequence.FIELDNAME_QSEQID, qseqid);
        matchStep.put("_id." + DBConstant.FIELDNAME_PROJECT, projId);

        BasicDBObject projectLookup = new BasicDBObject("$lookup",
                new BasicDBObject("from", MongoTemplateManager.getMongoCollectionName(MetagenomicsProject.class))
                .append("localField", "_id." + DBConstant.FIELDNAME_PROJECT)
                .append("foreignField", "_id")
                .append("as", MetagenomicsProject.TYPE_ALIAS));
        
        BasicDBObject sampleLookup = new BasicDBObject("$lookup",
                new BasicDBObject("from", MongoTemplateManager.getMongoCollectionName(Sample.class))
                .append("localField", Sequence.FIELDNAME_SAMPLE_COMPOSITION + "." + SampleReadCount.FIELDNAME_SAMPLE_CODE)
                .append("foreignField", "_id")
                .append("as", Sample.TYPE_ALIAS));

        BasicDBObject projectStep = new BasicDBObject("_class", 0)
                .append(Sample.TYPE_ALIAS + "._class", 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.FIELDNAME_PROJECT, 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "._id", 0)
                .append(AssignedSequence.FIELDNAME_ASSIGNMENT + "._class", 0);

        List<BasicDBObject> pipeline = Arrays.asList(new BasicDBObject("$match", matchStep), projectLookup, sampleLookup, new BasicDBObject("$project", projectStep));

        MongoCursor<Document> cursor = mongoTemplate.getCollection(mongoTemplate.getCollectionName(AssignedSequence.class)).aggregate(pipeline).allowDiskUse(true).iterator();
        Document result = cursor.next();
        HashSet<Integer> subjectTaxa = new HashSet<>();
        for (Object assignment : (List) result.get(AssignedSequence.FIELDNAME_ASSIGNMENT)) {
        	Double taxId = (Double) ((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).get("" + DBField.taxonFieldId);
        	if (taxId != null)
        		subjectTaxa.add((int) (double) taxId);
        }

        HashMap<Integer, String> taxaAncestry = TaxonomyNode.getTaxaAncestry(subjectTaxa, true, false, "; ");
        for (Object assignment : (List) result.get(AssignedSequence.FIELDNAME_ASSIGNMENT)) {
        	Double taxId = (Double) ((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).get("" + DBField.taxonFieldId);
        	if (taxId != null) {
        		int taxIdAsInt = (int) (double) taxId;
        		String taxonAncestry = taxaAncestry.get(taxIdAsInt);
        		((Document) ((Document) assignment).get(DBConstant.DOUBLE_TYPE)).put("" + DBField.taxonFieldId, "[" + taxIdAsInt + "]" + (taxonAncestry != null ? " " + taxonAncestry : ""));        		
        	}
        }
        return result;
    }

//    private HashMap<Integer, String> getTaxaAncestry(MongoTemplate mongoTemplate, Collection<Integer> taxa) {
//    	long before = System.currentTimeMillis();
//    	String taxCollName = mongoTemplate.getCollectionName(Taxon.class);
//    	List<DBObject> pipeline = new ArrayList<>();
//    	pipeline.add(new BasicDBObject("$match", new BasicDBObject("_id", new BasicDBObject("$in", taxa))));
//    	pipeline.add(new BasicDBObject("$graphLookup", new BasicDBObject("from", taxCollName).append("startWith", "$_id").append("connectFromField", "pa").append("connectToField", "_id").append("as", "tx").append("depthField", "dp")));
//    	pipeline.add(new BasicDBObject("$unwind", "$tx"));
//    	pipeline.add(new BasicDBObject("$sort", new BasicDBObject("tx.dp", -1)));
//    	pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", "$_id").append("tx", new BasicDBObject("$push", "$tx"))));
//    	Cursor cursor = mongoTemplate.getCollection(taxCollName).aggregate(pipeline, AggregationOptions.builder().allowDiskUse(true).build());
//
//    	HashMap<Integer, String> result = new HashMap<>();
//    	while (cursor.hasNext()) {
//    		DBObject taxonWithAncestry = cursor.next();
//    		StringBuffer taxonomy = new StringBuffer();
//    		for (Object tx : (List) taxonWithAncestry.get("tx"))
//    			if ((int) ((Document)tx).get("_id") != 1)
//    				taxonomy.append((taxonomy.length() == 0 ? "" : "; ") + ((List)((Document)tx).get(Taxon.FIELDNAME_NAMES)).get(0));
//    		result.put((int) taxonWithAncestry.get("_id"), taxonomy.toString()); 
//    	}
//    	LOG.debug("getTaxaAncestry took " + (System.currentTimeMillis() - before) + "ms for " + taxa.size() + " taxa");
//    	return result;
//    }

    /**
     * Gets the progress indicator.
     *
     * @param processId the process id
     * @return the progress indicator
     */
    @RequestMapping(PROGRESS_INDICATOR_URL)
    @ResponseBody
    public ProgressIndicator getProgressIndicator(HttpServletResponse resp, @RequestParam("processId") String processId) {
    	ProgressIndicator pi = ProgressIndicator.get(processId);
    	if (pi == null)
    		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return pi;
    }

    /**
     * Abort process.
     *
     * @param processId the process id
     * @return true, if successful
     */
    @RequestMapping(PROCESS_ABORT_URL)
    @ResponseBody
    public boolean abortProcess(@RequestParam("processId") String processId) {
        ProgressIndicator progress = ProgressIndicator.get(processId.substring(1 + processId.indexOf('§')));
        if (progress != null) {
            progress.abort();
            LOG.debug("Aborting process: " + processId);
            return true;
        }
        return false;
    }

    /**
     * delete temp collection on pageunload
     *
     * @param resp
     * @param module
     * @param processId
     */
    @RequestMapping(value = SEARCH_INTERFACE_CLEANUP_URL, method = RequestMethod.DELETE)
    @ResponseBody
    public void onSearchInterfaceUnload(HttpServletResponse resp, @RequestParam("module") String module, @RequestParam("processId") String processId) {
    	String viewName = MongoTemplateManager.TMP_VIEW_PREFIX + Helper.convertToMD5(processId);
        MongoTemplateManager.get(module).dropCollection(viewName);
        LOG.debug("Dropped temp view from module " + module + ": " + viewName);
    	String collName = MongoTemplateManager.TMP_SAMPLE_SORT_CACHE_COLL + Helper.convertToMD5(processId);
        MongoTemplateManager.get(module).dropCollection(collName);
        LOG.debug("Dropped temp sample-sort-cache collection from module " + module + ": " + collName);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
    
    @RequestMapping(value = PROJECT_FASTA_URL + "/{module}/{accessKey}/{projId}" + Sequence.NUCL_FASTA_EXT, produces = "text/plain")
    public void getProjectFasta(HttpServletRequest request, HttpServletResponse resp, @PathVariable String module, @PathVariable(value="projId") int projId, @PathVariable(value="accessKey") String receivedAccessKey /* sort-of prevents making it available to anyone */) throws IOException {
    	try {
	    	// only allow being called locally or by passing the correct access-key
			String remoteAddress = request.getRemoteAddr();
			boolean fAllowAccess = false;
			Set<InetAddress> inetAddressesWithInterfaceNames = BackOfficeController.getInetAddressesWithInterfaceNames().keySet();
	        for (InetAddress addr : inetAddressesWithInterfaceNames) {
	        	String aLocalHostAddress = addr.getHostAddress().replaceAll("/", "");
	        	int percentPos = aLocalHostAddress.indexOf("%");
	        	if (percentPos != -1)
	        		aLocalHostAddress = aLocalHostAddress.substring(0, percentPos);
	            if (aLocalHostAddress.equals(remoteAddress)) {
		            fAllowAccess = true;
	            	break;
	            }
	        }
			
	    	File fastaFile = new File(appConfig.sequenceLocation() + File.separator + module + File.separator + projId + Sequence.NUCL_FASTA_EXT);
			if (!fAllowAccess) {
		    	File indexFile = new File(fastaFile.getAbsolutePath() + Sequence.NUCL_FAI_EXT);
		        String correctAccessKey = DigestUtils.md5Hex(new FileInputStream(indexFile));
		    	if (correctAccessKey.equals(receivedAccessKey)) 
		    		fAllowAccess = true;
			}
			
			if (!fAllowAccess) {
	    		build401Response(resp);
	    		return;
	    	}
			
	    	BufferedInputStream is = new BufferedInputStream(new FileInputStream(fastaFile));
	    	try {
				resp.setHeader("Content-disposition", "inline; filename=" + projId + Sequence.NUCL_FASTA_EXT);
		    	int len;
		    	byte[] buffer = new byte[1024];
		        while ((len = is.read(buffer)) > 0)
		        	resp.getOutputStream().write(buffer, 0, len);
		        resp.getOutputStream().close();
	    	}
	    	finally {
	    		is.close();
	    	}
    	}
    	catch (Error e) {
    		LOG.error("Error getting project fasta", e);
    		throw e;
    	}
    }
    
    /**
     * delete temp collection on pageunload
     *
     * @param resp
     * @param module
     * @param processId
     */
	@RequestMapping(value = IMPORT_INTERFACE_CLEANUP_URL, method = RequestMethod.DELETE)
	public void onImportInterfaceUnload(HttpServletResponse resp, @RequestParam("module") String module, @RequestParam("processId") String processId) {
		String shortProcessId = processId.substring(1 + processId.indexOf('_'));
		URL importArchiveURL = checkedImportFiles.get(shortProcessId);
		if (importArchiveURL != null && importArchiveURL.toString().startsWith("file:")) {
			checkedImportFiles.remove(shortProcessId);
			new File(importArchiveURL.getFile()).delete();
			LOG.debug("import archive deleted: " + importArchiveURL);
		}
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
	
	public void build401Response(HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		resp.getWriter().write("You are not allowed to access this resource");
	}

	public void build404Response(HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
		resp.getWriter().write("This resource does not exist");
	}
	
	public void build500Response(HttpServletResponse resp, String msg) throws IOException {
		resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		resp.getWriter().write(msg == null ? "Internal server error" : msg);
	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {	// do some cleanup at application startup
		for (File f : commonsMultipartResolver.getFileItemFactory().getRepository().listFiles())
			if (!f.isDirectory())
				f.delete();	// must be a somehow remaining import file
	}
	
	@ResponseBody
	@RequestMapping(value = MAX_UPLOAD_SIZE_PATH, method = RequestMethod.GET)
	public Long maxUploadSize(HttpServletRequest request, @RequestParam(required=false) Boolean capped) {
		String maxSize = null;

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean fIsAdmin = auth != null && auth.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN)); // limit only applies when capped for administrators
		if (!fIsAdmin) {
			maxSize = appConfig.get("maxImportSize_" + (auth == null ? "anonymousUser" : auth.getName()));
			if (maxSize == null || !StringUtils.isNumeric(maxSize))
				maxSize = appConfig.get("maxImportSize");
		}
		Long nMaxSizeMb = fIsAdmin ? null : (maxSize == null ? 500 /* absolute default */ : Long.parseLong(maxSize));

		if (!Boolean.TRUE.equals(capped))
			return nMaxSizeMb;
		
		return Math.min(commonsMultipartResolver.getFileUpload().getSizeMax() / (1024 * 1024), fIsAdmin ? Integer.MAX_VALUE : nMaxSizeMb);
	}

	@ResponseBody
	@RequestMapping(value = MAX_REFPKG_SIZE_PATH, method = RequestMethod.GET)
	public Long maxRefPkgSize(HttpServletRequest request) {
		String maxSize = null;
		
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean fIsAdmin = auth != null && auth.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN)); // limit only applies when capped for administrators
		if (!fIsAdmin) {
			maxSize = appConfig.get("maxRefPkgSize_" + (auth == null ? "anonymousUser" : auth.getName()));
			if (maxSize == null || !StringUtils.isNumeric(maxSize))
				maxSize = appConfig.get("maxRefPkgSize");
		}
		Long nMaxSizeMb = maxSize == null ? 5 /* absolute default */ : Long.parseLong(maxSize);
		return nMaxSizeMb;
	}

	@ResponseBody
	@RequestMapping(value = MAX_PHYLO_ASSIGN_FASTA_SEQ_COUNT, method = RequestMethod.GET)
	public Long maxPhyloAssignFastaSeqCount(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		String maxSize = appConfig.get("maxPhyloAssignFastaSeqCount_" + (auth == null ? "anonymousUser" : auth.getName()));
		if (maxSize == null || !StringUtils.isNumeric(maxSize))
			maxSize = appConfig.get("maxPhyloAssignFastaSeqCount");

		Long nMaxSizeMb = maxSize == null ? 5000 /* absolute default */ : Long.parseLong(maxSize);
		return nMaxSizeMb;
	}
	
	@ResponseBody
	@RequestMapping(value=ONLINE_OUTPUT_TOOLS_URL)
	public HashMap<String, HashMap<String, String>> getOnlineOutputToolURLs() {
		HashMap<String, HashMap<String, String>> results = new LinkedHashMap<>();
		for (int i=1; ; i++)
		{
			String toolInfo = appConfig.get("onlineOutputTool_" + i);
			if (toolInfo == null)
				break;

			String[] splitToolInfo = toolInfo.split(";");
			if (splitToolInfo.length >= 2 && splitToolInfo[1].trim().length() > 0 && splitToolInfo[0].trim().length() > 0)
			{
				HashMap<String, String> aResult = new HashMap<>();
				aResult.put("url", splitToolInfo[1].trim());
				results.put(splitToolInfo[0].trim(), aResult);
			}
		}
		return results;
	}
}
