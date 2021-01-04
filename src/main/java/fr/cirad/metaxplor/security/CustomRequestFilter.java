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
package fr.cirad.metaxplor.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.GenericFilterBean;

import fr.cirad.metaxplor.model.MetagenomicsProject;
import fr.cirad.security.ReloadableInMemoryDaoImpl;
import fr.cirad.security.base.IRoleDefinition;
import fr.cirad.tools.MetaXplorModuleManager;
import fr.cirad.tools.mongo.MongoTemplateManager;

/**
 * Filter that (i) restricts access to allowed projects ; (ii) checks whether administrator is still using default username & password
 *
 * @author petel, sempere
 */
public class CustomRequestFilter extends GenericFilterBean {
	
    private ReloadableInMemoryDaoImpl userDao;
    
    /**
     * filter method
     *
     * @param request
     * @param response
     * @param chain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (userDao == null) {
            WebApplicationContext webApplicationContext = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
            userDao = webApplicationContext.getBean(ReloadableInMemoryDaoImpl.class);
        }

    	String sModule = request.getParameter("module"), projects = request.getParameter("projects");
    	if (sModule != null && !sModule.isEmpty() && (projects == null || projects.isEmpty())) {	// make sure user only sees projects he's allowed to
	    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    		boolean fIsAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
			HashSet<Integer> allowedProjects = new HashSet<>(MongoTemplateManager.get(sModule).findDistinct(fIsAdmin ? new Query() /* administrators have access to all projects */ : new Query(Criteria.where(MetagenomicsProject.FIELDNAME_PUBLIC).is(true)), "_id", MetagenomicsProject.class, Integer.class));
			if (!fIsAdmin) { // look for specific user permissions
				Map<String, Map<String, Collection<Comparable>>> customRolesByEntityType = userDao.getCustomRolesByModuleAndEntityType(authentication.getAuthorities()).get(sModule);
				if (customRolesByEntityType != null)
				{
					Map<String, Collection<Comparable>> customRolesOnProjects = customRolesByEntityType.get(MetaXplorModuleManager.ENTITY_PROJECT);
					if (customRolesOnProjects != null)
					{
						Collection<Comparable> manageableProjects = customRolesOnProjects.get(IRoleDefinition.ENTITY_MANAGER_ROLE), readableProjects = customRolesOnProjects.get(MetaXplorModuleManager.ROLE_READER);
						Collection<Integer> projectCustomRoles = CollectionUtils.union(manageableProjects == null ? new ArrayList<>() : manageableProjects, readableProjects == null ? new ArrayList<>() : readableProjects);
						if (projectCustomRoles != null)
							for (Integer readableProjId : projectCustomRoles)
								allowedProjects.add(readableProjId);
					}
				}
				if (allowedProjects.isEmpty())
					allowedProjects.add(-1);
			}

			String sAllowedProjects = "";
			for (Comparable projId : allowedProjects)
				sAllowedProjects += (sAllowedProjects.isEmpty() ? "" : ";") + projId;

			request = new RequestWrapper((HttpServletRequest) request, Collections.singletonMap("projects", new String[]{sAllowedProjects}));
    	}
    	else {
    		if ("/index.jsp".equals(((HttpServletRequest) request).getServletPath()) && "success".equals(request.getParameter("login"))) {
	    		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
	    		boolean fIsAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
	    		if (fIsAdmin && "metadmin".equals(authentication.getName()) && "nimda".equals(authentication.getCredentials()))
	    			request = new RequestWrapper((HttpServletRequest) request, Collections.singletonMap("warnAboutDefaultPassword", new String[]{"true"}));
        	}
    	}
		chain.doFilter(request, response);
    }

    class RequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> modifiableParameters;
        private Map<String, String[]> allParameters = null;

        /**
         * Create a new request wrapper that will merge additional parameters
         * into the request object without prematurely reading parameters from
         * the original request.
         *
         * @param request
         * @param additionalParams
         */
        public RequestWrapper(final HttpServletRequest request,
                final Map<String, String[]> additionalParams) {
            super(request);
            modifiableParameters = new TreeMap<>();
            modifiableParameters.putAll(additionalParams);
        }

        @Override
        public String getParameter(final String name) {
            String[] strings = getParameterMap().get(name);
            if (strings != null) {
                return strings[0];
            }
            return super.getParameter(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            if (allParameters == null) {
                allParameters = new TreeMap<>();
                allParameters.putAll(super.getParameterMap());
                allParameters.putAll(modifiableParameters);
            }
            //Return an unmodifiable collection because we need to uphold the interface contract.
            return Collections.unmodifiableMap(allParameters);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(getParameterMap().keySet());
        }

        @Override
        public String[] getParameterValues(final String name) {
            return getParameterMap().get(name);
        }
    }
}
