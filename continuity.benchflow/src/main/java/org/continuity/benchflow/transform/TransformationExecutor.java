package org.continuity.benchflow.transform;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.api.rest.RestApi.IdpaAnnotation;
import org.continuity.api.rest.RestApi.IdpaApplication;
import org.continuity.api.rest.RestApi.Wessbas;
import org.continuity.benchflow.artifact.BenchFlowUtility;
import org.continuity.benchflow.artifact.ContinuITyModel;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import cloud.benchflow.dsl.definition.workload.HttpWorkload;

public class TransformationExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger(TransformationExecutor.class);
	
	/**
	 * Transforms the ContinuITy model into a BenchFlow workload model and returns it with the corresponding application version.
	 * 
	 * @param tag
	 * 			The tag of the application.
	 * @param version
	 * 			The version of the application.
	 * @param restTemplate
	 * 			Rest template to call REST interfaces.
	 * @return A container {@link HttpWorkloadVersion} containing the transformed {@link HttpWorkload} and the application version
	 * or null if some required input fields are missing.
	 */
	public HttpWorkloadVersion createBenchFlowWorkload(String tag, String version, RestTemplate restTemplate) {
		ContinuITyModel continuITyModel = this.getContinuITyModel(tag, version, restTemplate);
		
		if (continuITyModel == null) {
			LOGGER.warn("The workload model with tag '{}' and version '{}' does not provide all required input fields!", tag, version);
			return null;
		} else {				
			HttpWorkload httpWorkload = this.transformToBenchFlowWorkloadModel(continuITyModel);
			String workloadVersion = BenchFlowUtility.extractVersion(continuITyModel.getApplication().getVersion());
			String id = String.format("%s-%s", tag, workloadVersion);
			
			return new HttpWorkloadVersion(httpWorkload, id);
		}
	}
	
	
	/**
	 * Transforms the provided ContinuITy model into a BenchFlow workload model.
	 * 
	 * @param continuITyModel
	 * 			The model which should be transformed into the BenchFlow workload model.
	 * @return The transformed {@link HttpWorkload}.
	 */
	public HttpWorkload transformToBenchFlowWorkloadModel(ContinuITyModel continuITyModel) {
		ModelTransformator modelConverter = new ModelTransformator();
		return modelConverter.transformToBenchFlow(continuITyModel);
	}

	/**
	 * Returns the {@link ContinuITyModel} of the provided workload model link with by means of the defined rest template.
	 * 
	 * @param tag
	 * 			The tag of the application.
	 * @param version
	 * 			The version of the application.
	 * @param restTemplate
	 * 			Rest template to call REST interfaces.
	 * @return The {@link ContinuITyModel} or null if it is not possible to retrieve required fields.
	 */
	public ContinuITyModel getContinuITyModel(String tag, String version, RestTemplate restTemplate) {

		String id = String.format("%s-%s", tag, version);
		
		ApplicationAnnotation annotation;
		try {
			annotation = restTemplate.getForObject(IdpaAnnotation.Annotation.GET.requestUrl(id).get(), ApplicationAnnotation.class);
			if (annotation == null) {
				LOGGER.error("Annotation with tag {} is null! Aborting.", tag);
				return null;
			}
		} catch (HttpStatusCodeException e) {
			LOGGER.error("Received a non-200 response from IDPA-annotation (id: {}): {} ({}) - {}", id, e.getStatusCode(), e.getStatusCode().getReasonPhrase(), e.getResponseBodyAsString());
			return null;
		}

		Application application;
		try {
			application = restTemplate.getForObject(IdpaApplication.Application.GET.requestUrl(id).get(), Application.class);
			if (application == null) {
				LOGGER.error("Application with tag {} is null! Aborting.", tag);
				return null;
			}
		} catch (HttpStatusCodeException e) {
			LOGGER.error("Received a non-200 response from IDPA-application (id: {}): {} ({}) - {}", id, e.getStatusCode(), e.getStatusCode().getReasonPhrase(), e.getResponseBodyAsString());
			return null;
		}
	
		BehaviorModel behaviorModel = null;
			
		try {
			behaviorModel = restTemplate.getForObject(Wessbas.BehaviorModel.CREATE.requestUrl(tag).withQuery("version", version).get(), BehaviorModel.class);
		} catch (HttpStatusCodeException e) {
			LOGGER.debug("Received a non-200 response (tag: {}, version: {}): {} ({}) - {}", tag, version, e.getStatusCode(), e.getStatusCode().getReasonPhrase(), e.getResponseBodyAsString());
			behaviorModel = null;
		}
		return new ContinuITyModel(behaviorModel, application, annotation);
	}
	
	public static class HttpWorkloadVersion {
		private HttpWorkload workload;
		private String id;
		
		public HttpWorkloadVersion(HttpWorkload workload, String id) {
			this.workload = workload;
			this.id = id;
		}
		
		public HttpWorkload getWorkload() {
			return workload;
		}
		
		public String getId() {
			return id;
		}
	}
}
