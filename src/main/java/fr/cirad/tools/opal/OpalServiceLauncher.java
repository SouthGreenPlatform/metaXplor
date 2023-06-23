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
package fr.cirad.tools.opal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.axis.types.URI;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import edu.sdsc.nbcr.opal.types.BlockingOutputType;
import edu.sdsc.nbcr.opal.types.JobSubOutputType;
import edu.sdsc.nbcr.opal.types.StatusOutputType;
import fr.cirad.gridengine.opalclient.OpalJobInvoker;
import fr.cirad.metaxplor.jobs.base.IOpalServiceInvoker;
import fr.cirad.metaxplor.model.Blast;
import fr.cirad.metaxplor.model.PhylogeneticAssignment;
import fr.cirad.metaxplor.model.Sequence;
import fr.cirad.tools.AppConfig;
import fr.cirad.tools.Constant;
import fr.cirad.tools.Helper;
import fr.cirad.tools.ProgressIndicator;
import fr.cirad.tools.mongo.MongoTemplateManager;
import fr.cirad.web.controller.BackOfficeController;
import fr.cirad.web.controller.metaxplor.MetaXplorController;

@Component
public class OpalServiceLauncher implements IOpalServiceInvoker {
	
    private static final Logger LOG = Logger.getLogger(OpalServiceLauncher.class);
    
    private static AppConfig appConfig;
    
    /**
     * prefix for metaXplor service names
     */
    public static final String METAXPLOR_SERVICE_PREFIX = "mtx-";
    /**
     * suffix for nucleic db (for blast)
     */
    public static final String NUCL_SUFFIX = "_nucl";
    /**
     * suffix for proteic db (for blast)
     */
    public static final String PROT_SUFFIX = "_prot";
    /**
     * extension for pplacer files
     */
    public static final String JPLACE_EXT = ".jplace";

	private String webappRootPath;
	private String webappRootURL;

	public OpalServiceLauncher(HttpServletRequest request) throws UnknownHostException, SocketException {
		webappRootPath = request.getServletContext().getRealPath(MetaXplorController.PATH_SEPARATOR);
		
		webappRootURL = appConfig.get("enforcedWebapRootUrl");
		if (webappRootURL == null) {
			String computedBaseURL = BackOfficeController.determinePublicHostName(request);
			if (computedBaseURL != null)
				webappRootURL = computedBaseURL + request.getContextPath();
		}
	}

	public OpalServiceLauncher() {}
	
	@Autowired
	public void setAppConfig(AppConfig ac) {
		appConfig = ac;
	}

