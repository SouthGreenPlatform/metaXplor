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
package fr.cirad.scheduler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.mongodb.client.result.UpdateResult;

import fr.cirad.metaxplor.importing.AccessionImport;
import fr.cirad.metaxplor.model.Accession;
import fr.cirad.metaxplor.model.AssignedSequence;
import fr.cirad.metaxplor.model.Blast;
import fr.cirad.metaxplor.model.DBField;
import fr.cirad.metaxplor.model.PhylogeneticAssignment;
import fr.cirad.metaxplor.model.Taxon;
import fr.cirad.tools.mongo.DBConstant;
import fr.cirad.tools.mongo.MongoTemplateManager;
import fr.cirad.tools.opal.OpalServiceLauncher;
import fr.cirad.web.controller.metaxplor.MetaXplorController;

/**
 * Class for periodic cleanup
 *
 * @author petel, sempere
 */
@Configuration
@EnableScheduling
public class ScheduledTaskManager {

    @Autowired ServletContext servletContext = null;
    @Autowired private AccessionImport accessionImport;

    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(ScheduledTaskManager.class);
    
    private OpalServiceLauncher opalServiceLauncher = null;
    private String refPkgFolderPath = null;

    public void enableRefPkgInspection(HttpServletRequest request) throws UnknownHostException, SocketException {
		opalServiceLauncher = new OpalServiceLauncher(request);
		refPkgFolderPath = request.getRealPath(MetaXplorController.REF_PKG_FOLDER);
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(3);
        ses.scheduleAtFixedRate(	() -> { inspectRefPackages(); },
        							0 /* launch now */,
        							120 /* run every 2 hours */
        							, TimeUnit.MINUTES
        						);
	}

	public boolean isRefPkgInspectionEnabled() {
		return opalServiceLauncher != null && refPkgFolderPath != null;
	}

