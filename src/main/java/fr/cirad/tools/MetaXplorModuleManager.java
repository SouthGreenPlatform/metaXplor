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
package fr.cirad.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import fr.cirad.manager.IModuleManager;
import fr.cirad.manager.dump.DumpMetadata;
import fr.cirad.manager.dump.DumpProcess;
import fr.cirad.manager.dump.DumpStatus;
import fr.cirad.manager.dump.IBackgroundProcess;
import fr.cirad.metaxplor.importing.MtxImport;
import fr.cirad.metaxplor.model.AssignedSequence;
import fr.cirad.metaxplor.model.Blast;
import fr.cirad.metaxplor.model.DBField;
import fr.cirad.metaxplor.model.DatabaseInformation;
import fr.cirad.metaxplor.model.MetagenomicsProject;
import fr.cirad.metaxplor.model.Sample;
import fr.cirad.metaxplor.model.Sequence;

import fr.cirad.security.ReloadableInMemoryDaoImpl;

import fr.cirad.security.base.IRoleDefinition;
import fr.cirad.tools.mongo.DBConstant;
import fr.cirad.tools.mongo.MongoTemplateManager;
import fr.cirad.tools.opal.OpalServiceLauncher;

/**
 * @author sempere
 *
 */
@Component
public class MetaXplorModuleManager implements IModuleManager {

	static public final String ENTITY_PROJECT = "project";
	static public final String ROLE_READER = "READER";
	
	@Autowired private ApplicationContext appContext;
    @Autowired private ServletContext servletContext;
    
	private static final Logger LOG = Logger.getLogger(MetaXplorModuleManager.class);
	
	@Autowired private AppConfig appConfig;
	
    private static final String defaultDumpFolder = DumpProcess.dumpManagementPath + "/dumps";

    private String actionRequiredToEnableDumps = null;
    
    @Autowired
    private ReloadableInMemoryDaoImpl userDao;
    
    @Override
    public Collection<String> getModules(Boolean fTrueForPublicFalseForPrivateNullForBoth) {
        if (fTrueForPublicFalseForPrivateNullForBoth == null) {
            return MongoTemplateManager.getAvailableModules();
        }
        if (Boolean.TRUE.equals(fTrueForPublicFalseForPrivateNullForBoth)) {
            return MongoTemplateManager.getPublicDatabases();
        }
        return CollectionUtils.disjunction(MongoTemplateManager.getAvailableModules(), MongoTemplateManager.getPublicDatabases());
    }

    @Override
    public Map<String, Map<Comparable, String[]>> getEntitiesByModule(String entityType, Boolean fTrueIfPublicFalseIfPrivateNullIfAny, Collection<String> modules, boolean fIncludeEntityDescriptions) throws Exception {
        Map<String, Map<Comparable, String[]>> entitiesByModule = new LinkedHashMap<>();
        if (ENTITY_PROJECT.equals(entityType)) {
            Query q = new Query();
            q.with(Sort.by(Arrays.asList(new Sort.Order(Sort.Direction.ASC, "_id"))));
            q.fields().include(MetagenomicsProject.FIELDNAME_NAME).include(MetagenomicsProject.FIELDNAME_PUBLIC);
            if (fIncludeEntityDescriptions)
            	q.fields().include(MetagenomicsProject.FIELDNAME_DESCRIPTION);

            for (String sModule : modules != null ? modules : MongoTemplateManager.getAvailableModules())
                if (fTrueIfPublicFalseIfPrivateNullIfAny == null || (MongoTemplateManager.isModulePublic(sModule) == fTrueIfPublicFalseIfPrivateNullIfAny)) {
                    Map<Comparable, String[]> moduleEntities = entitiesByModule.get(sModule);
                    if (moduleEntities == null) {
                        moduleEntities = new LinkedHashMap<>();
                        entitiesByModule.put(sModule, moduleEntities);
                    }

                    for (MetagenomicsProject project : MongoTemplateManager.get(sModule).find(q, MetagenomicsProject.class)) {
                    	String[] projectInfo = new String[fIncludeEntityDescriptions ? 2 : 1];
                    	projectInfo[0] = project.getName();
                    	if (fIncludeEntityDescriptions)
                    		projectInfo[1] = project.getDescription();
                        moduleEntities.put(project.getId(), projectInfo);
                    }
                }
        }
        else
            throw new Exception("Not managing entities of type " + entityType);

        return entitiesByModule;
    }

