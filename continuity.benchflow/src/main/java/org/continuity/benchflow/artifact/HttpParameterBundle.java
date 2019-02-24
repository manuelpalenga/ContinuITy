package org.continuity.benchflow.artifact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cloud.benchflow.dsl.definition.workload.datasource.DataSource;
import cloud.benchflow.dsl.definition.workload.operation.body.BodyType;
import cloud.benchflow.dsl.definition.workload.operation.parameter.Parameter;

/**
 * 
 * @author Manuel Palenga
 *
 */
public class HttpParameterBundle {
	
	private Map<String, Parameter> queryParameter = null;
	private Map<String, Parameter> urlParameter = null;
	private BodyType bodyInput = null;
	private List<DataSource> dataSources = null;
	
	public HttpParameterBundle() {
		queryParameter = new HashMap<String, Parameter>();
		urlParameter = new HashMap<String, Parameter>();
		bodyInput = null;
		dataSources = new ArrayList<DataSource>();
	}
	
	public Map<String, Parameter> getQueryParameter() {
		return queryParameter;
	}
	
	public Map<String, Parameter> getUrlParameter() {
		return urlParameter;
	}
	
	public List<DataSource> getDataSources() {
		return dataSources;
	}

	public void setBodyInput(BodyType bodyInput) {
		this.bodyInput = bodyInput;
	}
	
	public BodyType getBodyInput() {
		return bodyInput;
	}
	
}
