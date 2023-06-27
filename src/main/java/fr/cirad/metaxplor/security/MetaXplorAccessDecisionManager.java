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

import java.util.Collection;
import java.util.List;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;

import fr.cirad.tools.mongo.MongoTemplateManager;

public class MetaXplorAccessDecisionManager extends AffirmativeBased {

    public MetaXplorAccessDecisionManager(List<AccessDecisionVoter<?>> decisionVoters) {
        super(decisionVoters);
    }

    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes) {
        if (object instanceof FilterInvocation) {

            FilterInvocation fi = (FilterInvocation) object;
            if (!fi.getRequest().getClass().getName().equals("org.springframework.security.web.DummyRequest")) {
                String module = fi.getRequest().getParameter("module");
                if (module != null && MongoTemplateManager.get(module) != null && !MongoTemplateManager.isModulePublic(module)) {
//                    boolean fIsAnonymous = authorities != null && authorities.contains(new GrantedAuthorityImpl("ROLE_ANONYMOUS"));
//                    boolean fIsAdmin = authorities != null && authorities.contains(new GrantedAuthorityImpl(IRoleDefinition.ROLE_ADMIN));
//                    boolean fHasRequiredRole = authorities != null && authorities.contains(new GrantedAuthorityImpl(IRoleDefinition.TOPLEVEL_ROLE_PREFIX + UserPermissionController.ROLE_STRING_SEPARATOR + module));
//                    if (!fIsAnonymous && !fIsAdmin && !fHasRequiredRole) {
//                        throw new AccessDeniedException("You are not allowed to access module '" + module + "'");
//                    }
                }
            }
        }
        super.decide(authentication, object, configAttributes);
    }
}
