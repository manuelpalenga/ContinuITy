package org.continuity.benchflow.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.benchflow.BenchFlowTestHelper;
import org.continuity.benchflow.artifact.BenchFlowUtility;
import org.continuity.benchflow.artifact.ContinuITyModel;
import org.continuity.benchflow.transform.TransformationExecutor;
import org.continuity.benchflow.transform.TransformationExecutor.HttpWorkloadVersion;
import org.continuity.commons.storage.MemoryStorage;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.application.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import cloud.benchflow.dsl.definition.BenchFlowTest;
import cloud.benchflow.dsl.definition.configuration.goal.goaltype.GoalType;
import cloud.benchflow.dsl.definition.workload.HttpWorkload;
import cloud.benchflow.dsl.definition.workload.Workload;
import cloud.benchflow.dsl.definition.workload.drivertype.DriverType;
import cloud.benchflow.dsl.definition.workload.operation.Operation;
import cloud.benchflow.dsl.definition.workload.workloaditem.HttpWorkloadItem;
import scala.collection.JavaConverters;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TransformationExecutor.class)
public class TestBenchFlowController {
	
	private BenchFlowController controller;
	
	private static final String SUT_NAME = "my_sut";
	private static final String DSL = 
			"version: '1'\n" + 
			"name: abc\n" + 
			"configuration: \n" + 
			"  goal:\n" + 
			"    type: %s\n" + 
			"  settings:\n" + 
			"    stored_knowledge: false\n" + 
			"  workload_execution:\n" + 
			"    ramp_up: 100m\n" + 
			"    steady_state: 10m\n" + 
			"    ramp_down: 100m\n" + 
			"  termination_criteria:\n" + 
			"    test:\n" + 
			"      max_time: 10m\n" + 
			"    experiment:\n" + 
			"      type: FIXED\n" + 
			"      number_of_trials: 1\n" + 
			"sut:\n" + 
			"  name: %s\n" + 
			"  version: %s\n" + 
			"  type: http\n" + 
			"  configuration:\n" + 
			"    target_service:\n" + 
			"      name: my_target_service\n" + 
			"      endpoint: localhost:8080\n" + 
			"    deployment: {}\n" + 
			"workload: {}\n" + 
			"data_collection:\n" + 
			"  client_side:\n" + 
			"    faban:\n" + 
			"      max_run_time: 10m\n" + 
			"      interval: 10m\n";
	
	private static final String WORKLOAD_NOT_VALID = 
			"workload-items:\n" + 
			"  behavior_model_1:\n" + 
			"    popularity: 100.0%\n" + 
			"    driver_type: HTTP\n";
	
	private static final String WORKLOAD_1 = 
			"sut-version: 1.2.3\n"+
			"workload-items:\n" + 
			"  behavior_model_1:\n" + 
			"    popularity: 100.0%\n" + 
			"    driver_type: HTTP\n" + 
			"    operations:\n" + 
			"      - id: indexUsingGET\n" + 
			"        method: GET\n" + 
			"        endpoint: /\n" + 
			"        protocol: http\n";
	
	private static final String WORKLOAD_2 = 
			"sut-version: 4.5.6\n"+
			"workload-items:\n" + 
			"  behavior_model_2:\n" + 
			"    popularity: 100.0%\n" + 
			"    driver_type: HTTP\n" + 
			"    operations:\n" + 
			"      - id: indexUsingGET\n" + 
			"        method: GET\n" + 
			"        endpoint: /\n" + 
			"        protocol: http\n" + 
			"      - id: buyUsingGET\n" + 
			"        method: POST\n" + 
			"        endpoint: /buy\n" + 
			"        protocol: http\n" + 
			"      - id: loginUsingGET\n" + 
			"        method: GET\n" + 
			"        endpoint: /login\n" + 
			"        protocol: https\n" + 
			"";
	
	@Before
	public void setUp() {
		controller = new BenchFlowController();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testUploadNotValidWorkloadDSL() {
		// SUT-version is missing
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_NOT_VALID);		
	}
	
	@Test
	public void testUploadWorkloadDSL() {
		
		String id = SUT_NAME + "-1.2.3";
		
		MemoryStorage<HttpWorkload> storage = new MemoryStorage<HttpWorkload>(HttpWorkload.class);
		Whitebox.setInternalState(controller, "storage", storage);	
		assertNull(storage.get(id));
		
		ResponseEntity<String> result = controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_1);		
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(id, result.getBody());
		
