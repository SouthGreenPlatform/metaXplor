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

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.map.UnmodifiableMap;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

@ControllerAdvice
public class GlobalExceptionHandler {

	protected static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);
	
	@Autowired private SimpleMappingExceptionResolver exceptionResolver;
		
//  @ExceptionHandler({Exception.class, Error.class})
//  @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
//  public ModelAndView handleAllExceptions(HttpServletRequest request, HttpServletResponse response, Throwable t) {
//  	LOG.error("Error at URL " + request.getRequestURI() + "?" + request.getQueryString(), t);
//  	if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")))
//  	{
//  		HashMap<String, String> map = new HashMap<String, String>();
//  		map.put("errorMsg", ExceptionUtils.getStackTrace(t));
//  		return new ModelAndView(new MappingJackson2JsonView(), UnmodifiableMap.decorate(map));
//  	}
//  	else
//  		return exceptionResolver.resolveException(request, response, null, Exception.class.isAssignableFrom(t.getClass()) ? (Exception) t : new Exception(t));
//  }
  
  @ExceptionHandler(Exception.class)
  @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
  public ModelAndView handleAllExceptions(HttpServletRequest request, HttpServletResponse response, Exception ex) {
  	LOG.error("Error at URL " + request.getRequestURI() + "?" + request.getQueryString(), ex);
  	if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")))
  	{
  		HashMap<String, String> map = new HashMap<String, String>();
  		map.put("errorMsg", ExceptionUtils.getStackTrace(ex));
  		return new ModelAndView(new MappingJackson2JsonView(), UnmodifiableMap.decorate(map));
  	}
  	else
  		return exceptionResolver.resolveException(request, response, null, ex);
  }
}