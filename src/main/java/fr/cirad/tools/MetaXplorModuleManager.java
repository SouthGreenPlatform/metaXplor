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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.stereotype.Component;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import fr.cirad.metaxplor.importing.MtxImport;
import fr.cirad.metaxplor.model.AssignedSequence;
import fr.cirad.metaxplor.model.Blast;
import fr.cirad.metaxplor.model.DBField;
import fr.cirad.metaxplor.model.MetagenomicsProject;
import fr.cirad.metaxplor.model.Sample;
import fr.cirad.metaxplor.model.Sequence;
import fr.cirad.security.ReloadableInMemoryDaoImpl;
import fr.cirad.security.base.IModuleManager;
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
	
	private static final Logger LOG = Logger.getLogger(MetaXplorModuleManager.class);
	
	@Autowired private AppConfig appConfig;
    
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
    public Map<String, Map<Comparable, String>> getEntitiesByModule(String entityType, Boolean fTrueForPublicFalseForPrivateNullForAny) {
        Map<String, Map<Comparable, String>> entitiesByModule = new LinkedHashMap<>();
        MongoTemplateManager.getAvailableModules().stream().forEach((module) -> {
            LinkedHashMap<Comparable, String> moduleEntities = new LinkedHashMap<>();
            if (ENTITY_PROJECT.equals(entityType)) {
                Query q = new Query();
                q.fields().include(MetagenomicsProject.FIELDNAME_NAME).include(MetagenomicsProject.FIELDNAME_PUBLIC);
                MongoTemplateManager.get(module).find(q, MetagenomicsProject.class).stream().forEach((project) -> {
                	if (fTrueForPublicFalseForPrivateNullForAny == null || project.isPublicProject() == fTrueForPublicFalseForPrivateNullForAny)
                		moduleEntities.put(project.getId(), project.getName());
                });
                entitiesByModule.put(module, moduleEntities);
            }
        });
        return entitiesByModule;
    }

    @Override
    public boolean removeDataSource(String module, boolean fAlsoDropDatabase) throws IOException {
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
    public boolean removeManagedEntity(String module, String sEntityType, Comparable entityId) throws Exception {
    	if (ENTITY_PROJECT.equals(sEntityType)) {
    		int projId = Integer.parseInt(entityId.toString());
            MongoTemplate mongoTemplate = MongoTemplateManager.get(module);

            MetagenomicsProject project = mongoTemplate.findById(projId, MetagenomicsProject.class);
            String sProjectDesc = entityId + (project == null ? "" : (" (" + project.getAcronym() + ")"));
            
            // clear project sequence and sample data 
           	new Thread() {
           		public void run() {
                    DeleteResult wr = mongoTemplate.remove(new Query(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(projId)), Sequence.class);
                    if (wr.getDeletedCount() > 0)
                    	LOG.debug(wr.getDeletedCount() + " unassigned sequences removed while deleting project " + sProjectDesc + " from database " + module);           			
           		}
           	}.start();
            DeleteResult wr = mongoTemplate.remove(new Query(Criteria.where("_id." + DBConstant.FIELDNAME_PROJECT).is(projId)), AssignedSequence.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " assigned sequences removed while deleting project " + sProjectDesc + " from database " + module);
            UpdateResult ur = mongoTemplate.updateMulti(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(projId)), new Update().pull(DBConstant.FIELDNAME_PROJECT, projId), Sample.class);
            if (ur.getModifiedCount() > 0)
            	LOG.debug(ur.getModifiedCount() + " samples updated while deleting project " + sProjectDesc + " from database " + module);
            wr = mongoTemplate.remove(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).size(0)), Sample.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " samples removed while deleting project " + sProjectDesc + " from database " + module);
            wr = mongoTemplate.remove(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(projId)), Blast.class);
            if (wr.getDeletedCount() > 0)
            	LOG.debug(wr.getDeletedCount() + " blast results removed while deleting project " + sProjectDesc + " from database " + module);
                        
            // clear cache collections
            for (String collName : mongoTemplate.getCollectionNames()) {
                if (collName.startsWith(DBConstant.CACHE_PREFIX)) {
                	DBField dbField = mongoTemplate.findById(Integer.parseInt(collName.substring(collName.lastIndexOf("_") + 1)), DBField.class);
                	if (dbField != null && (DBConstant.DOUBLE_TYPE.equals(dbField.getType()) || DBConstant.DATE_TYPE.equals(dbField.getType())))
                		wr = mongoTemplate.remove(new Query(Criteria.where("_id").is(projId)), collName);	// this kind of cache collection holds one record per project
                	else {
    	                // '_id' of records part of this project only  
    	                List<Comparable> singleProjectRecord = new ArrayList<>();
    	                // '_id' of records part of this project and one or more other projects
    	                List<Comparable> multiProjectRecord = new ArrayList<>();
    	                BasicDBObject m = new BasicDBObject("$match", new BasicDBObject(DBConstant.FIELDNAME_PROJECT, projId));
    	                BasicDBObject p = new BasicDBObject("$project", new BasicDBObject("ln", new BasicDBObject("$size", "$pj")));
    	                List<BasicDBObject> pipeline = Arrays.asList(m, p);
    	                MongoCursor<Document> cursor = mongoTemplate.getCollection(collName).aggregate(pipeline).allowDiskUse(true).iterator();
    	                if (cursor != null && cursor.hasNext()) {
    	                    while (cursor.hasNext()) {
    	                    	Document record = cursor.next();
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
	    	                Update update = new Update().pull(DBConstant.FIELDNAME_PROJECT, projId);
	    	                ur = mongoTemplate.updateMulti(query, update, collName);
    	                }
                	}
                    if (mongoTemplate.getCollection(collName).countDocuments() == 0)
                    	mongoTemplate.dropCollection(collName);
                }
            }
            
            // delete sequence files on webserver
            File fastaFile = new File(appConfig.sequenceLocation() + File.separator + module + File.separator + projId + Sequence.NUCL_FASTA_EXT), indexFile = new File(fastaFile.getAbsolutePath() + Sequence.NUCL_FAI_EXT), lightIndexFile = new File(fastaFile.getParent() + File.separator + "_" + fastaFile.getName() + Sequence.NUCL_FAI_EXT);
            fastaFile.delete();
            indexFile.delete();
            lightIndexFile.delete();

            // delete blast-db files on cluster
            try {
            	new OpalServiceLauncher().cleanupProjectFiles(module, projId);
            }
            catch (Exception e) {
            	LOG.warn(e);
            }

            // remove the project from project collection
            mongoTemplate.remove(project);
            
            if (mongoTemplate.count(new Query(), MetagenomicsProject.class) == 0)
            	mongoTemplate.getDb().drop(); // remove whole db if it contains no more project
            else {
            	ur = mongoTemplate.updateMulti(new Query(Criteria.where(DBConstant.FIELDNAME_PROJECT).is(projId)), new Update().pull(DBConstant.FIELDNAME_PROJECT, projId), DBField.class);
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
		boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));

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
			boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
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
		boolean fAdminUser = fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
		Collection<String> writableEntityTypes = userDao.getWritableEntityTypesByModule(authentication.getAuthorities()).get(module);
        if (fAdminUser || (fAuthentifiedUser && ((writableEntityTypes != null && writableEntityTypes.contains(ENTITY_PROJECT)))))
            return true;
        return false;
    }

	public boolean canUserWriteToProject(Authentication authentication, String sModule, int projectId)
	{
		boolean fAuthentifiedUser = authentication != null && authentication.getAuthorities() != null && !"anonymousUser".equals(authentication.getPrincipal());
		if (fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN)))
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
		if (fAuthentifiedUser && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN)))
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
}