	@Override
	public String makeBlastDb(String module, int projId, File fastaFile) throws Exception {
		if (webappRootURL == null)
			throw new Exception("The makeBlastDb method requires providing the constructor with a HttpServletRequest object!");

    	SimpleDateFormat timeSDF = new SimpleDateFormat(Constant.DATE_FORMAT_HHMMSS);

        // where blast database files will be written 
        String bankLocation = appConfig.blastDbLocation();
        JobSubOutputType subOut = OpalJobInvoker.invokeNonBlocking(METAXPLOR_SERVICE_PREFIX + "makeblastdb", projId + " \"" + bankLocation + File.separator + module + "\"",
            null,
            new String[] { fastaFile.getAbsolutePath() },
            null,
            null);

        String jobID = subOut.getJobID();
        ScheduledExecutorService dataTimer = Executors.newScheduledThreadPool(3);
        Runnable r = () -> {
            try {
                StatusOutputType sot = OpalJobInvoker.getJobStatus(METAXPLOR_SERVICE_PREFIX + "makeblastdb", jobID);

                LOG.debug(timeSDF.format(new Date()) + " | " + jobID + " -> " + sot.getCode());

                if (sot.getCode() == OpalJobInvoker.STATUS_FAILED) {
                    StringBuilder error;
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(new URL(sot.getBaseURL().toString() + "/stderr.txt").openStream()))) {
                    	 // if job failed, parse output to know the cause and send it back to the user
                        String inputLine;
                        error = new StringBuilder();
                        while ((inputLine = input.readLine()) != null)
                            error.append(inputLine).append("\n");
                        if (error.toString().trim().isEmpty()) { // try with stdout
                            try (BufferedReader input2 = new BufferedReader(new InputStreamReader(new URL(sot.getBaseURL().toString() + "/stdout.txt").openStream()))) {
                               error = new StringBuilder();
                               while ((inputLine = input2.readLine()) != null)
                                   error.append(inputLine).append("\n");
                           }
                        }
                    }
                    LOG.error("makeblastDB failed: " + error.toString());
                    dataTimer.shutdown();
                } else if (sot.getCode() == OpalJobInvoker.STATUS_DONE) {
                    dataTimer.shutdown();
                }
            } catch (IOException ex) {
                LOG.error("remote error", ex);
            }
        };
        dataTimer.scheduleAtFixedRate(r, 10, 10, TimeUnit.SECONDS);
        return jobID;
	}
	
	@Override
	public String getMakeBlastDbStatus(String jobId) throws Exception {
		return checkJobStatus("makeblastdb", jobId).getMessage();
	}

	@Override
	public void cleanupDbFiles(String module) throws IOException {
        BlockingOutputType subOut = OpalJobInvoker.invokeBlocking(METAXPLOR_SERVICE_PREFIX + "cleanupDbFiles", "\"" + appConfig.blastDbLocation() + File.separator + module + "\"", null, null, null, null);

        int code = subOut.getStatus().getCode();
        if (code != OpalJobInvoker.STATUS_PENDING && code != OpalJobInvoker.STATUS_ACTIVE && code != OpalJobInvoker.STATUS_DONE && code != OpalJobInvoker.STATUS_STAGE_IN && code != OpalJobInvoker.STATUS_STAGE_OUT) {
            throw new IOException("The execution of the Opal job failed with the following error: "
                + subOut.getStatus().getMessage() + ". You can consult output and error file here: "
                + subOut.getStatus().getBaseURL() + "/stdout.txt, "
                + subOut.getStatus().getBaseURL() + "/stderr.txt");
        }
	}

	@Override
	public void cleanupProjectFiles(String module, int projId) throws IOException {
        BlockingOutputType subOut = OpalJobInvoker.invokeBlocking(METAXPLOR_SERVICE_PREFIX + "cleanupProjectFiles", "\"" +  appConfig.blastDbLocation() + File.separator + module + "\" " + projId, null, null, null, null);
 
        int code = subOut.getStatus().getCode();
        if (code != OpalJobInvoker.STATUS_PENDING && code != OpalJobInvoker.STATUS_ACTIVE && code != OpalJobInvoker.STATUS_DONE && code != OpalJobInvoker.STATUS_STAGE_IN && code != OpalJobInvoker.STATUS_STAGE_OUT) {
            throw new IOException("The execution of the Opal job failed with the following error: "
                + subOut.getStatus().getMessage() + ". You can consult output and error file here: "
                + subOut.getStatus().getBaseURL() + "/stdout.txt, "
                + subOut.getStatus().getBaseURL() + "/stderr.txt");
        }
	}

	@Override
	public String inspectRefPackage(String refPkgName) throws IOException {		
        BlockingOutputType subOut = OpalJobInvoker.invokeBlocking(METAXPLOR_SERVICE_PREFIX + "inspectRefPkg", refPkgName, null, new String[] {webappRootPath + "/" + MetaXplorController.REF_PKG_FOLDER + "/" + refPkgName}, null, null);
 
        int code = subOut.getStatus().getCode();
        if (code != OpalJobInvoker.STATUS_PENDING && code != OpalJobInvoker.STATUS_ACTIVE && code != OpalJobInvoker.STATUS_DONE && code != OpalJobInvoker.STATUS_STAGE_IN && code != OpalJobInvoker.STATUS_STAGE_OUT) {
            throw new IOException("The execution of the Opal job failed with the following error: "
                + subOut.getStatus().getMessage() + ". You can consult output and error file here: "
                + subOut.getStatus().getBaseURL() + "/stdout.txt, "
                + subOut.getStatus().getBaseURL() + "/stderr.txt");
        }
        return subOut.getStatus().getBaseURL().toString();
	}
	
	@Override
	public Collection<String> diamond(String sModule, String banks, String program, String sequence, String expect, String align, ProgressIndicator progress) throws IOException {
	   	final Set<String> result = new HashSet<>();

        // parse expect/ align to make sure correct values are used
        double evalue = Double.parseDouble(expect);
        int alignement = Integer.parseInt(align);
        String basePath = appConfig.blastDbLocation();

        // threads: number of CPU 
        final StringBuffer extraParameters = new StringBuffer(program);

        switch (program) {
            case "blastp":
                extraParameters.append(" --matrix BLOSUM62");
                break;
            case "blastx":
                extraParameters.append(" --strand both");
                break;
        }
        extraParameters.append(" --threads 2");
        
        final String finalSequence = (sequence.startsWith(">") ? "" : ">query\n") + sequence.toLowerCase();

        String diamondQueryHash = Helper.convertToMD5(finalSequence + MetaXplorController.PATH_SEPARATOR + MetaXplorController.DIAMOND_PREFIX + program + MetaXplorController.PATH_SEPARATOR + expect + MetaXplorController.PATH_SEPARATOR + align);
        String querySeqPath = webappRootPath + MetaXplorController.TMP_OUTPUT_FOLDER + File.separator + diamondQueryHash + ".fa";
        File fastaQuery = new File(querySeqPath);
        fastaQuery.getParentFile().mkdirs();
        if (fastaQuery.createNewFile()) {	// if file doesn't exist, create it
            LOG.debug("new query file created : " + querySeqPath);
        }
        try (FileOutputStream fastaStream = new FileOutputStream(fastaQuery, false)) {
            byte[] queryBytes = finalSequence.getBytes();
            fastaStream.write(queryBytes);
        }
        final List<Integer> projIdList = Arrays.asList(banks.split(";")).stream().map(id -> Integer.parseInt(id)).collect(Collectors.toList());
        final String[] jobIDs = new String[projIdList.size()];
        for (int i=0; i<projIdList.size(); i++) {
        	String diamondResultId = projIdList.get(i) + "_" + diamondQueryHash;
            result.add(diamondResultId);
            Blast diamondResult = MongoTemplateManager.get(sModule).findById(diamondResultId, Blast.class);
            if (diamondResult == null) {
                JobSubOutputType subOut = OpalJobInvoker.invokeNonBlocking(METAXPLOR_SERVICE_PREFIX + MetaXplorController.DIAMOND_PREFIX + program,
                	"--db \"" + basePath + MetaXplorController.PATH_SEPARATOR + sModule + MetaXplorController.PATH_SEPARATOR + projIdList.get(i) + "\""
                	+ " --evalue " + expect
	                + " --max-target-seqs " + alignement + extraParameters.toString()
	                + " --out blast.out --outfmt 5 --query " + diamondQueryHash + ".fa",
	                null, new String[] { querySeqPath }, 1, null);

                jobIDs[i] = subOut.getJobID();
            }
            else {
            	diamondResult.getJobIDs().add(progress.getProcessId());
            	MongoTemplateManager.get(sModule).save(diamondResult);
            }
        }

        int nLaunchedJobCount = 0;
        for (String jobID : jobIDs)
        	if (jobID != null)
        		nLaunchedJobCount++;
        if (nLaunchedJobCount == 0)
        	progress.markAsComplete();
        else
        { // monitor all job statuses
        	// create an executor service that runs in another thread, so the browser gets the hash strings immediately
            ScheduledExecutorService dataTimer = Executors.newScheduledThreadPool(1);
            Runnable r = () -> {
                AtomicInteger pendingJobCount = new AtomicInteger(), completedJobCount = new AtomicInteger(); 
            	for (int i=0; i<projIdList.size(); i++) {
					try {

						StatusOutputType status = checkJobStatus(program, jobIDs[i]);
		                checkIfJobHasFailed(status);

						if (OpalJobInvoker.STATUS_DONE == status.getCode()) {
							completedJobCount.incrementAndGet();
		            		LOG.debug("url : " + status.getBaseURL().toString());
		                    Blast diamondResult = new Blast(projIdList.get(i) + "_" + diamondQueryHash,
	                            status.getBaseURL().toString(),
	                            Calendar.getInstance().getTime(),
	                            evalue,
	                            alignement,
	                            finalSequence,
	                            MetaXplorController.DIAMOND_PREFIX + program,
	                            projIdList.get(i));
		                    
		                    diamondResult.getJobIDs().add(progress.getProcessId());
		                    MongoTemplateManager.get(sModule).save(diamondResult);
						}
						else {
							if (OpalJobInvoker.STATUS_PENDING == status.getCode()) {
								pendingJobCount.incrementAndGet();
								progress.setProgressDescription("DIAMOND job #" + (i+1) + " is being queued");
							}
							else if (OpalJobInvoker.STATUS_ACTIVE == status.getCode())
								progress.setProgressDescription("DIAMOND job #" + (i+1) + " is being executed");
						}
					} catch (Exception e) {
						String sMissingBankMsg = "Opening the database... No such file or directory";
						int nMsgPos = e.getMessage().indexOf(sMissingBankMsg);
						progress.setError(nMsgPos > -1 ? e.getMessage().substring(nMsgPos) + "\nPossible reasons: (1) bank creation is still running, (2) bank could not be created on HPC due to lack of disk space, (3) it was removed accidentally." : e.getMessage());
						dataTimer.shutdown();
					}
					
					if (completedJobCount.get() > 0 || pendingJobCount.get() == 0)
						progress.setProgressDescription("Running DIAMOND job(s)... " + completedJobCount.get() + " / " + projIdList.size() + " completed");
					if (completedJobCount.get() == projIdList.size()) {
						if (progress.getError() == null)
							progress.markAsComplete();
						dataTimer.shutdown();
					}
            	}
            };
            dataTimer.scheduleAtFixedRate(r, 5, 5, TimeUnit.SECONDS);
        }
        
        return result;
	}
	
	@Override
	public Collection<String> blast(String sModule, String banks, String program, String sequence, String expect, String align, ProgressIndicator progress) throws IOException {
	   	final Set<String> result = new HashSet<>();
        // write query to fasta 

        // parse expect/ align to make sure correct values are used
        double evalue = Double.parseDouble(expect);
        int alignement = Integer.parseInt(align);
        String basePath = appConfig.blastDbLocation();
        String suffix;
        // threads: number of CPU 
        final StringBuffer extraParameters = new StringBuffer(" -num_threads 2");

        switch (program) {
            case "blastn":
                suffix = NUCL_SUFFIX;
                extraParameters.append(" -dust yes -task blastn -strand both");
                break;
            case "blastp":
                suffix = PROT_SUFFIX;
                extraParameters.append(" -seg yes -matrix BLOSUM62 -task blastp");
                break;
            case "blastx":
                suffix = PROT_SUFFIX;
                extraParameters.append(" -strand both");
                break;
            case "tblastn":
                suffix = NUCL_SUFFIX;
                break;
            case "tblastx":
                suffix = NUCL_SUFFIX;
                extraParameters.append(" -strand both");
                break;
            default:
                suffix = null;
                break;
        }
        
        final String finalSequence = (sequence.startsWith(">") ? "" : ">query\n") + sequence.toLowerCase();

        String blastQueryHash = Helper.convertToMD5(finalSequence + MetaXplorController.PATH_SEPARATOR + program + MetaXplorController.PATH_SEPARATOR + expect + MetaXplorController.PATH_SEPARATOR + align);
        String querySeqPath = webappRootPath + MetaXplorController.TMP_OUTPUT_FOLDER + File.separator + blastQueryHash + ".fa";
        File fastaQuery = new File(querySeqPath);
        fastaQuery.getParentFile().mkdirs();
        if (fastaQuery.createNewFile()) {	// if file doesn't exist, create it
            LOG.debug("new query file created : " + querySeqPath);
        }
        try (FileOutputStream fastaStream = new FileOutputStream(fastaQuery, false)) {
            byte[] queryBytes = finalSequence.getBytes();
            fastaStream.write(queryBytes);
        }
        final List<Integer> projIdList = Arrays.asList(banks.split(";")).stream().map(id -> Integer.parseInt(id)).collect(Collectors.toList());
        final String[] jobIDs = new String[projIdList.size()];
        for (int i=0; i<projIdList.size(); i++) {
        	String blastResultId = projIdList.get(i) + "_" + blastQueryHash;
            result.add(blastResultId);
            Blast blastResult = MongoTemplateManager.get(sModule).findById(blastResultId, Blast.class);
            if (blastResult == null) {
                JobSubOutputType subOut = OpalJobInvoker.invokeNonBlocking(METAXPLOR_SERVICE_PREFIX + program,
                	"-db \"" + basePath + MetaXplorController.PATH_SEPARATOR + sModule + MetaXplorController.PATH_SEPARATOR + projIdList.get(i) + suffix + "\""
                	+ " -evalue " + expect
	                + " -num_alignments " + alignement + extraParameters.toString()
	                + " -out blast.out -outfmt 0 -query " + blastQueryHash + ".fa",
	                null, new String[] { querySeqPath }, 1, null);

                jobIDs[i] = subOut.getJobID();
            }
            else {
            	blastResult.getJobIDs().add(progress.getProcessId());
            	MongoTemplateManager.get(sModule).save(blastResult);
            }
        }

        int nLaunchedJobCount = 0;
        for (String jobID : jobIDs)
        	if (jobID != null)
        		nLaunchedJobCount++;
        if (nLaunchedJobCount == 0)
        	progress.markAsComplete();
        else
        { // monitor all job statuses
        	// create an executor service that runs in another thread, so the browser gets the hash strings immediately
            ScheduledExecutorService dataTimer = Executors.newScheduledThreadPool(1);
            Runnable r = () -> {
                AtomicInteger pendingJobCount = new AtomicInteger(), completedJobCount = new AtomicInteger(); 
            	for (int i=0; i<projIdList.size(); i++) {
					try {

						StatusOutputType status = checkJobStatus(program, jobIDs[i]);
		                checkIfJobHasFailed(status);

						if (OpalJobInvoker.STATUS_DONE == status.getCode()) {
							completedJobCount.incrementAndGet();
		            		LOG.debug("url : " + status.getBaseURL().toString());
		                    Blast blastResult = new Blast(projIdList.get(i) + "_" + blastQueryHash,
	                            status.getBaseURL().toString(),
	                            Calendar.getInstance().getTime(),
	                            evalue,
	                            alignement,
	                            finalSequence,
	                            program,
	                            projIdList.get(i));
		                    
		                    blastResult.getJobIDs().add(progress.getProcessId());
		                    MongoTemplateManager.get(sModule).save(blastResult);
						}
						else {
							if (OpalJobInvoker.STATUS_PENDING == status.getCode()) {
								pendingJobCount.incrementAndGet();
								progress.setProgressDescription("BLAST job #" + (i+1) + " is being queued");
							}
							else if (OpalJobInvoker.STATUS_ACTIVE == status.getCode())
								progress.setProgressDescription("BLAST job #" + (i+1) + " is being executed");
						}
					} catch (Exception e) {
						progress.setError(e.getMessage() + (e.getMessage().startsWith("BLAST Database error: No alias or index file found for") ? "\nPossible reasons: (1) bank creation is still running, (2) bank could not be created on HPC due to lack of disk space, (3) it was removed accidentally." : ""));
						dataTimer.shutdown();
					}
					
					if (completedJobCount.get() > 0 || pendingJobCount.get() == 0)
						progress.setProgressDescription("Running BLAST job(s)... " + completedJobCount.get() + " / " + projIdList.size() + " completed");
					if (completedJobCount.get() == projIdList.size()) {
						if (progress.getError() == null)
							progress.markAsComplete();
						dataTimer.shutdown();
					}
            	}
            };
            dataTimer.scheduleAtFixedRate(r, 5, 5, TimeUnit.SECONDS);
        }
        
        return result;
	}

	@Override
	public String phyloAssign(String sModule, String assignmentQueryHash, String mafftOption, ProgressIndicator progress) throws Exception {
       try {
//	        String uploadedFileLocation = webappRootURL + "/" + MetaXplorController.TMP_OUTPUT_FOLDER + "/" + assignmentQueryHash + "/";
	        String uploadedFolderPath = webappRootPath + MetaXplorController.TMP_OUTPUT_FOLDER + File.separator;

        	MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
        	if (mongoTemplate == null)
        		mongoTemplate = MongoTemplateManager.getCommonsTemplate();	// probably called with an external fasta

	    	PhylogeneticAssignment clusterPplacer = mongoTemplate.findById(assignmentQueryHash, PhylogeneticAssignment.class);
	        String jPlaceUrl;
	
	        if (clusterPplacer == null) { // pplacer AND guppy need to be launched
	        	progress.addStep("Aligning fasta with reference using mafft");
	        	progress.moveToNextStep();
	        	
	        	String mafftOpt = Arrays.asList("add", "addlong", "addfragments").contains(mafftOption) ? mafftOption : "addfragments";

				JobSubOutputType subOut = OpalJobInvoker.invokeNonBlocking(METAXPLOR_SERVICE_PREFIX + "mafft", mafftOpt + " sequences.fasta ref.fasta aligned_sequences.fasta",
						null,
	                	new String[] { uploadedFolderPath + "/" + assignmentQueryHash + "/sequences.fasta", uploadedFolderPath + "/" + assignmentQueryHash + "/ref.fasta" },
	                	1,
	                	null);
				
				String mafftJobId = subOut.getJobID();

                ScheduledExecutorService mafftJobDataTimer = Executors.newScheduledThreadPool(1);
                Runnable r = () -> {
					try {
						StatusOutputType mafftStatus = checkJobStatus("mafft", mafftJobId);
		                checkIfJobHasFailed(mafftStatus);

						if (OpalJobInvoker.STATUS_DONE == mafftStatus.getCode())
							mafftJobDataTimer.shutdown();
						else {
							try
							{
								BufferedReader input = new BufferedReader(new InputStreamReader(new URL(mafftStatus.getBaseURL().toString() + "/stdout.txt").openStream()));
								String inputLine, sDesc = null;
								while ((inputLine = input.readLine()) != null)
									if (inputLine != null)
										sDesc = inputLine;
								input.close();
								if (sDesc != null && !sDesc.isEmpty())
									progress.setProgressDescription("mafft: " + sDesc);
							}
							catch (FileNotFoundException ignored)
							{}
						}
					} catch (Exception e) {
						progress.setError(e.getMessage() != null ? e.getMessage() : e.toString());
						mafftJobDataTimer.shutdown();						
					}
                };
                mafftJobDataTimer.scheduleAtFixedRate(r, 5, 5, TimeUnit.SECONDS);

                while (!mafftJobDataTimer.isShutdown())
                	Thread.sleep(500);
                
                URI mafftJobBaseUrl = subOut.getStatus().getBaseURL();
                if (new URL(mafftJobBaseUrl + "/aligned_sequences.fasta").openConnection().getContentLength() == 0) {
                	LOG.error(mafftJobBaseUrl + "/aligned_sequences.fasta is empty");
					BufferedReader input = new BufferedReader(new InputStreamReader(new URL(mafftJobBaseUrl + "/stderr.txt").openStream()));
					String inputLine;
					while ((inputLine = input.readLine()) != null)
						if (inputLine != null && inputLine.contains("line 2440:") && inputLine.contains("Killed")) {
							progress.setError("mafft could complete due to a lack of memory. Please grant more memory to HPC or retry with fewer sequences");
							throw new Exception(progress.getError());
						}
                }
                
                progress.setProgressDescription(null);
				progress.addStep("Running pplacer");
				progress.moveToNextStep();

                LOG.info("Providing pplacer with " + mafftJobBaseUrl + "/1.refpkg.zip and " + mafftJobBaseUrl + "/aligned_sequences.fasta");
                subOut = OpalJobInvoker.invokeNonBlocking(METAXPLOR_SERVICE_PREFIX + "pplacer", "-c \"1.refpkg.zip\" \"aligned_sequences.fasta\" -o " + assignmentQueryHash + JPLACE_EXT,
                        new URI[] { new URI(mafftJobBaseUrl + "/aligned_sequences.fasta") },
                        new String[] { uploadedFolderPath + "/" + assignmentQueryHash + "/1.refpkg.zip" },
                        1,
                        null);
                
                String pplacerJobId = subOut.getJobID();
                
                ScheduledExecutorService pplacerJobDataTimer = Executors.newScheduledThreadPool(1);
                r = () -> {
					try {

						StatusOutputType pplacerStatus = checkJobStatus("pplacer", pplacerJobId);
		                checkIfJobHasFailed(pplacerStatus);

						if (OpalJobInvoker.STATUS_DONE == pplacerStatus.getCode())
							pplacerJobDataTimer.shutdown();
						else {
							try
							{
								BufferedReader input = new BufferedReader(new InputStreamReader(new URL(pplacerStatus.getBaseURL().toString() + "/stdout.txt").openStream()));
								String inputLine, sDesc = null;
								while ((inputLine = input.readLine()) != null)
									if (inputLine != null)
										sDesc = inputLine;
								input.close();
								if (sDesc != null && !sDesc.isEmpty())
									progress.setProgressDescription("pplacer: " + sDesc);
							}
							catch (FileNotFoundException ignored)
							{}
						}
					} catch (Exception e) {
						LOG.error(e);
						progress.setError(e.getMessage() != null ? e.getMessage() : e.toString());
						pplacerJobDataTimer.shutdown();						
					}
                };
                pplacerJobDataTimer.scheduleAtFixedRate(r, 5, 5, TimeUnit.SECONDS);

                while (!pplacerJobDataTimer.isShutdown())
                	Thread.sleep(500);
                
                progress.setProgressDescription(null);
                
                if (progress.getError() != null)
                	throw new Exception(progress.getError());
                
                clusterPplacer = new PhylogeneticAssignment(assignmentQueryHash, Calendar.getInstance().getTime(), subOut.getStatus().getBaseURL().toString());
                clusterPplacer.getJobIDs().add(progress.getProcessId());
            	mongoTemplate.save(clusterPplacer);
                jPlaceUrl = subOut.getStatus().getBaseURL().toString() + MetaXplorController.PATH_SEPARATOR + assignmentQueryHash + JPLACE_EXT;
	        }
	        else {
	            // results are already available (job with same parameters has been launched within the last 2 weeks)
	        	clusterPplacer.getJobIDs().add(progress.getProcessId());
	        	mongoTemplate.save(clusterPplacer);
	        	URL u = new URL(clusterPplacer.getOutputUrl() + MetaXplorController.PATH_SEPARATOR + assignmentQueryHash + ".xml"); 
	        	HttpURLConnection huc = (HttpURLConnection)u.openConnection(); 
	        	huc.setRequestMethod("GET"); 
	        	huc.connect(); 
	        	if (huc.getResponseCode() == HttpServletResponse.SC_OK) { // final XML result is available: no need to launch any job
	        		progress.markAsComplete();
	        		return u.toString();
	        	}

	        	// guppy still needs to be launched
	        	jPlaceUrl = clusterPplacer.getOutputUrl() + MetaXplorController.PATH_SEPARATOR + assignmentQueryHash + JPLACE_EXT;
	        }
        
			progress.addStep("Running guppy");
			progress.moveToNextStep();

	        BlockingOutputType subOut = OpalJobInvoker.invokeBlocking(METAXPLOR_SERVICE_PREFIX + "guppy", "\"1.refpkg.zip\" " + jPlaceUrl.substring(jPlaceUrl.lastIndexOf('/') + 1),
	                	new URI[] {new URI(jPlaceUrl)},
	                	new String[] { uploadedFolderPath + "/" + assignmentQueryHash + "/1.refpkg.zip" },
	                	1,
	                	null);
	        
//		        BlockingOutputType subOut = OpalJobInvoker.invokeBlocking(METAXPLOR_SERVICE_PREFIX + "guppy", "tog " + jPlaceUrl.substring(jPlaceUrl.lastIndexOf('/') + 1) + "--xml",
//			        	new URI[] {new URI(jPlaceUrl)},
//			        	new String[] { uploadedFolderPath + "/" + assignmentQueryHash + "/1.refpkg.zip" },
//	                	1,
//	                	null);
	        
	        checkIfJobHasFailed(subOut.getStatus());
	        
	        String guppyOutputUrl = subOut.getStatus().getBaseURL().toString();
	        clusterPplacer.setOutputUrl(guppyOutputUrl);
	        mongoTemplate.save(clusterPplacer);
	        
	        progress.markAsComplete();
	        return guppyOutputUrl + "/" + assignmentQueryHash + ".xml";
        }
        catch (IOException | NumberFormatException | InterruptedException e) {
            progress.setError("Job submission failed: " + e.getMessage());
//	            LOG.debug("job failed: ", e);
            throw e;
        }
	}
	
    /**
     * check the status of a job running on the cluster
     *
     * @param program the name of the launched program
     * @param jobID id of the job to monitor
     * @throws Exception
     * @return StatusOutputType status object
     */
    public StatusOutputType checkJobStatus(String program, final String jobId) throws Exception {
        StringBuilder error = new StringBuilder();
        StatusOutputType sot = OpalJobInvoker.getJobStatus(METAXPLOR_SERVICE_PREFIX + program, jobId);
        LOG.debug(new SimpleDateFormat(Constant.DATE_FORMAT_HHMMSS).format(new Date()) + " | " + jobId + " -> " + sot.getCode());

        if (sot.getCode() == OpalJobInvoker.STATUS_FAILED) {            
            try (BufferedReader input = new BufferedReader(new InputStreamReader(new URL(sot.getBaseURL().toString() + "/stderr.txt").openStream()))) {
            	 // if job failed, parse output to know the cause and send it back to the user
                String inputLine;
                while ((inputLine = input.readLine()) != null)
                    error.append(inputLine).append("\n");
                if (error.toString().trim().isEmpty()) { // try with stdout
                    try (BufferedReader input2 = new BufferedReader(new InputStreamReader(new URL(sot.getBaseURL().toString() + "/stdout.txt").openStream()))) {
                       error = new StringBuilder();
                       while ((inputLine = input2.readLine()) != null)
                           error.append(inputLine).append("\n");
                   }
                }
            }
            throw new Exception(error.toString());
        }        	
        return sot;
    }

    /**
     * Check if a job has failed according to its status
     *
     * @param status
     * @throws IOException
     */
    public void checkIfJobHasFailed(StatusOutputType status) throws IOException {
        int code = status.getCode();
        if (code != OpalJobInvoker.STATUS_PENDING && code != OpalJobInvoker.STATUS_ACTIVE && code != OpalJobInvoker.STATUS_DONE && code != OpalJobInvoker.STATUS_STAGE_IN && code != OpalJobInvoker.STATUS_STAGE_OUT) {
            throw new IOException("The execution of the OpalClient failed with the following error: "
                    + status.getMessage() + ". You can consult output and error file here: "
                    + status.getBaseURL() + "/stdout.txt, "
                    + status.getBaseURL() + "/stderr.txt");
        }
    }
}