    @Override
    public boolean removeDataSource(String module, boolean fAlsoDropDatabase, boolean fRemoveDumps) throws IOException {
        if (fRemoveDumps)
            try {
                FileUtils.deleteDirectory(new File(getDumpPath(module)));
            } catch (IOException e) {
                LOG.warn("Error removing dumps while deleting database " + module, e);
            }

        // delete sequence folder on webserver
        File[] fastaFileFolder = new File(appConfig.sequenceLocation() + File.separator + module + File.separator).listFiles();
        
        if (((Number) MongoTemplateManager.get(module).getDb().runCommand(new BasicDBObject("dbStats", 1)).get("dataSize")).intValue() > 0 || (fastaFileFolder != null && fastaFileFolder.length > 0)) { // if it looks like an empty db we don't bother calling Opal WS        
	        if (fastaFileFolder != null)
		        for (File f : fastaFileFolder)
		        	f.delete();        
	    	new OpalServiceLauncher().cleanupDbFiles(module);
        }
        return MongoTemplateManager.removeDataSource(module, fAlsoDropDatabase);
    }

    @Override
    public boolean updateDataSource(String sModule, boolean fPublic, boolean fHidden, String sSpeciesName) throws Exception {
        return MongoTemplateManager.saveOrUpdateDataSource(MongoTemplateManager.ModuleAction.UPDATE_STATUS, sModule, fPublic, fHidden, null, null, null);
    }

    @Override
    public boolean createDataSource(String sModule, String sHost, String sSpeciesName, Long expiryDate) throws Exception {
        return MongoTemplateManager.saveOrUpdateDataSource(MongoTemplateManager.ModuleAction.CREATE, sModule, false, false, sHost, null, expiryDate);
    }

    @Override
    public Collection<String> getHosts() {
        return MongoTemplateManager.getHostNames();
    }

    @Override
    public String getModuleHost(String sModule) {
        return MongoTemplateManager.getModuleHost(sModule);
    }

    @Override
    public boolean isModuleHidden(String sModule) {
        return MongoTemplateManager.isModuleHidden(sModule);
    }