	/**
     * runs every 6 hours, deletes expired exported files
     */
    @Scheduled(fixedRate = 1000 * 60 * 60 * 6)
    public void cleanupTempOutputFiles() {
        LOG.debug("cleaning contents of " + MetaXplorController.TMP_OUTPUT_FOLDER + " directory in the webapp");
        Calendar calendar = Calendar.getInstance();
        String path = servletContext.getRealPath(MetaXplorController.TMP_OUTPUT_FOLDER);
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            try {
                for (File file : listOfFiles) {
                    BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    long creationTime = attr.lastModifiedTime().toMillis();
                    // if the file was created more than 48 hours ago, delete it 
                    if (calendar.getTimeInMillis() - creationTime > (1000 * 60 * 60 * 48)) {
                    	if (!file.isDirectory()) {
                    		if (file.delete()) 
                    			LOG.debug("temporary file deleted: " + path + "/" + file.getName());                    		
                    	}
                    	else {
	                    	FileUtils.deleteDirectory(file);
	                    	if (!file.exists())
	                    		LOG.debug("temporary folder deleted: " + path + "/" + file.getName());
                    	}
                    }
                }
            } catch (IOException ex) {
                LOG.debug("cleaning query dir failed: ", ex);
            }
        }
    }

    /**
     * runs every 20 hours, deletes expired job data
     */
    @Scheduled(fixedRate = 1000 * 60  * 60 * 20)
    public void cleanupJobData() {
        LOG.debug("cleaning old blast and phylo-assign jobs");
        Calendar calendar = Calendar.getInstance();
        Date expirationDate =  new Date(calendar.getTime().getTime() - (1000 * 60 * 60 * 48));	// if records were created more than 48 hours ago, delete them 
        
        Query query = new Query(Criteria.where(PhylogeneticAssignment.FIELDNAME_CREATED_TIME).lt(expirationDate));
        long nRemoved = MongoTemplateManager.getCommonsTemplate().remove(query, PhylogeneticAssignment.class).getDeletedCount();
    	if (nRemoved > 0)
    		LOG.debug(nRemoved + " old PhyloAssign records removed from commons database");
    	
        for (String module : MongoTemplateManager.getAvailableModules())
        {
        	MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        	
        	query = new Query(Criteria.where(Blast.FIELDNAME_CREATED_TIME).lt(expirationDate));
        	nRemoved = mongoTemplate.remove(query, Blast.class).getDeletedCount();
        	if (nRemoved > 0)
        		LOG.debug(nRemoved + " old Blast records removed from database " + module);
        	query = new Query(Criteria.where(PhylogeneticAssignment.FIELDNAME_CREATED_TIME).lt(expirationDate));
        	nRemoved = mongoTemplate.remove(query, PhylogeneticAssignment.class).getDeletedCount();
        	if (nRemoved > 0)
        		LOG.debug(nRemoved + " old PhyloAssign records removed from database " + module);
        }
    }

    /**
     * runs every hour, attempts to call WS again for missing tax IDs
     */
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void retryFailedAccessionRequests() {
        for (String module : MongoTemplateManager.getAvailableModules()) { // try and fix taxids that are not known in our taxonomy cache
        	MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
        	
        	List<Integer> usedTaxa = mongoTemplate.findDistinct(new Query(), AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId, AssignedSequence.class, Number.class).stream().map(id -> id.intValue()).collect(Collectors.toList());
        	List<Integer> knownUsedTaxa = MongoTemplateManager.getCommonsTemplate().findDistinct(new Query(Criteria.where("_id").in(usedTaxa)), "_id", Taxon.class, Integer.class);

        	if (usedTaxa.size() != knownUsedTaxa.size()) {	// this module refers to taxids that are missing from the taxon collection
        		usedTaxa.removeAll(knownUsedTaxa);	// from now on it only contains the problematic IDs
        		UpdateResult wr = mongoTemplate.updateMulti(new Query(Criteria.where(AssignedSequence.FIELDNAME_ASSIGNMENT + "." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId).in(usedTaxa)),
        													new Update().unset(AssignedSequence.FIELDNAME_ASSIGNMENT + ".$[elm]." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId).filterArray(Criteria.where("elm." + DBConstant.DOUBLE_TYPE + "." + DBField.taxonFieldId).in(usedTaxa)),
        													AssignedSequence.class);
        		LOG.debug("Removed taxid for " + wr.getModifiedCount() + " assigned sequences in database " + module);
        		
        		
        		wr = MongoTemplateManager.getCommonsTemplate().updateMulti(new Query(Criteria.where(Accession.FIELDNAME_NCBI_TAXID).in(usedTaxa)), new Update().unset(Accession.FIELDNAME_NCBI_TAXID), Accession.class);
        		LOG.debug("Removed taxid for " + wr.getModifiedCount() + " cached accessions");
        	}
        }

    	LOG.debug("Checking for failed accession requests");
    	accessionImport.retryFailedAccessionRequests(true, 20000);
    }

	/**
     * inspects newly found reference packages ; not scheduled via annotation because it requires a first http request to have been made
     */
    private void inspectRefPackages() {
    	if (!isRefPkgInspectionEnabled())
    		return;

       LOG.debug("checking contents of " + MetaXplorController.REF_PKG_FOLDER + " directory in the webapp");

       File folder = new File(refPkgFolderPath);
       File[] listOfFiles = folder.listFiles();
       if (listOfFiles != null) {
    	   for (File file : listOfFiles)
    		   if (MongoTemplateManager.getCommonsTemplate().count(new Query(Criteria.where("_id").is(file.getName())), HashMap.class, PhylogeneticAssignment.REF_PKG_COLL_NAME) == 0) {
    			   Thread t = new Thread() {
    				   @Override
    				   public void run() {
    					   	try {
    					   		String resultBaseUrl = opalServiceLauncher.inspectRefPackage(file.getName());
    					   		HashMap<String, String> refPkgInfo = new HashMap<>();
    					   		refPkgInfo.put("_id", file.getName());
    					   		StringBuffer desc = new StringBuffer();
    					   		try {
    					   			desc.append(IOUtils.toString(new URL(resultBaseUrl + "/" + FilenameUtils.getBaseName(file.getName()) + "/README")) + "\n");
    					   		}
    					   		catch (FileNotFoundException ignored)
    					   		{}
    					   		String summary = IOUtils.toString(new URL(resultBaseUrl + "/summary.txt"));
    					   		desc.append(summary.substring(Math.max(0, summary.indexOf("Tree with "))));
    					   		String description = desc.toString();
    					   		while (description.startsWith("\n"))
    					   			description = description.substring(1);
    					   		refPkgInfo.put(PhylogeneticAssignment.REF_PKG_DESC_FIELD_NAME, description);
    					   		refPkgInfo.put(PhylogeneticAssignment.REF_PKG_KRONA_FIELD_NAME, IOUtils.toString(new URL(resultBaseUrl + "/text.krona.html")));
    					   		MongoTemplateManager.getCommonsTemplate().save(refPkgInfo, PhylogeneticAssignment.REF_PKG_COLL_NAME);
    					   	} catch (IOException e) {
								LOG.error("Unable to inspect refpkg " + file.getName(), e);
							}
    				   }
    			   };
    			   t.start();
    		   }
       }
    }
}
