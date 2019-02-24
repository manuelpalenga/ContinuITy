package org.continuity.benchflow.controllers;

import static org.continuity.api.rest.RestApi.BenchFlow.DSL.ROOT;
import static org.continuity.api.rest.RestApi.BenchFlow.DSL.Paths.GET;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.continuity.benchflow.artifact.BenchFlowUtility;
import org.continuity.benchflow.transform.IntersectionTransformator;
import org.continuity.benchflow.transform.ScalaHelper;
import org.continuity.benchflow.transform.TransformationExecutor;
import org.continuity.benchflow.transform.TransformationExecutor.HttpWorkloadVersion;
import org.continuity.commons.storage.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import cloud.benchflow.dsl.BenchFlowTestAPI;
import cloud.benchflow.dsl.definition.BenchFlowTest;
import cloud.benchflow.dsl.definition.configuration.goal.goaltype.GoalType;
import cloud.benchflow.dsl.definition.workload.HttpWorkload;
import cloud.benchflow.dsl.definition.workload.Workload;

/**
 * REST endpoint for BenchFlow DSLs.
 *
 * @author Henning Schulz, Manuel Palenga
 *
 */
@RestController
@RequestMapping(ROOT)
public class BenchFlowController {

	private static final Logger LOGGER = LoggerFactory.getLogger(BenchFlowController.class);

	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	@Qualifier("benchflowDSLStorage")
	private MemoryStorage<HttpWorkload> storage;

	/**
	 * Returns the BenchFlow DSL that is stored with the specified ID.
	 *
	 * @param id
	 *            The ID of the BenchFlow DSL.
	 * @return A bundle holding the BenchFlow DSL or a 404 error response if not found.
	 */
	@RequestMapping(value = GET, method = RequestMethod.GET)
	public ResponseEntity<String> getBenchFlowDSL(@PathVariable String id) {
		HttpWorkload httpWorkload = storage.get(id);

		if (httpWorkload == null) {
			LOGGER.warn("Could not find a BenchFlow DSL with id {}!", id);
			return ResponseEntity.notFound().build();
		} else {
			LOGGER.info("Retrieved BenchFlow DSL with id {}.", id);
			return ResponseEntity.ok(BenchFlowUtility.getStringFromHttpWorkload(httpWorkload));
		}
	}

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public ResponseEntity<String> uploadWorkloadDSL(@RequestParam String tag, @RequestBody String workload) {
		
		HttpWorkload httpWorkload = null;
		try {
			httpWorkload = BenchFlowUtility.loadHttpWorkloadFromString(workload);
		} catch (IllegalArgumentException e) {
			String exceptionMessage = String.format("Exception during loading BenchFlow workload DSL with tag '%s'!", tag);
			LOGGER.error(exceptionMessage, e);
			throw new IllegalArgumentException(exceptionMessage, e);
		}
		
		if(!httpWorkload.sutVersion().isDefined()) {
			String exceptionMessage = String.format("BenchFlow workload DSL with tag '%s' does not contain a version!", tag);
			LOGGER.error(exceptionMessage);
			throw new IllegalArgumentException(exceptionMessage);
		}
		
		String version = httpWorkload.sutVersion().get();
		String extractedVersion = BenchFlowUtility.extractVersion(version);
		String id = String.format("%s-%s", tag, extractedVersion);
		
		storage.putToReserved(id, httpWorkload);
		LOGGER.info("Stored BenchFlow DSL with id {}.", id);
		return ResponseEntity.ok(id);
	}
	
	@RequestMapping(value = "/get", method = RequestMethod.POST)
	public ResponseEntity<String> getFulfilledDSL(@RequestBody String dsl) {

		BenchFlowTest test = BenchFlowUtility.loadBenchFlowTestFromString(dsl);

		if(test.workload() != null && test.workload().nonEmpty()) {
			return ResponseEntity.ok(dsl);
		}
	
		String systemName = test.sut().name();	
		List<String> versions = BenchFlowUtility.getVersions(test);
		if(versions.isEmpty()) {
			String exceptionMessage = String.format("No versions specified for SUT name '%s'!", systemName);
			LOGGER.error(exceptionMessage);
			throw new IllegalArgumentException(exceptionMessage);
		}

		GoalType goalType = test.configuration().goal().goalType();
		
		// Test preconditions for regression testing
		/*
		if(versions.size() < 1 && (goalType == GoalType.INDIVIDUAL_REGRESSION || goalType == GoalType.INTERSECTION_REGRESSION)) {
			String exceptionMessage = String.format("For regression testing are at least two versions required!");
			LOGGER.error(exceptionMessage);
			throw new IllegalArgumentException(exceptionMessage);					
		}
		*/
		if (versions.size() != 1 && goalType != GoalType.INDIVIDUAL_REGRESSION && goalType != GoalType.INTERSECTION_REGRESSION)	{
			// Test precondition for load testing
			
			String exceptionMessage = String.format("Only one SUT version is allowed using no regression goal!");
			LOGGER.error(exceptionMessage);
			throw new IllegalArgumentException(exceptionMessage);
			
		}
				
		Map<String, Workload> workloads = null;
		
		LOGGER.debug("Retrieved BenchFlow DSL with SUT name {} and goal type {}.", systemName, goalType.toString());
		
		if(goalType == GoalType.INTERSECTION_REGRESSION) {
			workloads = new HashMap<String, Workload>();
			IntersectionTransformator transformater = new IntersectionTransformator();
			HttpWorkload workload = transformater.getIntersectionWorkloadFromVersions(systemName, versions, restTemplate);
			workloads.put("intersection-workload", workload);			
		} else {			
			workloads = this.getIndividualWorkloadsFromVersions(systemName, versions);
		}
		
		LOGGER.debug("BenchFlow DSL fulfilled successfully!");

		BenchFlowTest testWithWorkloads = new BenchFlowTest(
				test.version(), 
				test.name(), 
				test.description(), 
				test.configuration(), 
				test.sut(), 
				ScalaHelper.mapAsScalaMap(workloads), 
				test.dataCollection());
		
		String dslWithWorkloads = BenchFlowTestAPI.testToYamlString(testWithWorkloads);
		return ResponseEntity.ok(dslWithWorkloads);
	}
	
	private Map<String, Workload> getIndividualWorkloadsFromVersions(String systemName, List<String> versions){
		Map<String, Workload> workloads = new HashMap<String, Workload>();
		
		for(String version : versions) {
			
			String id = String.format("%s-%s", systemName, version);
			HttpWorkload httpWorkload = storage.get(id);

			if (httpWorkload == null) {
				TransformationExecutor executor = new TransformationExecutor();
				HttpWorkloadVersion workloadContainer = executor.createBenchFlowWorkload(systemName, version, restTemplate);

				if (workloadContainer == null) {
					String exceptionMessage = String.format("No HTTP workload with SUT name %s and version %s is available!", systemName, version);
					LOGGER.error(exceptionMessage);
					throw new IllegalArgumentException(exceptionMessage);
				} else {			
					httpWorkload = workloadContainer.getWorkload();
					String storedId = workloadContainer.getId();
					
					storage.putToReserved(storedId, httpWorkload);
					LOGGER.debug("Retrieves HttpWorkload from storage with id '{}', the stored id was '{}'." , id, storedId);
				}
			}
			workloads.put(id, httpWorkload);
		}
		
		return workloads;
	}
}