		assertNotNull(storage.get(id));
		assertTrue(storage.get(id).workloads().nonEmpty());
	}
	
	@Test
	public void testGetFulfilledDSLWithOneWorkload() {

		// Test setup: Add workload model to storage
		MemoryStorage<HttpWorkload> storage = new MemoryStorage<HttpWorkload>(HttpWorkload.class);
		Whitebox.setInternalState(controller, "storage", storage);	
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_1);
		
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.INDIVIDUAL_REGRESSION.toString(), SUT_NAME, "1.2.3");
		ResponseEntity<String> result = controller.getFulfilledDSL(testDSL);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertTrue("DSL does not contain the workload!", result.getBody().length() >= (testDSL.length() + WORKLOAD_1.length()));
		
		BenchFlowTest benchFlowTest = BenchFlowUtility.loadBenchFlowTestFromString(result.getBody());
		assertTrue(benchFlowTest.workload().nonEmpty());
		
		Map<String, Workload> mapWorkloads = JavaConverters.mapAsJavaMap(benchFlowTest.workload());
		assertEquals(1, mapWorkloads.size());
		
		assertTrue(mapWorkloads.containsKey(SUT_NAME + "-1.2.3"));
	}
	
	@Test
	public void testGetFulfilledDSLWithTwoWorkloadsAndStoredElements() {
			
		final String VERSIONS = "\n"+
		"    - 1.2.3\n" + 
		"    - 4.5.6";

		// Test setup: Add workload model to storage
		MemoryStorage<HttpWorkload> storage = new MemoryStorage<HttpWorkload>(HttpWorkload.class);
		Whitebox.setInternalState(controller, "storage", storage);	
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_1);
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_2);
			
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.INDIVIDUAL_REGRESSION.toString(), SUT_NAME, VERSIONS);
		ResponseEntity<String> result = controller.getFulfilledDSL(testDSL);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertTrue("DSL does not contain the workloads!", result.getBody().length() >= (testDSL.length() + WORKLOAD_1.length() + WORKLOAD_2.length()));
		
		BenchFlowTest benchFlowTest = BenchFlowUtility.loadBenchFlowTestFromString(result.getBody());
		assertTrue(benchFlowTest.workload().nonEmpty());
		
		Map<String, Workload> mapWorkloads = JavaConverters.mapAsJavaMap(benchFlowTest.workload());
		assertEquals(2, mapWorkloads.size());
		
		assertTrue(mapWorkloads.containsKey(SUT_NAME + "-1.2.3"));
		assertTrue(mapWorkloads.containsKey(SUT_NAME + "-4.5.6"));
	}
	
	@Test
	public void testGetFulfilledDSLWithTwoWorkloadsAndWithoutStoredElements() {
		
		// Test setup: Add workload model to storage
		MemoryStorage<HttpWorkload> storage = new MemoryStorage<HttpWorkload>(HttpWorkload.class);
		Whitebox.setInternalState(controller, "storage", storage);	
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_1);		
		
		PowerMockito.replace(PowerMockito.method(TransformationExecutor.class, "createBenchFlowWorkload")).with(
			new InvocationHandler() {
				
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					String id = SUT_NAME + "-4.5.6";
					HttpWorkload workload = BenchFlowUtility.loadHttpWorkloadFromString(WORKLOAD_2);
					return new HttpWorkloadVersion(workload, id);
				}
			}
		);
		
		final String VERSIONS = "\n"+
		"    - 1.2.3\n" + 
		"    - 4.5.6";
			
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.INDIVIDUAL_REGRESSION.toString(), SUT_NAME, VERSIONS);
		ResponseEntity<String> result = controller.getFulfilledDSL(testDSL);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertTrue("DSL does not contain the workloads!", result.getBody().length() >= (testDSL.length() + WORKLOAD_1.length() + WORKLOAD_2.length()));
		
		BenchFlowTest benchFlowTest = BenchFlowUtility.loadBenchFlowTestFromString(result.getBody());
		assertTrue(benchFlowTest.workload().nonEmpty());
		
		Map<String, Workload> mapWorkloads = JavaConverters.mapAsJavaMap(benchFlowTest.workload());
		assertEquals(2, mapWorkloads.size());
		
		assertTrue(mapWorkloads.containsKey(SUT_NAME + "-1.2.3"));
		assertTrue(mapWorkloads.containsKey(SUT_NAME + "-4.5.6"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFulfilledDSLWithMissingWorkload() {
		
		// Test setup: Add workload model to storage
		MemoryStorage<HttpWorkload> storage = new MemoryStorage<HttpWorkload>(HttpWorkload.class);
		Whitebox.setInternalState(controller, "storage", storage);	
		controller.uploadWorkloadDSL(SUT_NAME, WORKLOAD_1);
		
		PowerMockito.stub(PowerMockito.method(TransformationExecutor.class, "createBenchFlowWorkload")).toReturn(null);
		
		final String VERSIONS = "\n"+
		"    - 1.2.3\n" + 
		"    - 4.5.6";
			
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.INDIVIDUAL_REGRESSION.toString(), SUT_NAME, VERSIONS);
		controller.getFulfilledDSL(testDSL);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFulfilledDSLWithGoalTestAndTooManyVersions() {
		
		final String VERSIONS = "\n"+
		"    - 1.2.3\n" + 
		"    - 4.5.6";
			
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.LOAD.toString(), SUT_NAME, VERSIONS);
		controller.getFulfilledDSL(testDSL);
	}
	
	
	@Test
	public void testGetFulfilledDSLWithGoalIntersectionRegression() {
		
		final BenchFlowTestHelper testHelper = new BenchFlowTestHelper();
		
		PowerMockito.replace(PowerMockito.method(TransformationExecutor.class, "getContinuITyModel")).with(
			new InvocationHandler() {
				
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					ApplicationAnnotation annotationModel = testHelper.getIdpaModelFromFile(ApplicationAnnotation.class, BenchFlowTestHelper.TEST_IDPA_ANNOTATION_FILE_1);
					if(args[0].equals("application-test") && args[1].equals("1.2.3")) {
						BehaviorModel behavior = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
						return new ContinuITyModel(behavior, testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1), annotationModel);
					} else if(args[0].equals("application-test") && args[1].equals("1.2.4")) {						
						return new ContinuITyModel(null, testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_2), annotationModel);
					}
					return null;
				}
			}
		);
		
		final String VERSIONS = "\n"+
		"    - 1.2.3\n" + 
		"    - 1.2.4";
			
		// Gets the DSL and tests it
		String testDSL = String.format(DSL, GoalType.INTERSECTION_REGRESSION.toString(), "application-test", VERSIONS);	
		ResponseEntity<String> result = controller.getFulfilledDSL(testDSL);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());

		BenchFlowTest benchFlowTest = BenchFlowUtility.loadBenchFlowTestFromString(result.getBody());	
		assertNotNull(benchFlowTest);
		Map<String, Workload> workloads = JavaConverters.mapAsJavaMap(benchFlowTest.workload());
		assertEquals(1, workloads.size());
		assertTrue(workloads.containsKey("intersection-workload"));
		assertTrue(workloads.get("intersection-workload") instanceof HttpWorkload);
		
		HttpWorkload httpWorkload = (HttpWorkload) workloads.get("intersection-workload");
		
		Map<String, HttpWorkloadItem> workloadItems = JavaConverters.mapAsJavaMap(httpWorkload.workloads());
		assertEquals(3, workloadItems.size());
		
		assertTrue(workloadItems.containsKey("behavior_model0"));
		assertTrue(workloadItems.containsKey("behavior_model1"));
		assertTrue(workloadItems.containsKey("behavior_model2"));
		
		assertEquals(0.48, workloadItems.get("behavior_model0").popularity().get().underlying(), 0.00000001);
		assertEquals(0.27, workloadItems.get("behavior_model1").popularity().get().underlying(), 0.00000001);
		assertEquals(0.25, workloadItems.get("behavior_model2").popularity().get().underlying(), 0.00000001);
		
		for(HttpWorkloadItem item : workloadItems.values()) {
			assertEquals(DriverType.HTTP, item.driverType());
			assertFalse(item.dataSources().isDefined());
			
			List<Operation> operations = new ArrayList<>(JavaConverters.asJavaCollection(item.operations().get()));
			assertEquals(3, operations.size());
			assertEquals("INITIAL_STATE", operations.get(0).id());
			assertEquals("buyUsingGET", operations.get(1).id());
			assertEquals("shopUsingGET", operations.get(2).id());
		}
	}
}
