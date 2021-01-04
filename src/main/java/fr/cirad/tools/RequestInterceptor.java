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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import fr.cirad.scheduler.ScheduledTaskManager;

public class RequestInterceptor extends HandlerInterceptorAdapter {

    @Autowired ScheduledTaskManager scheduledTaskManager;
    
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    	// this is a hack to be able to launch ref package inspection as early as possible (it needs a HttpServletRequest to be able to invoke Opal)
    	if (!scheduledTaskManager.isRefPkgInspectionEnabled())
    		scheduledTaskManager.enableRefPkgInspection((HttpServletRequest) request);
	}
}