    @Override
    public boolean removeManagedEntity(String module, String sEntityType, Collection<Comparable> entityIDs) throws Exception {
    	if (ENTITY_PROJECT.equals(sEntityType)) {
    		final int nProjectIdToRemove = Integer.parseInt(entityIDs.iterator().next().toString());
            MongoTemplate mongoTemplate = MongoTemplateManager.get(module);

            MetagenomicsProject project = mongoTemplate.findById(nProjectIdToRemove, MetagenomicsProject.class);
            String sProjectDesc = nProjectIdToRemove + (project == null ? "" : (" (" + project.getAcronym() + ")"));
            
            // clear project sequence and sample data 
           	new Thread() {
           		public void run() {
                    DeleteResult wr = mongoTemplate.remove(new Query(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(nProjectIdToRemove)), Sequence.class);
                    if (wr.getDeletedCount() > 0)
                    	LOG.debug(wr.getDeletedCount() + " unassigned sequences removed while deleting project " + sProjectDesc + " from database " + module);           			
           		}
           	}.start();
            DeleteResult wr = mongoTemplate.remove(new Query(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(nProjectIdToRemove)), AssignedSequence.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " assigned sequences removed while deleting project " + sProjectDesc + " from database " + module);
            UpdateResult ur = mongoTemplate.updateMulti(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(nProjectIdToRemove)), new Update().pull(DBConstant.FIELDNAME_PROJECT, nProjectIdToRemove), Sample.class);
            if (ur.getModifiedCount() > 0)
            	LOG.debug(ur.getModifiedCount() + " samples updated while deleting project " + sProjectDesc + " from database " + module);
            wr = mongoTemplate.remove(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).size(0)), Sample.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " samples removed while deleting project " + sProjectDesc + " from database " + module);
            wr = mongoTemplate.remove(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(nProjectIdToRemove)), Blast.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " blast results removed while deleting project " + sProjectDesc + " from database " + module);
                        
            // clear cache collections
            for (String collName : mongoTemplate.getCollectionNames()) {
                if (collName.startsWith(DBConstant.CACHE_PREFIX)) {
                	DBField dbField = mongoTemplate.findById(Integer.parseInt(collName.substring(collName.lastIndexOf("_") + 1)), DBField.class);
                	if (dbField != null && (DBConstant.DOUBLE_TYPE.equals(dbField.getType()) || DBConstant.DATE_TYPE.equals(dbField.getType())))
                		wr = mongoTemplate.remove(new Query(Criteria.where("_id").is(nProjectIdToRemove)), collName);	// this kind of cache collection holds one record per project
                	else {
    	                // '_id' of records part of this project only  
    	                List<Comparable> singleProjectRecord = new ArrayList<>();
    	                // '_id' of records part of this project and one or more other projects
    	                List<Comparable> multiProjectRecord = new ArrayList<>();
    	                BasicDBObject m = new BasicDBObject("$match", new BasicDBObject(DBConstant.FIELDNAME_PROJECT, nProjectIdToRemove));
    	                BasicDBObject p = new BasicDBObject("$project", new BasicDBObject("ln", new BasicDBObject("$size", "$pj")));
    	                List<BasicDBObject> pipeline = Arrays.asList(m, p);
    	                MongoCursor<org.bson.Document> cursor = mongoTemplate.getCollection(collName).aggregate(pipeline).allowDiskUse(true).iterator();
    	                if (cursor != null && cursor.hasNext()) {
    	                    while (cursor.hasNext()) {
    	                    	org.bson.Document record = cursor.next();
    	                        if ((int) record.get("ln") == 1) {
    	                            singleProjectRecord.add((Comparable) record.get("_id"));
    	                        } else {
    	                            multiProjectRecord.add((Comparable) record.get("_id"));
    	                        }
    	                    }
    	                }
    	                if (!singleProjectRecord.isEmpty())
    	                	wr = mongoTemplate.remove(new Query(Criteria.where("_id").in(singleProjectRecord)), collName);	// remove records that are only part of this project    	                
    	                if (!multiProjectRecord.isEmpty()) {	// pull the projectId from other records
	    	                Query query = new Query(Criteria.where("_id").in(multiProjectRecord));
	    	                Update update = new Update().pull(DBConstant.FIELDNAME_PROJECT, nProjectIdToRemove);
	    	                ur = mongoTemplate.updateMulti(query, update, collName);
    	                }
                	}
                    if (mongoTemplate.getCollection(collName).countDocuments() == 0)
                    	mongoTemplate.dropCollection(collName);
                }
            }
            
            // delete sequence files on webserver
            File fastaFile = new File(appConfig.sequenceLocation() + File.separator + module + File.separator + nProjectIdToRemove + Sequence.NUCL_FASTA_EXT), indexFile = new File(fastaFile.getAbsolutePath() + Sequence.NUCL_FAI_EXT), lightIndexFile = new File(fastaFile.getParent() + File.separator + "_" + fastaFile.getName() + Sequence.NUCL_FAI_EXT);
            fastaFile.delete();
            indexFile.delete();
            lightIndexFile.delete();

            // delete blast-db files on cluster
            try {
            	new OpalServiceLauncher().cleanupProjectFiles(module, nProjectIdToRemove);
            }
            catch (Exception e) {
            	LOG.warn(e);
            }

            // remove the project from project collection
            mongoTemplate.remove(project);
            
            if (mongoTemplate.count(new Query(), MetagenomicsProject.class) == 0)
            	mongoTemplate.getDb().drop(); // remove whole db if it contains no more project
            else {
            	ur = mongoTemplate.updateMulti(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(nProjectIdToRemove)), new Update().pull(DBConstant.FIELDNAME_PROJECT, nProjectIdToRemove), DBField.class);
                if (ur.getModifiedCount() > 0)
                	LOG.debug(ur.getModifiedCount() + " dbField records updated while deleting project " + sProjectDesc + " from database " + module);
            }
            	
            // index cleanup
            Helper.removeObsoleteIndexes(module);

            return true;
    	}
		else
			throw new Exception("Not managing entities of type " + sEntityType);
    }

    @Override
    public boolean doesEntityExistInModule(String sModule, String sEntityType, Comparable entityId) {
        return MongoTemplateManager.doesModuleContainProject(sModule, Integer.parseInt(entityId.toString()));
    }

    @Override
    public boolean doesEntityTypeSupportVisibility(String sModule, String sEntityType) {
        return true; 
    }

    @Override
    public boolean setManagedEntityVisibility(String sModule, String sEntityType, Comparable entityId, boolean fPublic) throws Exception {
       return MongoTemplateManager.updateVisibility(sModule, Integer.parseInt(entityId.toString()), fPublic);
    }
    
    /**
     * return readable modules a given Authentication instance
     *
     * @return List<String> readable modules
     */
    public Collection<String> listReadableDBs(Authentication authentication, String selectedModule)
    {
    	Map<String, Map<String, Map<String, Collection<Comparable>>>> customRolesByModuleAndEntityType = userDao.getCustomRolesByModuleAndEntityType(authentication.getAuthorities());
    	Map<String, Map<String, Collection<Comparable>>> managedEntitiesByModuleAndType = userDao.getManagedEntitiesByModuleAndType(authentication.getAuthorities());
		Collection<String> modules = MongoTemplateManager.getAvailableModules(), authorizedModules = new ArrayList<String>();
		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new SimpleGrantedAuthority(IRoleDefinition.ROLE_ADMIN));

		for (String module : modules)
		{
			boolean fHiddenModule = MongoTemplateManager.isModuleHidden(module);
			boolean fPublicModule = MongoTemplateManager.isModulePublic(module);
			boolean fAuthorizedUser = fAuthentifiedUser && (customRolesByModuleAndEntityType.get(module) != null || managedEntitiesByModuleAndType.get(module) != null);
			if (fAdminUser || ((!fHiddenModule || module.equals(selectedModule)) && (fAuthorizedUser || fPublicModule || (fAuthentifiedUser && MongoTemplateManager.get(module).count(new Query(Criteria.where(MetagenomicsProject.FIELDNAME_PUBLIC).is(true)), MetagenomicsProject.class) > 0))))
				authorizedModules.add(module);
		}
        return authorizedModules;
    }

    /**
     * return writable modules a given Authentication instance
     *
     * @return List<String> readable modules
     */
    public Collection<String> listWritableDBs(Authentication authentication) {
		Collection<String> modules = MongoTemplateManager.getAvailableModules(), authorizedModules = new ArrayList<String>();
		Map<String, Collection<String>> writableEntityTypesByModule = userDao.getWritableEntityTypesByModule(authentication.getAuthorities());
		Map<String, Map<String, Collection<Comparable>>> managedEntitiesByModuleAndType = userDao.getManagedEntitiesByModuleAndType(authentication.getAuthorities());
		for (String module : modules)
		{
			boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
			boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new SimpleGrantedAuthority(IRoleDefinition.ROLE_ADMIN));
			Collection<String> writableEntityTypes = writableEntityTypesByModule.get(module);
			boolean fAuthorizedUser = fAuthentifiedUser && ((writableEntityTypes != null && writableEntityTypes.contains(ENTITY_PROJECT)) || managedEntitiesByModuleAndType.get(module) != null);
			if (fAdminUser || (fAuthorizedUser))
				authorizedModules.add(module);
		}
        return authorizedModules;
    }
    
    public boolean canUserCreateProjectInDB(Authentication authentication, String module) 
    {
    	if (authentication == null)
    		return false;

		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new SimpleGrantedAuthority(IRoleDefinition.ROLE_ADMIN));
		Collection<String> writableEntityTypes = userDao.getWritableEntityTypesByModule(authentication.getAuthorities()).get(module);
        if (fAdminUser || (fAuthentifiedUser && ((writableEntityTypes != null && writableEntityTypes.contains(ENTITY_PROJECT)))))
            return true;
        return false;
    }

	public boolean canUserWriteToProject(Authentication authentication, String sModule, int projectId)
	{
		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		if (fAuthentifiedUser && authentication.getAuthorities().contains(new SimpleGrantedAuthority(IRoleDefinition.ROLE_ADMIN)))
			return true;
		
		if ("anonymousUser".equals(authentication.getPrincipal()))
			return false;
		
		Map<String, Collection<Comparable>> managedEntitesByType = userDao.getManagedEntitiesByModuleAndType(authentication.getAuthorities()).get(sModule);
		if (managedEntitesByType != null)
		{
			Collection<Comparable> managedProjects = managedEntitesByType.get(ENTITY_PROJECT);
			if (managedProjects != null && managedProjects.contains(projectId))
				return true;

		}
		return false;
	}

	public boolean canUserReadProject(Authentication authentication, String sModule, Comparable projectId)
	{
		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		if (fAuthentifiedUser && authentication.getAuthorities().contains(new SimpleGrantedAuthority(IRoleDefinition.ROLE_ADMIN)))
			return true;

		MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
		boolean fPublicModule = MongoTemplateManager.isModulePublic(sModule);

		MetagenomicsProject project = mongoTemplate.findById(projectId, MetagenomicsProject.class);
		if (project.isPublicProject())
			return fAuthentifiedUser || fPublicModule;
		
		if (!fAuthentifiedUser)
			return false;

		// if we get there then user is logged in and project is private: look for specific permissions
		Map<String, Map<String, Collection<Comparable>>> customRolesByEntityType = userDao.getCustomRolesByModuleAndEntityType(authentication.getAuthorities()).get(sModule);
		if (customRolesByEntityType != null)
		{
			Map<String, Collection<Comparable>> customRolesOnProjects = customRolesByEntityType.get(ENTITY_PROJECT);
			if (customRolesOnProjects != null)
			{
				Collection<Comparable> projectCustomRoles = customRolesOnProjects.get(ROLE_READER);
				if (projectCustomRoles != null && projectCustomRoles.contains(projectId))
					return true;
			}
		}
		
		Map<String, Collection<Comparable>> managedEntitesByType = userDao.getManagedEntitiesByModuleAndType(authentication.getAuthorities()).get(sModule);
		if (managedEntitesByType != null)
		{
			Collection<Comparable> managedProjects = managedEntitesByType.get(ENTITY_PROJECT);
			if (managedProjects != null && managedProjects.contains(projectId))
				return true;

		}
		return false;
	}
	
	/**
	 * @param module database to run cleanup on
	 */
	public void cleanupOrphanSequences(String module) {
		MongoTemplate mongoTemplate = MongoTemplateManager.get(module);
		List<Integer> projectIDsToKeep = mongoTemplate.findDistinct(new Query(), "_id", MetagenomicsProject.class, Integer.class);
		Collection<Integer> currentlyImportedProjects = MtxImport.getCurrentlyImportedProjectsForModule(module);
		if (currentlyImportedProjects != null && !currentlyImportedProjects.isEmpty())
			projectIDsToKeep.addAll(currentlyImportedProjects);
		Query q = new Query(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).not().in(new HashSet<>(projectIDsToKeep)));
        DeleteResult wr = mongoTemplate.remove(q, AssignedSequence.class);
        if (wr.getDeletedCount() > 0)
        	LOG.warn(wr.getDeletedCount() + " orphan assigned sequences removed from database " + module);
        wr = mongoTemplate.remove(q, Sequence.class);
        if (wr.getDeletedCount() > 0)
        	LOG.warn(wr.getDeletedCount() + " orphan unassigned sequences removed from database " + module);
	}
	
	// TODO: METHODS BELOW ARE PLAIN COPIES OF GIGWA CODE ===> HOW TO AVOID DUPLICATON??

    String getDumpPath(String sModule) {
        String dumpBase = appConfig.get("dumpFolder");
        if (dumpBase == null)
            dumpBase = servletContext.getRealPath("") + defaultDumpFolder;

        String dumpPath = dumpBase + File.separator + sModule;
        return dumpPath;
    }    
    
    @Override public void cleanupDb(String sModule) {
    	if (!MongoTemplateManager.isModuleAvailableForWriting(sModule)) {
    		LOG.warn("cleanupDb execution skipped because database " + sModule + " is locked for writing");
    		return;
    	}
    	
    	LOG.warn("TODO: nyi");
    	
//    	MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
//		if (mongoTemplate.findOne(new Query(), MetagenomicsProject.class) == null) {
//			mongoTemplate.dropCollection(VariantRunData.class);
//			LOG.info("Dropped VariantRunData collection subsequently because no project in database " + sModule);
//		}
//		
//        if (mongoTemplate.findOne(new Query(), VariantRunData.class) == null && AbstractGenotypeImport.doesDatabaseSupportImportingUnknownVariants(sModule))
//        {	// if there is no genotyping data left and we are not working on a fixed list of variants then any other data is irrelevant
//            mongoTemplate.getDb().drop();
//            LOG.info("Dropped database " + sModule + " which contained no genotypes and is not configured for working with a fixed list of variants");
//        }
    }
    

    @Override
    public String getActionRequiredToEnableDumps() {
        if (actionRequiredToEnableDumps == null) {
            String dumpFolder = appConfig.get("dumpFolder");
            if (dumpFolder == null)
                actionRequiredToEnableDumps = "specify a value for dumpFolder in config.properties (webapp should reload automatically)";
            else if (Files.isDirectory(Paths.get(dumpFolder))) {
                try {
                    String commandPrefix = System.getProperty("os.name").toLowerCase().startsWith("win") ? "cmd.exe /c " : "";

                    Process p = Runtime.getRuntime().exec(commandPrefix + "mongodump --help"); // will throw an exception if command is not on the path if running Linux (but not if running Windows)
                    Charset defaultCharset = java.nio.charset.Charset.defaultCharset();
                    IOUtils.toString(p.getInputStream(), defaultCharset); // necessary otherwise the Thread hangs...
                    String stdErr = IOUtils.toString(p.getErrorStream(), defaultCharset);
                    if (!stdErr.isEmpty())
                        throw new IOException(stdErr);

                    p = Runtime.getRuntime().exec(commandPrefix + "mongorestore --help"); // will throw an exception if command is not on the path if running Linux (but not if running Windows)
                    IOUtils.toString(p.getInputStream(), defaultCharset); // necessary otherwise the Thread hangs...
                    stdErr = IOUtils.toString(p.getErrorStream(), defaultCharset);
                    if (!stdErr.isEmpty())
                        throw new IOException(stdErr);

                    actionRequiredToEnableDumps = ""; // all seems OK
                } catch (IOException ioe) {
                    LOG.error("error checking for mongodump presence: " + ioe.getMessage());
                    actionRequiredToEnableDumps = "install MongoDB Command Line Database Tools (then restart application-server)";
                }
            }
            else
                actionRequiredToEnableDumps = new File(dumpFolder).mkdirs() ? "" : "grant app-server write permissions on folder " + dumpFolder + " (then reload webapp)";
        }
        return actionRequiredToEnableDumps;
    }

    @Override
    public List<DumpMetadata> getDumps(String sModule) {
        return getDumps(sModule, true);
    }

    public List<DumpMetadata> getDumps(String sModule, boolean withDescription) {
        DatabaseInformation dbInfo = MongoTemplateManager.getDatabaseInformation(sModule);
        String dumpPath = this.getDumpPath(sModule);

        // List files in the database's dump directory, filter out subdirectories and logs
        File[] fileList = new File(dumpPath).listFiles();
        if (fileList != null) {
            ArrayList<DumpMetadata> result = new ArrayList<DumpMetadata>();
            for (File file : fileList) {
                String filename = file.getName();
                if (filename.endsWith(".gz") && !filename.endsWith(".log.gz")) {
                    String extensionLessFilename = filename.substring(0, filename.lastIndexOf('.'));
                    if (!extensionLessFilename.contains("__")) {
                        if (withDescription)
                            LOG.warn("Ignoring archive " + file.getName() + " found in dump folder for database " + sModule + " (wrong naming structure)");
                        continue;
                    }

                    Date creationDate;
                    long fileSizeMb;
                    boolean fRecentlyModified;
                    try {
                        BasicFileAttributes fileAttr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                        creationDate = Date.from(fileAttr.creationTime().toInstant());
                        fileSizeMb = fileAttr.size();
                        fRecentlyModified = System.currentTimeMillis() - fileAttr.lastModifiedTime().toMillis() < 5000;
                    } catch (IOException e) {
                        LOG.error("File attributes unreadable for dump " + filename, e);
                        continue;
                    }

                    String description = null;
                    if (withDescription) {
                        try {
                            description = FileUtils.readFileToString(new File(dumpPath + "/" + extensionLessFilename + "__description.txt"));
                        } catch (IOException ignored) {}
                    }

                    DumpStatus validity;

                    // No last modification date set : default to valid ?
                    if (dbInfo == null) {
                        validity = (!isModuleAvailableForDump(sModule) && fRecentlyModified) ? DumpStatus.BUSY : DumpStatus.VALID;
                        // creationDate < lastModification : outdated
                    } else if (creationDate.compareTo(dbInfo.getLastModification()) < 0) {
                        validity = DumpStatus.OUTDATED;
                        // The last modification was a dump restore, and this dump is more recent than the restored dump
                    } else if (creationDate.compareTo(dbInfo.getLastModification()) > 0 && dbInfo.getRestoreDate() != null && creationDate.compareTo(dbInfo.getRestoreDate()) < 0) {
                        validity = DumpStatus.DIVERGED;
                    } else {
                        validity = DumpStatus.VALID;
                    }

                    result.add(new DumpMetadata(extensionLessFilename, extensionLessFilename.split("__")[1], creationDate, fileSizeMb, description == null ? "" : description, validity));
                }
            }
            return result;
        } else { // The database dump directory does not exist
            return new ArrayList<DumpMetadata>();
        }
    }

    @Override
    public DumpStatus getDumpStatus(String sModule) {
        if (!isModuleAvailableForDump(sModule))
            return DumpStatus.BUSY;

        DumpStatus result = DumpStatus.NONE;
        for (DumpMetadata metadata : getDumps(sModule, false)) {
            if (metadata.getValidity().validity > result.validity)
                result = metadata.getValidity();
        }

        return result;
    }

    @Override
    public IBackgroundProcess startDump(String sModule, String dumpName, String sDescription) {
        String sHost = this.getModuleHost(sModule);
        String credentials = this.getHostCredentials(sHost);
        String databaseName = MongoTemplateManager.getDatabaseName(sModule);
        String outPath = this.getDumpPath(sModule);

        new File(outPath).mkdirs();

        DumpProcess process = new DumpProcess(this, sModule, databaseName, MongoTemplateManager.getServerHosts(sHost), servletContext.getRealPath(""), outPath);

        String fileName = databaseName + "__" + dumpName;
        process.startDump(fileName, credentials);

        if (sDescription != null && !sDescription.trim().isEmpty())
            try {
                FileWriter descriptionWriter = new FileWriter(outPath + File.separator + fileName + "__description.txt");
                descriptionWriter.write(sDescription);
                descriptionWriter.close();
            } catch (IOException e) {
                LOG.error("Error creating description file", e);
            }
        return process;
    }

    @Override
    public IBackgroundProcess startRestore(String sModule, String dumpId, boolean drop) {
        String sHost = this.getModuleHost(sModule);
        String credentials = this.getHostCredentials(sHost);
        String dumpFile = this.getDumpPath(sModule) + File.separator + dumpId + ".gz";
        DumpProcess process = new DumpProcess(this, sModule, MongoTemplateManager.getDatabaseName(sModule), MongoTemplateManager.getServerHosts(sHost), servletContext.getRealPath(""), appConfig.get("dumpFolder"));

        process.startRestore(dumpFile, drop, credentials);
        return process;
    }

    @Override
    public boolean deleteDump(String sModule, String sDump) {
        String dumpPath = getDumpPath(sModule);
        String basename = dumpPath + File.separator + sDump;

        File archiveFile = new File(basename + ".gz");
        boolean result = archiveFile.delete();

        for (File file : new File(dumpPath).listFiles()) {
            String filename = file.getName();
            if (filename.startsWith(sDump) && (filename.endsWith(".log") || filename.endsWith(".log.gz") || filename.endsWith(".txt")))
                file.delete();
        }

        return result;
    }
    
    // FIXME
    private String getHostCredentials(String sHost) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(appContext.getResource("classpath:/applicationContext-data.xml").getFile());

            NodeList clients = document.getElementsByTagName("mongo:mongo-client");
            for (int i = 0; i < clients.getLength(); i++) {
                Node node = clients.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element client = (Element) node;
                    String credentialString = client.getAttribute("credential");
                    if (credentialString.length() == 0) {
                        return null;
                    } else
                        return accountForEnvVariables(credentialString);
                }
            }
            return null;
        } catch (ParserConfigurationException | IOException | SAXException e) {
            LOG.error("Error parsing host credentials", e);
            return null;
        }
    }
    
    static public String accountForEnvVariables(String stringContainingEnvVariables) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#\\{systemEnvironment\\[(.*?)\\]\\}").matcher(stringContainingEnvVariables);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String matchingString = matcher.group(1), replacementString = System.getenv(matchingString.replaceAll("'|\"", ""));
            if (replacementString == null)
                matcher.appendReplacement(output, "#{systemEnvironment['" + matchingString + "']}"); // replacement failed (no such env variable)
            else
                matcher.appendReplacement(output, replacementString);
        }
        matcher.appendTail(output);
        return output.toString();
    }
    
    @Override
    public long getModuleSize(String module) {
        return ((Number) MongoTemplateManager.get(module).getDb().runCommand(new BasicDBObject("dbStats", 1)).get("storageSize")).longValue();
    }

    @Override
    public InputStream getDumpInputStream(String sModule, String sDumpName) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(new File(getDumpPath(sModule) + File.separator + sDumpName + ".gz")));
    }

    @Override
    public InputStream getDumpLogInputStream(String sModule, String sDumpName) throws IOException {
        return DumpProcess.getLogInputStream(getDumpPath(sModule) + File.separator + sDumpName + "__dump.log");
    }

	public boolean isModuleAvailableForWriting(String sModule) {
		return MongoTemplateManager.isModuleAvailableForWriting(sModule);
	}
	
	@Override
	public void updateDatabaseLastModification(String sModule, Date lastModification, boolean restored) {
		MongoTemplateManager.updateDatabaseLastModification(sModule, lastModification, restored);
	}

	public void lockModuleForWriting(String sModule) {
		MongoTemplateManager.lockModuleForWriting(sModule);
	}

	public void unlockModuleForWriting(String sModule) {
		MongoTemplateManager.unlockModuleForWriting(sModule);
	}
	
    public boolean isModuleAvailableForDump(String sModule) {
        return isModuleAvailableForWriting(sModule);
    }

    // TODO: METHODS ABOVE ARE PLAIN COPIES OF GIGWA CODE ===> HOW TO AVOID DUPLICATON??
    
	@Override
	public Map<Comparable, String> getSubEntities(String entityType, String sModule, Comparable[] parentEntityIDs) throws Exception {
	    Map<Comparable, String> subEntityIdToNameMap = new LinkedHashMap<Comparable, String>();
	    return subEntityIdToNameMap;
	}
	
	@Override
	public String managedEntityInfo(String sModule, String entityType, Collection<Comparable> entityIDs) throws Exception {
    	if (ENTITY_PROJECT.equals(entityType)) {
            Query q = new Query(Criteria.where("_id").is(Integer.valueOf(entityIDs.iterator().next().toString())));
            q.with(Sort.by(Arrays.asList(new Sort.Order(Sort.Direction.ASC, "_id"))));
            q.fields().include(MetagenomicsProject.FIELDNAME_DESCRIPTION);
            return MongoTemplateManager.get(sModule).findOne(q, MetagenomicsProject.class).getDescription();

        }
        else
        	throw new Exception("Not managing entities of type " + entityType);
	}
	
	@Override
	public boolean doesEntityTypeSupportDescription(String sModule, String sEntityType) {
		return true;
	}
	
	@Override
	public boolean setManagedEntityDescription(String sModule, String sEntityType, String entityId, String desc) throws Exception {
		if (ENTITY_PROJECT.equals(sEntityType)) {
			final int projectIdToModify = Integer.parseInt(entityId);
			if (!canUserWriteToProject(SecurityContextHolder.getContext().getAuthentication(), sModule, projectIdToModify))
				throw new Exception("You are not allowed to modify this project");

			MongoTemplate mongoTemplate = MongoTemplateManager.get(sModule);
			if (mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(projectIdToModify)), new Update().set(MetagenomicsProject.FIELDNAME_DESCRIPTION, desc), MetagenomicsProject.class).getModifiedCount() > 0)
				LOG.debug("Updated description for project " + projectIdToModify + " from module " + sModule);

			return true;
		}

		throw new Exception("Not managing entities of type " + sEntityType);
	}
	
}
